package com.bank.account.repository;

import com.bank.account.model.Account;
import com.bank.account.model.AccountType;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface AccountRepository extends ReactiveMongoRepository<Account, String> {
    Mono<Account> findByAccountNumber(String accountNumber);
    Flux<Account> findByCustomerDocument(String customerDocument);
    Mono<Long> countByCustomerDocumentAndAccountType(String customerDocument, AccountType accountType);
}
