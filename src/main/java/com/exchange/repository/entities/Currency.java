package com.exchange.repository.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Data
@Table(name = "currencies")
public class Currency {

    @Id
    private String currencyName;
    private String currencyFullName;
    private String rates;
    private long createdAt;
    private long updatedAt;
}
