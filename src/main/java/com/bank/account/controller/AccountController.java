package com.bank.account.controller;

import com.bank.account.dto.AccountDto;
import com.bank.account.dto.BalanceUpdateRequest;
import com.bank.account.service.AccountService;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Single<AccountDto> create(@Valid @RequestBody AccountDto dto) {
        return accountService.create(dto);
    }

    @GetMapping
    public Flowable<AccountDto> findAll() {
        return accountService.findAll();
    }

    @GetMapping("/{accountNumber}")
    public Single<AccountDto> findByAccountNumber(@PathVariable String accountNumber) {
        return accountService.findByAccountNumber(accountNumber);
    }

    @GetMapping("/customer/{customerDocument}")
    public Flowable<AccountDto> findByCustomer(@PathVariable String customerDocument) {
        return accountService.findByCustomer(customerDocument);
    }

    @PutMapping("/{accountNumber}")
    public Single<AccountDto> update(@PathVariable String accountNumber,
                                     @Valid @RequestBody AccountDto dto) {
        return accountService.update(accountNumber, dto);
    }

    @PatchMapping("/{accountNumber}/balance")
    public Single<AccountDto> updateBalance(@PathVariable String accountNumber,
                                            @Valid @RequestBody BalanceUpdateRequest request) {
        return accountService.updateBalance(accountNumber, request.getAmount());
    }

    @DeleteMapping("/{accountNumber}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Completable delete(@PathVariable String accountNumber) {
        return accountService.delete(accountNumber);
    }
}
