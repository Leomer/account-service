package com.bank.account.strategy;

import com.bank.account.model.Account;
import com.bank.account.model.AccountType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** Savings: no fee, monthly movement limit, personal customers only. */
@Component
public class SavingsStrategy implements AccountTypeStrategy {

    private final BigDecimal minimumOpening;
    private final int movementLimit;

    public SavingsStrategy(@Value("${business.min-opening-savings}") BigDecimal minimumOpening,
                           @Value("${business.savings-movement-limit}") int movementLimit) {
        this.minimumOpening = minimumOpening;
        this.movementLimit = movementLimit;
    }

    @Override
    public AccountType type() {
        return AccountType.SAVINGS;
    }

    @Override
    public BigDecimal minimumOpeningAmount() {
        return minimumOpening;
    }

    @Override
    public boolean allowedFor(String customerType) {
        return "PERSONAL".equals(customerType);
    }

    @Override
    public boolean limitedToOnePerPersonalCustomer() {
        return true;
    }

    @Override
    public void applyDefaults(Account account) {
        account.setMaintenanceFee(BigDecimal.ZERO);
        if (account.getMonthlyMovementLimit() == null) {
            account.setMonthlyMovementLimit(movementLimit);
        }
    }
}