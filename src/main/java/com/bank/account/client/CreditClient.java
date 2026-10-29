package com.bank.account.client;

import com.bank.account.dto.CreditResponse;
import com.bank.account.dto.OverdueResponse;
import com.bank.account.exception.BusinessException;
import com.bank.account.exception.ServiceUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Calls credit-service to check the credit products of a customer. */
@Slf4j
@Component
@RequiredArgsConstructor
public class CreditClient {

    private final WebClient creditWebClient;
    private final ReactiveCircuitBreakerFactory<?, ?> circuitBreakerFactory;

    /** Lists the credit products of a customer, protected by a circuit breaker. */
    public Flux<CreditResponse> findByCustomer(String customerDocument) {
        log.debug("Calling credit-service for customer {}", customerDocument);
        Flux<CreditResponse> call = creditWebClient.get()
                .uri("/credits/customer/{doc}", customerDocument)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, r -> reactor.core.publisher.Mono.error(
                        new BusinessException("Could not read credits of " + customerDocument)))
                .bodyToFlux(CreditResponse.class);
        return circuitBreakerFactory.create("credit-service").run(call, this::fallback);
    }

    private Flux<CreditResponse> fallback(Throwable ex) {
        if (ex instanceof BusinessException) {
            return Flux.error(ex);
        }
        log.warn("credit-service fallback triggered: {}", ex.toString());
        return Flux.error(new ServiceUnavailableException(
                "credit-service is unavailable, try again later"));
    }

    /** Asks credit-service whether the customer has overdue debt. */
    public Mono<Boolean> hasOverdueDebt(String customerDocument) {
        Mono<OverdueResponse> call = creditWebClient.get()
                .uri("/credits/customer/{doc}/overdue", customerDocument)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, r -> Mono.error(
                        new BusinessException("Could not check overdue debt of " + customerDocument)))
                .bodyToMono(OverdueResponse.class);
        return circuitBreakerFactory.create("credit-service").run(call, this::overdueFallback)
                .map(OverdueResponse::isOverdue);
    }

    private Mono<OverdueResponse> overdueFallback(Throwable ex) {
        if (ex instanceof BusinessException) {
            return Mono.error(ex);
        }
        log.warn("credit-service fallback triggered: {}", ex.toString());
        return Mono.error(new ServiceUnavailableException(
                "credit-service is unavailable, try again later"));
    }
}