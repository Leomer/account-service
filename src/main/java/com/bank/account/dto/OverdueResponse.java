package com.bank.account.dto;

import lombok.Data;

/** Answer of credit-service to the overdue debt query. */
@Data
public class OverdueResponse {
    private boolean overdue;
}