package com.bank.account.service.impl;

import com.bank.account.client.CreditClient;
import com.bank.account.client.CustomerClient;
import com.bank.account.dto.AccountDto;
import com.bank.account.dto.CreditResponse;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit tests for {@link AccountServiceImpl}: creation rules. */
@ExtendWith(MockitoExtension.class)
class AccountServiceImplTest {

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
        buildService("0");
    }

    /**
     * Builds the service with real strategies; only the checking minimum varies.
     */
    private void buildService(String checkingMinimum) {
        AccountStrategyRegistry registry = new AccountStrategyRegistry(List.of(
                new SavingsStrategy(BigDecimal.ZERO, 10),
                new CheckingStrategy(new BigDecimal(checkingMinimum), new BigDecimal("10.00")),
                new FixedTermStrategy(BigDecimal.ZERO)));
        AccountFactory factory = new AccountFactory(registry, 5, new BigDecimal("1.50"),
                new BigDecimal("500"));
        service = new AccountServiceImpl(repository, customerClient, mongoTemplate,
                creditClient, registry, factory, eventPublisher);
    }

    // ---------- helpers ----------

    private CustomerResponse customer(String type) {
        CustomerResponse response = new CustomerResponse();
        response.setDocumentNumber("12345678");
        response.setType(type);
        return response;
    }

    private CustomerResponse customerWithProfile(String type, String profile) {
        CustomerResponse c = customer(type);
        c.setProfile(profile);
        return c;
    }

    private CreditResponse card() {
        CreditResponse c = new CreditResponse();
        c.setCreditNumber("CR1");
        c.setCreditType("CREDIT_CARD");
        return c;
    }

    private AccountDto request(AccountType type) {
        return AccountDto.builder()
                .accountType(type)
                .customerDocument("12345678")
                .build();
    }

    private void noOverdueDebt() {
        when(creditClient.hasOverdueDebt("12345678")).thenReturn(Mono.just(false));
    }

    private void saveReturnsSameEntity() {
        when(repository.save(any(Account.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
    }

// ---------- personal customers ----------

    @Test
    void createSavingsShouldSucceedForPersonalCustomerWithoutAccount() {
        when(customerClient.findByDocument("12345678")).thenReturn(Mono.just(customer("PERSONAL")));
        when(repository.countByCustomerDocumentAndAccountType("12345678", AccountType.SAVINGS))
                .thenReturn(Mono.just(0L));
        noOverdueDebt();
        saveReturnsSameEntity();

        service.create(request(AccountType.SAVINGS))
                .test()
                .assertValue(result -> result.getAccountNumber() != null
                        && BigDecimal.ZERO.compareTo(result.getMaintenanceFee()) == 0
                        && Integer.valueOf(10).equals(result.getMonthlyMovementLimit()))
                .assertComplete();
    }

    @Test
    void createSavingsShouldFailWhenPersonalCustomerAlreadyHasOne() {
        when(customerClient.findByDocument("12345678")).thenReturn(Mono.just(customer("PERSONAL")));
        when(repository.countByCustomerDocumentAndAccountType("12345678", AccountType.SAVINGS))
                .thenReturn(Mono.just(1L));

        service.create(request(AccountType.SAVINGS))
                .test()
                .assertError(BusinessException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void createFixedTermShouldNotCheckExistingAccounts() {
        when(customerClient.findByDocument("12345678")).thenReturn(Mono.just(customer("PERSONAL")));
        noOverdueDebt();
        saveReturnsSameEntity();

        service.create(request(AccountType.FIXED_TERM))
                .test()
                .assertValue(result -> Integer.valueOf(1).equals(result.getMonthlyMovementLimit()))
                .assertComplete();

        verify(repository, never()).countByCustomerDocumentAndAccountType(any(), any());
    }

    // ---------- business customers ----------

    @Test
    void createSavingsShouldFailForBusinessCustomer() {
        when(customerClient.findByDocument("12345678")).thenReturn(Mono.just(customer("BUSINESS")));

        service.create(request(AccountType.SAVINGS))
                .test()
                .assertError(BusinessException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void createCheckingShouldFailForBusinessCustomerWithoutHolders() {
        when(customerClient.findByDocument("12345678")).thenReturn(Mono.just(customer("BUSINESS")));

        service.create(request(AccountType.CHECKING))
                .test()
                .assertError(BusinessException.class);
    }

    @Test
    void createCheckingShouldSucceedForBusinessCustomerWithHolder() {
        when(customerClient.findByDocument("12345678")).thenReturn(Mono.just(customer("BUSINESS")));
        noOverdueDebt();
        saveReturnsSameEntity();

        AccountDto dto = request(AccountType.CHECKING);
        dto.setHolders(List.of("12345678"));

        service.create(dto)
                .test()
                .assertValue(result -> result.getMonthlyMovementLimit() == null
                        && new BigDecimal("10.00").compareTo(result.getMaintenanceFee()) == 0)
                .assertComplete();
    }

    // ---------- customer validation ----------

    @Test
    void createShouldFailWhenCustomerDoesNotExist() {
        when(customerClient.findByDocument("12345678"))
                .thenReturn(Mono.error(new BusinessException("Customer not found: 12345678")));

        service.create(request(AccountType.SAVINGS))
                .test()
                .assertError(BusinessException.class);

        verify(repository, never()).save(any());
    }

    // ---------- opening amount ----------

    @Test
    void createShouldFailWhenOpeningAmountIsBelowMinimum() {
        buildService("50");
        when(customerClient.findByDocument("12345678")).thenReturn(Mono.just(customer("PERSONAL")));
        when(repository.countByCustomerDocumentAndAccountType("12345678", AccountType.CHECKING))
                .thenReturn(Mono.just(0L));
        noOverdueDebt();

        AccountDto dto = request(AccountType.CHECKING);
        dto.setBalance(new BigDecimal("10"));

        service.create(dto).test().assertError(BusinessException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void createShouldSucceedWhenOpeningAmountMeetsMinimum() {
        buildService("50");
        when(customerClient.findByDocument("12345678")).thenReturn(Mono.just(customer("PERSONAL")));
        when(repository.countByCustomerDocumentAndAccountType("12345678", AccountType.CHECKING))
                .thenReturn(Mono.just(0L));
        noOverdueDebt();
        saveReturnsSameEntity();

        AccountDto dto = request(AccountType.CHECKING);
        dto.setBalance(new BigDecimal("50"));

        service.create(dto)
                .test()
                .assertValue(r -> new BigDecimal("50").compareTo(r.getMinimumOpeningAmount()) == 0
                        && Integer.valueOf(5).equals(r.getFreeTransactionLimit())
                        && new BigDecimal("1.50").compareTo(r.getTransactionFee()) == 0)
                .assertComplete();
    }

    // ---------- overdue debt ----------

    @Test
    void createShouldFailWhenCustomerHasOverdueDebt() {
        when(customerClient.findByDocument("12345678")).thenReturn(Mono.just(customer("PERSONAL")));
        when(repository.countByCustomerDocumentAndAccountType("12345678", AccountType.SAVINGS))
                .thenReturn(Mono.just(0L));
        when(creditClient.hasOverdueDebt("12345678")).thenReturn(Mono.just(true));

        service.create(request(AccountType.SAVINGS))
                .test()
                .assertError(BusinessException.class);

        verify(repository, never()).save(any());
    }

    // ---------- VIP and PYME profiles ----------

    @Test
    void createVipSavingsShouldFailWithoutCreditCard() {
        when(customerClient.findByDocument("12345678"))
                .thenReturn(Mono.just(customerWithProfile("PERSONAL", "VIP")));
        when(repository.countByCustomerDocumentAndAccountType("12345678", AccountType.SAVINGS))
                .thenReturn(Mono.just(0L));
        noOverdueDebt();
        when(creditClient.findByCustomer("12345678")).thenReturn(Flux.empty());

        service.create(request(AccountType.SAVINGS)).test().assertError(BusinessException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void createVipSavingsShouldSucceedWithCreditCard() {
        when(customerClient.findByDocument("12345678"))
                .thenReturn(Mono.just(customerWithProfile("PERSONAL", "VIP")));
        when(repository.countByCustomerDocumentAndAccountType("12345678", AccountType.SAVINGS))
                .thenReturn(Mono.just(0L));
        noOverdueDebt();
        when(creditClient.findByCustomer("12345678")).thenReturn(Flux.just(card()));
        saveReturnsSameEntity();

        service.create(request(AccountType.SAVINGS))
                .test()
                .assertValue(a -> "VIP".equals(a.getProfile())
                        && new BigDecimal("500").compareTo(a.getMinimumDailyAverage()) == 0)
                .assertComplete();
    }

    @Test
    void createPymeCheckingShouldHaveNoMaintenanceFee() {
        when(customerClient.findByDocument("12345678"))
                .thenReturn(Mono.just(customerWithProfile("BUSINESS", "PYME")));
        noOverdueDebt();
        when(creditClient.findByCustomer("12345678")).thenReturn(Flux.just(card()));
        saveReturnsSameEntity();

        AccountDto dto = request(AccountType.CHECKING);
        dto.setHolders(List.of("12345678"));

        service.create(dto)
                .test()
                .assertValue(a -> BigDecimal.ZERO.compareTo(a.getMaintenanceFee()) == 0)
                .assertComplete();
    }

    @Test
    void createPymeCheckingShouldFailWithoutCreditCard() {
        when(customerClient.findByDocument("12345678"))
                .thenReturn(Mono.just(customerWithProfile("BUSINESS", "PYME")));
        noOverdueDebt();
        when(creditClient.findByCustomer("12345678")).thenReturn(Flux.empty());

        AccountDto dto = request(AccountType.CHECKING);
        dto.setHolders(List.of("12345678"));

        service.create(dto).test().assertError(BusinessException.class);
    }

    // ---------- queries and balance ----------

    @Test
    void findByAccountNumberShouldFailWhenMissing() {
        when(repository.findByAccountNumber("X")).thenReturn(Mono.empty());

        service.findByAccountNumber("X")
                .test()
                .assertError(NotFoundException.class);
    }

    @Test
    void updateBalanceShouldFailWithInsufficientFunds() {
        // the atomic update matches nothing (guard failed), then the account is looked up
        when(mongoTemplate.findAndModify(any(Query.class), any(UpdateDefinition.class),
                any(FindAndModifyOptions.class), eq(Account.class)))
                .thenReturn(Mono.empty());
        when(repository.findByAccountNumber("ACC1"))
                .thenReturn(Mono.just(Account.builder().accountNumber("ACC1").build()));

        service.updateBalance("ACC1", new BigDecimal("-500"))
                .test()
                .assertError(BusinessException.class);
    }

    @Test
    void createShouldPublishAccountCreatedEvent() {
        when(customerClient.findByDocument("12345678")).thenReturn(Mono.just(customer("PERSONAL")));
        when(repository.countByCustomerDocumentAndAccountType("12345678", AccountType.SAVINGS))
                .thenReturn(Mono.just(0L));
        noOverdueDebt();
        saveReturnsSameEntity();

        service.create(request(AccountType.SAVINGS)).test().assertComplete();

        verify(eventPublisher).accountCreated(any(AccountDto.class));
    }
}