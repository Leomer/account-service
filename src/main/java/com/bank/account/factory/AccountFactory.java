package com.bank.account.factory;

import com.bank.account.dto.AccountDto;
import com.bank.account.mapper.AccountMapper;
import com.bank.account.model.Account;
import com.bank.account.model.AccountType;
import com.bank.account.strategy.AccountStrategyRegistry;
import com.bank.account.strategy.AccountTypeStrategy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/** Builds new accounts with their number and the defaults of their type and profile. */
@Component
public class AccountFactory {

    private final AccountStrategyRegistry strategies;
    private final int freeTransactions;
    private final BigDecimal transactionFee;
    private final BigDecimal vipMinimumDailyAverage;

    public AccountFactory(AccountStrategyRegistry strategies,
                          @Value("${business.free-transactions}") int freeTransactions,
                          @Value("${business.transaction-fee}") BigDecimal transactionFee,
                          @Value("${business.vip-minimum-daily-average}") BigDecimal vipMinimumDailyAverage) {
        this.strategies = strategies;
        this.freeTransactions = freeTransactions;
        this.transactionFee = transactionFee;
        this.vipMinimumDailyAverage = vipMinimumDailyAverage;
    }

    /** Creates a new account from an already validated request. */
    public Account create(AccountDto dto) {
        Account account = AccountMapper.toEntity(dto);
        account.setId(null);
        account.setAccountNumber(newAccountNumber());
        account.setProfile(dto.getProfile());

        AccountTypeStrategy strategy = strategies.get(account.getAccountType());
        strategy.applyDefaults(account);
        account.setMinimumOpeningAmount(strategy.minimumOpeningAmount());
        account.setFreeTransactionLimit(freeTransactions);
        account.setTransactionFee(transactionFee);
        applyProfile(account);
        return account;
    }

    /** VIP savings require a daily average; PYME checking has no maintenance fee. */
    private void applyProfile(Account account) {
        if ("VIP".equals(account.getProfile()) && account.getAccountType() == AccountType.SAVINGS) {
            account.setMinimumDailyAverage(vipMinimumDailyAverage);
        }
        if ("PYME".equals(account.getProfile()) && account.getAccountType() == AccountType.CHECKING) {
            account.setMaintenanceFee(BigDecimal.ZERO);
        }
    }

    private String newAccountNumber() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 14).toUpperCase();
    }
}