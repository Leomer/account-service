package com.bank.account.strategy;

import com.bank.account.model.Account;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Unit tests for the account type strategies. */
class AccountTypeStrategyTest {

    @Test
    void savingsShouldBePersonalOnlyWithNoFeeAndLimit() {
        SavingsStrategy s = new SavingsStrategy(BigDecimal.ZERO, 10);
        Account a = new Account();
        s.applyDefaults(a);

        assertTrue(s.allowedFor("PERSONAL"));
        assertFalse(s.allowedFor("BUSINESS"));
        assertEquals(0, BigDecimal.ZERO.compareTo(a.getMaintenanceFee()));
        assertEquals(10, a.getMonthlyMovementLimit());
    }

    @Test
    void checkingShouldBeUnlimitedWithFeeForAnyCustomer() {
        CheckingStrategy s = new CheckingStrategy(new BigDecimal("50"), new BigDecimal("10.00"));
        Account a = new Account();
        s.applyDefaults(a);

        assertTrue(s.allowedFor("PERSONAL"));
        assertTrue(s.allowedFor("BUSINESS"));
        assertNull(a.getMonthlyMovementLimit());
        assertEquals(0, new BigDecimal("10.00").compareTo(a.getMaintenanceFee()));
    }

    @Test
    void fixedTermShouldAllowSeveralAccountsAndOneMovement() {
        FixedTermStrategy s = new FixedTermStrategy(new BigDecimal("100"));
        Account a = new Account();
        s.applyDefaults(a);

        assertFalse(s.limitedToOnePerPersonalCustomer());
        assertEquals(1, a.getMonthlyMovementLimit());
    }
}