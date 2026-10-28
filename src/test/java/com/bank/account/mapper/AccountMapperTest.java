package com.bank.account.mapper;

import com.bank.account.dto.AccountDto;
import com.bank.account.model.Account;
import com.bank.account.model.AccountType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Unit tests for {@link AccountMapper}. */
class AccountMapperTest {

    @Test
    void toEntityShouldApplyDefaultsForNullFields() {
        Account entity = AccountMapper.toEntity(new AccountDto());

        assertEquals(0, BigDecimal.ZERO.compareTo(entity.getBalance()));
        assertTrue(entity.getHolders().isEmpty());
        assertTrue(entity.getAuthorizedSigners().isEmpty());
    }

    @Test
    void toDtoShouldCopyAllFields() {
        Account entity = Account.builder().id("1").accountNumber("A1")
                .accountType(AccountType.SAVINGS).customerDocument("123")
                .customerType("PERSONAL").balance(BigDecimal.TEN)
                .maintenanceFee(BigDecimal.ZERO).monthlyMovementLimit(10)
                .holders(List.of("h")).authorizedSigners(List.of("s")).build();

        AccountDto dto = AccountMapper.toDto(entity);

        assertEquals("A1", dto.getAccountNumber());
        assertEquals(AccountType.SAVINGS, dto.getAccountType());
        assertEquals(List.of("h"), dto.getHolders());
        assertEquals(List.of("s"), dto.getAuthorizedSigners());
    }
}