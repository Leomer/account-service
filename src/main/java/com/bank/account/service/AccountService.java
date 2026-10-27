package com.bank.account.service;

import com.bank.account.dto.AccountDto;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;

import java.math.BigDecimal;

public interface AccountService {
    Single<AccountDto> create(AccountDto dto);
    Flowable<AccountDto> findAll();
    Single<AccountDto> findByAccountNumber(String accountNumber);
    Flowable<AccountDto> findByCustomer(String customerDocument);
    Single<AccountDto> update(String accountNumber, AccountDto dto);
    Single<AccountDto> updateBalance(String accountNumber, BigDecimal amount);
    Completable delete(String accountNumber);
}
