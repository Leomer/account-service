package com.bank.account.strategy;

import com.bank.account.model.AccountType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Finds the strategy for an account type. Spring injects every strategy bean. */
@Component
public class AccountStrategyRegistry {

    private final Map<AccountType, AccountTypeStrategy> strategies = new EnumMap<>(AccountType.class);

    public AccountStrategyRegistry(List<AccountTypeStrategy> all) {
        all.forEach(s -> strategies.put(s.type(), s));
    }

    /** Strategy of the given type. */
    public AccountTypeStrategy get(AccountType type) {
        return strategies.get(type);
    }
}