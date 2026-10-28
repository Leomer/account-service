package com.bank.account.dto;

import lombok.Data;

/** Subset of credit data needed by this service. */
@Data
public class CreditResponse {
    private String creditNumber;
    private String creditType;
}