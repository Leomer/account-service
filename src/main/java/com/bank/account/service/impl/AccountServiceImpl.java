package com.bank.account.service.impl;

import com.bank.account.client.CreditClient;
import com.bank.account.client.CustomerClient;
import com.bank.account.dto.AccountDto;
import com.bank.account.exception.BusinessException;
import com.bank.account.exception.NotFoundException;
import com.bank.account.mapper.AccountMapper;
import com.bank.account.model.Account;
import com.bank.account.model.AccountType;
import com.bank.account.repository.AccountRepository;
import com.bank.account.service.AccountService;
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

    @Value("${business.savings-movement-limit}")
    private int savingsMovementLimit;

    @Value("${business.checking-maintenance-fee}")
    private BigDecimal checkingFee;

    @Value("${business.min-opening-savings}")
    private BigDecimal minOpeningSavings;

    @Value("${business.min-opening-checking}")
    private BigDecimal minOpeningChecking;

    @Value("${business.min-opening-fixed-term}")
    private BigDecimal minOpeningFixedTerm;

    @Value("${business.free-transactions}")
    private int freeTransactions;

    @Value("${business.transaction-fee}")
    private BigDecimal transactionFee;

    @Value("${business.vip-minimum-daily-average}")
    private BigDecimal vipMinimumDailyAverage;

    @Override
    public Single<AccountDto> create(AccountDto dto) {
        log.info("Creating {} account for customer {}", dto.getAccountType(), dto.getCustomerDocument());
        return Single.fromPublisher(customerClient.findByDocument(dto.getCustomerDocument()))
                .flatMap(customer -> {
                    dto.setCustomerType(customer.getType());
                    dto.setProfile(customer.getProfile() == null ? "STANDARD" : customer.getProfile());
                    return validate(dto);
                })
                .flatMap(this::validateProfile)
                .flatMap(this::validateOpeningAmount)
                .map(valid -> {
                    Account entity = AccountMapper.toEntity(valid);
                    entity.setId(null);
                    entity.setAccountNumber(generateAccountNumber());
                    entity.setProfile(valid.getProfile());
                    applyDefaults(entity);
                    return entity;
                })
                .flatMap(entity -> Single.fromPublisher(repository.save(entity)))
                .map(AccountMapper::toDto);
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
                .flatMapCompletable(existing -> Completable.fromPublisher(repository.delete(existing)));
    }

    /** Validates business rules by customer type and account type. */
    private Single<AccountDto> validate(AccountDto dto) {
        String customerType = dto.getCustomerType();
        AccountType type = dto.getAccountType();

        if ("BUSINESS".equals(customerType)) {
            if (type == AccountType.SAVINGS || type == AccountType.FIXED_TERM) {
                return Single.error(new BusinessException(
                        "Business customers cannot have savings or fixed-term accounts"));
            }
            if (dto.getHolders() == null || dto.getHolders().isEmpty()) {
                return Single.error(new BusinessException(
                        "Business accounts require at least one holder"));
            }
            return Single.just(dto);
        }

        if (type == AccountType.FIXED_TERM) {
            return Single.just(dto);
        }
        return Single.fromPublisher(
                        repository.countByCustomerDocumentAndAccountType(dto.getCustomerDocument(), type))
                .flatMap(count -> count > 0
                        ? Single.<AccountDto>error(new BusinessException(
                        "Personal customers can have only one " + type + " account"))
                        : Single.just(dto));
    }

    /** Sets default fee and movement limit by account type. */
    private void applyDefaults(Account entity) {
        switch (entity.getAccountType()) {
            case SAVINGS -> {
                entity.setMaintenanceFee(BigDecimal.ZERO);
                if (entity.getMonthlyMovementLimit() == null) {
                    entity.setMonthlyMovementLimit(savingsMovementLimit);
                }
            }
            case CHECKING -> {
                if (entity.getMaintenanceFee() == null) {
                    entity.setMaintenanceFee(checkingFee);
                }
                entity.setMonthlyMovementLimit(null); // unlimited
            }
            case FIXED_TERM -> {
                entity.setMaintenanceFee(BigDecimal.ZERO);
                entity.setMonthlyMovementLimit(1);
            }
        }
        entity.setMinimumOpeningAmount(minimumOpening(entity.getAccountType()));
        entity.setFreeTransactionLimit(freeTransactions);
        entity.setTransactionFee(transactionFee);
        if ("VIP".equals(entity.getProfile()) && entity.getAccountType() == AccountType.SAVINGS) {
            entity.setMinimumDailyAverage(vipMinimumDailyAverage);
        }
        if ("PYME".equals(entity.getProfile()) && entity.getAccountType() == AccountType.CHECKING) {
            entity.setMaintenanceFee(BigDecimal.ZERO);
        }
    }

    private String generateAccountNumber() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 14).toUpperCase();
    }

    private Single<Account> findEntity(String accountNumber) {
        return Maybe.fromPublisher(repository.findByAccountNumber(accountNumber))
                .switchIfEmpty(Single.error(
                        new NotFoundException("Account not found: " + accountNumber)));
    }

    /** Rejects the account if the opening balance is below the minimum for its type. */
    private Single<AccountDto> validateOpeningAmount(AccountDto dto) {
        BigDecimal minimum = minimumOpening(dto.getAccountType());
        BigDecimal opening = dto.getBalance() == null ? BigDecimal.ZERO : dto.getBalance();
        if (opening.compareTo(minimum) < 0) {
            log.warn("Opening amount {} is below the minimum {}", opening, minimum);
            return Single.error(new BusinessException(
                    "Minimum opening amount for " + dto.getAccountType() + " is " + minimum));
        }
        return Single.just(dto);
    }

    /** Minimum opening amount configured for each account type. */
    private BigDecimal minimumOpening(AccountType type) {
        return switch (type) {
            case SAVINGS -> minOpeningSavings;
            case CHECKING -> minOpeningChecking;
            case FIXED_TERM -> minOpeningFixedTerm;
        };
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
}
