package com.bank.account.mapper;

import com.bank.account.dto.AccountDto;
import com.bank.account.model.Account;

import java.math.BigDecimal;
import java.util.ArrayList;

public final class AccountMapper {

    private AccountMapper() {
    }

    public static AccountDto toDto(Account e) {
        return AccountDto.builder()
                .id(e.getId())
                .accountNumber(e.getAccountNumber())
                .accountType(e.getAccountType())
                .customerDocument(e.getCustomerDocument())
                .customerType(e.getCustomerType())
                .balance(e.getBalance())
                .maintenanceFee(e.getMaintenanceFee())
                .monthlyMovementLimit(e.getMonthlyMovementLimit())
                .holders(e.getHolders())
                .authorizedSigners(e.getAuthorizedSigners())
                .minimumOpeningAmount(e.getMinimumOpeningAmount())
                .freeTransactionLimit(e.getFreeTransactionLimit())
                .transactionFee(e.getTransactionFee())
                .profile(e.getProfile())
                .minimumDailyAverage(e.getMinimumDailyAverage())
                .build();
    }

    public static Account toEntity(AccountDto d) {
        return Account.builder()
                .id(d.getId())
                .accountNumber(d.getAccountNumber())
                .accountType(d.getAccountType())
                .customerDocument(d.getCustomerDocument())
                .customerType(d.getCustomerType())
                .balance(d.getBalance() != null ? d.getBalance() : BigDecimal.ZERO)
                .maintenanceFee(d.getMaintenanceFee())
                .monthlyMovementLimit(d.getMonthlyMovementLimit())
                .holders(d.getHolders() != null ? d.getHolders() : new ArrayList<>())
                .authorizedSigners(d.getAuthorizedSigners() != null ? d.getAuthorizedSigners() : new ArrayList<>())
                .build();
    }
}
