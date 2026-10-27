package com.bank.account.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "accounts")
public class Account {

    @Id
    private String id;

    @Indexed(unique = true)
    private String accountNumber;

    private AccountType accountType;

    /** Document number of the owner customer. */
    private String customerDocument;

    /** Customer type copied at creation (PERSONAL / BUSINESS). */
    private String customerType;

    private BigDecimal balance;

    private BigDecimal maintenanceFee;

    private Integer monthlyMovementLimit;

    /** Document numbers of holders (required for business accounts). */
    private List<String> holders;

    /** Document numbers of authorized signers. */
    private List<String> authorizedSigners;
}
