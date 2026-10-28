package com.bank.account.strategy;

import com.bank.account.model.Account;
import com.bank.account.model.AccountType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** Checking: maintenance fee, unlimited movements, personal and business customers. */
@Component
public class CheckingStrategy implements AccountTypeStrategy {

    private final BigDecimal minimumOpening;
    private final BigDecimal maintenanceFee;

    public CheckingStrategy(@Value("${business.min-opening-checking}") BigDecimal minimumOpening,
                            @Value("${business.checking-maintenance-fee}") BigDecimal maintenanceFee) {
        this.minimumOpening = minimumOpening;
        this.maintenanceFee = maintenanceFee;
    }

    @Override
    public AccountType type() {
        return AccountType.CHECKING;
    }

    @Override
    public BigDecimal minimumOpeningAmount() {
        return minimumOpening;
    }

    @Override
    public boolean allowedFor(String customerType) {
        return true;
    }

    @Override
    public boolean limitedToOnePerPersonalCustomer() {
        return true;
    }

    @Override
    public void applyDefaults(Account account) {
        if (account.getMaintenanceFee() == null) {
            account.setMaintenanceFee(maintenanceFee);
        }
        account.setMonthlyMovementLimit(null); // unlimited
    }
}