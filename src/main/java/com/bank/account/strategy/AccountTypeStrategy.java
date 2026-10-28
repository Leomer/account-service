package com.bank.account.strategy;

import com.bank.account.model.Account;
import com.bank.account.model.AccountType;

import java.math.BigDecimal;

/** Rules and defaults of one account type. */
public interface AccountTypeStrategy {

    /** Account type this strategy handles. */
    AccountType type();

    /** Minimum opening amount configured for this type. */
    BigDecimal minimumOpeningAmount();

    /** Whether a customer type (PERSONAL / BUSINESS) may hold this account. */
    boolean allowedFor(String customerType);

    /** Whether the customer can hold only one account of this type (personal customers). */
    boolean limitedToOnePerPersonalCustomer();

    /** Applies fee and movement-limit defaults to the account. */
    void applyDefaults(Account account);
}