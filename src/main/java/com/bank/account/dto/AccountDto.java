package com.bank.account.dto;

import com.bank.account.model.AccountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountDto {

    private String id;

    private String accountNumber;

    @NotNull(message = "accountType is required")
    private AccountType accountType;

    @NotBlank(message = "customerDocument is required")
    private String customerDocument;

    private String customerType;

    @DecimalMin(value = "0.0", message = "balance cannot be negative")
    private BigDecimal balance;

    private BigDecimal maintenanceFee;

    private Integer monthlyMovementLimit;

    private List<String> holders;

    private List<String> authorizedSigners;

    /** Minimum amount required to open the account. */
    private BigDecimal minimumOpeningAmount;

    /** Transactions per month that are free of commission. */
    private Integer freeTransactionLimit;

    /** Commission charged per transaction beyond the free limit. */
    private BigDecimal transactionFee;

    /** Profile of the account: STANDARD, VIP or PYME. */
    private String profile;

    /** Minimum daily average balance required each month (VIP accounts). */
    private BigDecimal minimumDailyAverage;
}
