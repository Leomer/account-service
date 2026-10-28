package com.bank.account.service.impl;

import com.bank.account.client.CustomerClient;
import com.bank.account.dto.AccountDto;
import com.bank.account.dto.CustomerResponse;
import com.bank.account.exception.BusinessException;
import com.bank.account.exception.NotFoundException;
import com.bank.account.model.Account;
import com.bank.account.model.AccountType;
import com.bank.account.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.UpdateDefinition;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit tests for {@link AccountServiceImpl}. */
@ExtendWith(MockitoExtension.class)
class AccountServiceImplTest {

    @Mock
    private AccountRepository repository;

    @Mock
    private CustomerClient customerClient;

    @Mock
    private ReactiveMongoTemplate mongoTemplate;

    @InjectMocks
    private AccountServiceImpl service;

    @BeforeEach
    void setUp() {
        // @Value fields are not filled by Mockito, so we set them manually
        ReflectionTestUtils.setField(service, "savingsMovementLimit", 10);
        ReflectionTestUtils.setField(service, "checkingFee", new BigDecimal("10.00"));
    }

    // ---------- helpers ----------

    private CustomerResponse customer(String type) {
        CustomerResponse response = new CustomerResponse();
        response.setDocumentNumber("12345678");
        response.setType(type);
        return response;
    }

    private AccountDto request(AccountType type) {
        return AccountDto.builder()
                .accountType(type)
                .customerDocument("12345678")
                .build();
    }

    /** Makes repository.save return the same entity it receives. */
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
}