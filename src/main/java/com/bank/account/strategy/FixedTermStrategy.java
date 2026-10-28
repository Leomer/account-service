package com.bank.account.strategy;

import com.bank.account.model.Account;
import com.bank.account.model.AccountType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** Fixed term: no fee, one movement per month, personal customers only, unlimited accounts. */
@Component
public class FixedTermStrategy implements AccountTypeStrategy {

    private final BigDecimal minimumOpening;

    public FixedTermStrategy(@Value("${business.min-opening-fixed-term}") BigDecimal minimumOpening) {
        this.minimumOpening = minimumOpening;
    }

    @Override
    public AccountType type() {
        return AccountType.FIXED_TERM;
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
        return false;
    }

    @Override
    public void applyDefaults(Account account) {
        account.setMaintenanceFee(BigDecimal.ZERO);
        account.setMonthlyMovementLimit(1);
    }
}