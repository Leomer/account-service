package com.bank.account.service.impl;

import com.bank.account.client.CreditClient;
import com.bank.account.client.CustomerClient;
import com.bank.account.dto.AccountDto;
import com.bank.account.dto.CustomerResponse;
import com.bank.account.event.AccountEventPublisher;
import com.bank.account.exception.BusinessException;
import com.bank.account.exception.NotFoundException;
import com.bank.account.factory.AccountFactory;
import com.bank.account.model.Account;
import com.bank.account.model.AccountType;
import com.bank.account.repository.AccountRepository;
import com.bank.account.strategy.AccountStrategyRegistry;
import com.bank.account.strategy.CheckingStrategy;
import com.bank.account.strategy.FixedTermStrategy;
import com.bank.account.strategy.SavingsStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.UpdateDefinition;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Additional unit tests for {@link AccountServiceImpl}: queries, update, delete, balance. */
@ExtendWith(MockitoExtension.class)
class AccountServiceImplCrudTest {

    @Mock
    private AccountRepository repository;

    @Mock
    private CustomerClient customerClient;

    @Mock
    private CreditClient creditClient;

    @Mock
    private ReactiveMongoTemplate mongoTemplate;

    @Mock
    private AccountEventPublisher eventPublisher;

    private AccountServiceImpl service;

    @BeforeEach
    void setUp() {
        AccountStrategyRegistry registry = new AccountStrategyRegistry(List.of(
                new SavingsStrategy(BigDecimal.ZERO, 10),
                new CheckingStrategy(BigDecimal.ZERO, new BigDecimal("10.00")),
                new FixedTermStrategy(BigDecimal.ZERO)));
        AccountFactory factory = new AccountFactory(registry, 5, new BigDecimal("1.50"),
                new BigDecimal("500"));
        service = new AccountServiceImpl(repository, customerClient, mongoTemplate,
                creditClient, registry, factory, eventPublisher);
    }

    // ---------- helpers ----------

    private Account personalAccount() {
        return Account.builder().accountNumber("ACC1").customerType("PERSONAL")
                .accountType(AccountType.CHECKING).balance(BigDecimal.TEN)
                .holders(new ArrayList<>()).build();
    }

    private CustomerResponse customer(String type) {
        CustomerResponse c = new CustomerResponse();
        c.setType(type);
        return c;
    }

    // ---------- queries ----------

    @Test
    void findAllShouldReturnAllAccounts() {
        when(repository.findAll()).thenReturn(Flux.just(personalAccount(), personalAccount()));
        service.findAll().test().assertValueCount(2).assertComplete();
    }

    @Test
    void findByCustomerShouldReturnCustomerAccounts() {
        when(repository.findByCustomerDocument("123")).thenReturn(Flux.just(personalAccount()));
        service.findByCustomer("123").test().assertValueCount(1).assertComplete();
    }

    @Test
    void findByAccountNumberShouldReturnAccount() {
        when(repository.findByAccountNumber("ACC1")).thenReturn(Mono.just(personalAccount()));
        service.findByAccountNumber("ACC1")
                .test()
                .assertValue(a -> "ACC1".equals(a.getAccountNumber()))
                .assertComplete();
    }

    // ---------- create: extra branches ----------

    @Test
    void createCheckingShouldKeepCustomFee() {
        when(customerClient.findByDocument("123")).thenReturn(Mono.just(customer("PERSONAL")));
        when(repository.countByCustomerDocumentAndAccountType("123", AccountType.CHECKING))
                .thenReturn(Mono.just(0L));
        when(creditClient.hasOverdueDebt("123")).thenReturn(Mono.just(false));
        when(repository.save(any(Account.class))).thenAnswer(i -> Mono.just(i.getArgument(0)));

        AccountDto dto = AccountDto.builder().accountType(AccountType.CHECKING)
                .customerDocument("123").maintenanceFee(new BigDecimal("25.00")).build();

        service.create(dto)
                .test()
                .assertValue(a -> new BigDecimal("25.00").compareTo(a.getMaintenanceFee()) == 0)
                .assertComplete();
    }

    @Test
    void createFixedTermShouldFailForBusinessCustomer() {
        when(customerClient.findByDocument("123")).thenReturn(Mono.just(customer("BUSINESS")));

        AccountDto dto = AccountDto.builder().accountType(AccountType.FIXED_TERM)
                .customerDocument("123").build();

        service.create(dto).test().assertError(BusinessException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void createCheckingShouldFailWhenPersonalCustomerAlreadyHasOne() {
        when(customerClient.findByDocument("123")).thenReturn(Mono.just(customer("PERSONAL")));
        when(repository.countByCustomerDocumentAndAccountType("123", AccountType.CHECKING))
                .thenReturn(Mono.just(1L));

        AccountDto dto = AccountDto.builder().accountType(AccountType.CHECKING)
                .customerDocument("123").build();

        service.create(dto).test().assertError(BusinessException.class);
    }

    // ---------- update ----------

    @Test
    void updateShouldModifyAllowedFields() {
        when(repository.findByAccountNumber("ACC1")).thenReturn(Mono.just(personalAccount()));
        when(repository.save(any(Account.class))).thenAnswer(i -> Mono.just(i.getArgument(0)));

        AccountDto dto = AccountDto.builder()
                .accountType(AccountType.CHECKING).customerDocument("123")
                .maintenanceFee(new BigDecimal("30.00"))
                .monthlyMovementLimit(5)
                .holders(List.of("111"))
                .authorizedSigners(List.of("222"))
                .build();

        service.update("ACC1", dto)
                .test()
                .assertValue(a -> new BigDecimal("30.00").compareTo(a.getMaintenanceFee()) == 0
                        && Integer.valueOf(5).equals(a.getMonthlyMovementLimit())
                        && a.getHolders().contains("111")
                        && a.getAuthorizedSigners().contains("222"))
                .assertComplete();
    }

    @Test
    void updateShouldFailWhenBusinessAccountLosesAllHolders() {
        Account business = personalAccount();
        business.setCustomerType("BUSINESS");
        business.setHolders(List.of("111"));
        when(repository.findByAccountNumber("ACC1")).thenReturn(Mono.just(business));

        AccountDto dto = AccountDto.builder().accountType(AccountType.CHECKING)
                .customerDocument("123").holders(new ArrayList<>()).build();

        service.update("ACC1", dto).test().assertError(BusinessException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void updateShouldFailWhenAccountMissing() {
        when(repository.findByAccountNumber("X")).thenReturn(Mono.empty());

        service.update("X", AccountDto.builder().build())
                .test()
                .assertError(NotFoundException.class);
    }

    // ---------- delete ----------

    @Test
    void deleteShouldRemoveExistingAccount() {
        Account acc = personalAccount();
        when(repository.findByAccountNumber("ACC1")).thenReturn(Mono.just(acc));
        when(repository.delete(acc)).thenReturn(Mono.empty());

        service.delete("ACC1").test().assertComplete();
        verify(repository).delete(acc);
    }

    @Test
    void deleteShouldFailWhenAccountMissing() {
        when(repository.findByAccountNumber("X")).thenReturn(Mono.empty());

        service.delete("X").test().assertError(NotFoundException.class);
        verify(repository, never()).delete(any());
    }

    // ---------- balance ----------

    @Test
    void updateBalanceShouldReturnUpdatedAccountOnDeposit() {
        Account updated = personalAccount();
        updated.setBalance(new BigDecimal("60"));
        when(mongoTemplate.findAndModify(any(Query.class), any(UpdateDefinition.class),
                any(FindAndModifyOptions.class), eq(Account.class)))
                .thenReturn(Mono.just(updated));

        service.updateBalance("ACC1", new BigDecimal("50"))
                .test()
                .assertValue(a -> new BigDecimal("60").compareTo(a.getBalance()) == 0)
                .assertComplete();
    }

    @Test
    void updateBalanceShouldWorkOnWithdrawalWithEnoughFunds() {
        Account updated = personalAccount();
        updated.setBalance(new BigDecimal("5"));
        when(mongoTemplate.findAndModify(any(Query.class), any(UpdateDefinition.class),
                any(FindAndModifyOptions.class), eq(Account.class)))
                .thenReturn(Mono.just(updated));

        service.updateBalance("ACC1", new BigDecimal("-5")).test().assertComplete();
    }

    @Test
    void updateBalanceShouldFailWhenAccountDoesNotExist() {
        when(mongoTemplate.findAndModify(any(Query.class), any(UpdateDefinition.class),
                any(FindAndModifyOptions.class), eq(Account.class)))
                .thenReturn(Mono.empty());
        when(repository.findByAccountNumber("X")).thenReturn(Mono.empty());

        service.updateBalance("X", new BigDecimal("-5"))
                .test()
                .assertError(NotFoundException.class);
    }
}