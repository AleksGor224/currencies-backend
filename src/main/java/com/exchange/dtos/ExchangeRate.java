package com.exchange.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ExchangeRate {
    private long timestamp;
    private String base;
    private Map<String, Double> rates;
}
