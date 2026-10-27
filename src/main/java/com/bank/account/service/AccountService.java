package com.bank.account.service;

import com.bank.account.dto.AccountDto;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;

public interface AccountService {
    Single<AccountDto> create(AccountDto dto);
    Flowable<AccountDto> findAll();
    Single<AccountDto> findByAccountNumber(String accountNumber);
    Flowable<AccountDto> findByCustomer(String customerDocument);
    Single<AccountDto> update(String accountNumber, AccountDto dto);
    Completable delete(String accountNumber);
}
