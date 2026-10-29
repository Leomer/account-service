package com.bank.account.service.impl;

import com.bank.account.client.CreditClient;
import com.bank.account.client.CustomerClient;
import com.bank.account.dto.AccountDto;
import com.bank.account.event.AccountEventPublisher;
import com.bank.account.exception.BusinessException;
import com.bank.account.exception.NotFoundException;
import com.bank.account.factory.AccountFactory;
import com.bank.account.mapper.AccountMapper;
import com.bank.account.model.Account;
import com.bank.account.model.AccountType;
import com.bank.account.repository.AccountRepository;
import com.bank.account.service.AccountService;
import com.bank.account.strategy.AccountStrategyRegistry;
import com.bank.account.strategy.AccountTypeStrategy;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {

    private final AccountRepository repository;
    private final CustomerClient customerClient;
    private final ReactiveMongoTemplate mongoTemplate;
    private final CreditClient creditClient;

    private final AccountStrategyRegistry strategies;
    private final AccountFactory accountFactory;
    private final AccountEventPublisher eventPublisher;

    @Override
    public Single<AccountDto> create(AccountDto dto) {
        log.info("Creating {} account for customer {}", dto.getAccountType(), dto.getCustomerDocument());
        return Single.fromPublisher(customerClient.findByDocument(dto.getCustomerDocument()))
                .flatMap(customer -> {
                    dto.setCustomerType(customer.getType());
                    dto.setProfile(customer.getProfile() == null ? "STANDARD" : customer.getProfile());
                    return validate(dto);
                })
                .flatMap(this::validateNoOverdueDebt)
                .flatMap(this::validateProfile)
                .flatMap(this::validateOpeningAmount)
                .map(accountFactory::create)
                .flatMap(entity -> Single.fromPublisher(repository.save(entity)))
                .map(AccountMapper::toDto)
                .doOnSuccess(eventPublisher::accountCreated);
    }

    @Override
    public Flowable<AccountDto> findAll() {
        return Flowable.fromPublisher(repository.findAll()).map(AccountMapper::toDto);
    }

    @Override
    public Single<AccountDto> findByAccountNumber(String accountNumber) {
        return findEntity(accountNumber).map(AccountMapper::toDto);
    }

    @Override
    public Flowable<AccountDto> findByCustomer(String customerDocument) {
        return Flowable.fromPublisher(repository.findByCustomerDocument(customerDocument))
                .map(AccountMapper::toDto);
    }

    @Override
    public Single<AccountDto> update(String accountNumber, AccountDto dto) {
        log.info("Updating account {}", accountNumber);
        return findEntity(accountNumber)
                .flatMap(existing -> {
                    // Only these fields can be modified
                    if (dto.getHolders() != null) {
                        existing.setHolders(dto.getHolders());
                    }
                    if (dto.getAuthorizedSigners() != null) {
                        existing.setAuthorizedSigners(dto.getAuthorizedSigners());
                    }
                    if (dto.getMaintenanceFee() != null) {
                        existing.setMaintenanceFee(dto.getMaintenanceFee());
                    }
                    if (dto.getMonthlyMovementLimit() != null) {
                        existing.setMonthlyMovementLimit(dto.getMonthlyMovementLimit());
                    }
                    if ("BUSINESS".equals(existing.getCustomerType())
                            && (existing.getHolders() == null || existing.getHolders().isEmpty())) {
                        return Single.<Account>error(
                                new BusinessException("Business accounts require at least one holder"));
                    }
                    return Single.fromPublisher(repository.save(existing));
                })
                .map(AccountMapper::toDto);
    }

    @Override
    public Single<AccountDto> updateBalance(String accountNumber, BigDecimal amount) {
        log.info("Updating balance of account {} by {}", accountNumber, amount);
        Query query = new Query(Criteria.where("accountNumber").is(accountNumber));
        if (amount.signum() < 0) {
            // atomic guard: only subtract if there are enough funds
            query.addCriteria(Criteria.where("balance").gte(amount.negate()));
        }
        Update update = new Update().inc("balance", amount);
        return Maybe.fromPublisher(mongoTemplate.findAndModify(
                        query, update, FindAndModifyOptions.options().returnNew(true), Account.class))
                .switchIfEmpty(Single.defer(() -> findEntity(accountNumber)
                        .flatMap(a -> Single.<Account>error(new BusinessException("Insufficient funds")))))
                .map(AccountMapper::toDto);
    }

    @Override
    public Completable delete(String accountNumber) {
        log.info("Deleting account {}", accountNumber);
        return findEntity(accountNumber)
                .flatMapCompletable(existing -> Completable.fromPublisher(repository.delete(existing))
                        .doOnComplete(() -> eventPublisher.accountDeleted(accountNumber)));
    }

    /** Validates business rules by customer type and account type. */
    private Single<AccountDto> validate(AccountDto dto) {
        AccountTypeStrategy strategy = strategies.get(dto.getAccountType());
        String customerType = dto.getCustomerType();

        if (!strategy.allowedFor(customerType)) {
            return Single.error(new BusinessException(
                    customerType + " customers cannot have " + dto.getAccountType() + " accounts"));
        }
        if ("BUSINESS".equals(customerType)) {
            return (dto.getHolders() == null || dto.getHolders().isEmpty())
                    ? Single.error(new BusinessException("Business accounts require at least one holder"))
                    : Single.just(dto);
        }
        if (!strategy.limitedToOnePerPersonalCustomer()) {
            return Single.just(dto);
        }
        return Single.fromPublisher(repository.countByCustomerDocumentAndAccountType(
                        dto.getCustomerDocument(), dto.getAccountType()))
                .flatMap(count -> count > 0
                        ? Single.<AccountDto>error(new BusinessException(
                        "Personal customers can have only one " + dto.getAccountType() + " account"))
                        : Single.just(dto));
    }

    private Single<Account> findEntity(String accountNumber) {
        return Maybe.fromPublisher(repository.findByAccountNumber(accountNumber))
                .switchIfEmpty(Single.error(
                        new NotFoundException("Account not found: " + accountNumber)));
    }

    /** Rejects the account if the opening balance is below the minimum for its type. */
    private Single<AccountDto> validateOpeningAmount(AccountDto dto) {
        BigDecimal minimum = strategies.get(dto.getAccountType()).minimumOpeningAmount();
        BigDecimal opening = dto.getBalance() == null ? BigDecimal.ZERO : dto.getBalance();
        if (opening.compareTo(minimum) < 0) {
            log.warn("Opening amount {} is below the minimum {}", opening, minimum);
            return Single.error(new BusinessException(
                    "Minimum opening amount for " + dto.getAccountType() + " is " + minimum));
        }
        return Single.just(dto);
    }

    /** Applies the requirements of the VIP and PYME profiles. */
    private Single<AccountDto> validateProfile(AccountDto dto) {
        String profile = dto.getProfile();
        AccountType type = dto.getAccountType();

        boolean vipSavings = "VIP".equals(profile) && type == AccountType.SAVINGS;
        boolean pymeChecking = "PYME".equals(profile) && type == AccountType.CHECKING;
        if (!vipSavings && !pymeChecking) {
            return Single.just(dto);   // standard rules only
        }
        return hasCreditCard(dto.getCustomerDocument())
                .flatMap(has -> has
                        ? Single.just(dto)
                        : Single.<AccountDto>error(new BusinessException(
                        profile + " accounts require a credit card with the bank")));
    }

    /** True when the customer holds at least one credit card. */
    private Single<Boolean> hasCreditCard(String customerDocument) {
        return Flowable.fromPublisher(creditClient.findByCustomer(customerDocument))
                .any(c -> "CREDIT_CARD".equals(c.getCreditType()));
    }

    /** Rejects the request when the customer has overdue debt. */
    private Single<AccountDto> validateNoOverdueDebt(AccountDto dto) {
        return Single.fromPublisher(creditClient.hasOverdueDebt(dto.getCustomerDocument()))
                .flatMap(overdue -> overdue
                        ? Single.<AccountDto>error(new BusinessException(
                        "Customer has overdue debt and cannot acquire new products"))
                        : Single.just(dto));
    }
}
