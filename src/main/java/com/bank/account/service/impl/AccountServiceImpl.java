package com.bank.account.service.impl;

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
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {

    private final AccountRepository repository;
    private final CustomerClient customerClient;

    @Override
    public Single<AccountDto> create(AccountDto dto) {
        log.info("Creating {} account for customer {}", dto.getAccountType(), dto.getCustomerDocument());
        return Single.fromPublisher(customerClient.findByDocument(dto.getCustomerDocument()))
                .flatMap(customer -> {
                    dto.setCustomerType(customer.getType());
                    return validate(dto);
                })
                .map(valid -> {
                    Account entity = AccountMapper.toEntity(valid);
                    entity.setId(null);
                    entity.setAccountNumber(generateAccountNumber());
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
                    entity.setMonthlyMovementLimit(10);
                }
            }
            case CHECKING -> {
                if (entity.getMaintenanceFee() == null) {
                    entity.setMaintenanceFee(new BigDecimal("10.00"));
                }
                entity.setMonthlyMovementLimit(null); // unlimited
            }
            case FIXED_TERM -> {
                entity.setMaintenanceFee(BigDecimal.ZERO);
                entity.setMonthlyMovementLimit(1);
            }
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
}
