package com.exchange.service;

import com.exchange.dtos.ExchangeRate;

import java.util.Collection;

public interface ICurrencyService {
    Collection<String> getCurrencies();

    ExchangeRate getExchangeRates(String currency);

    void addCurrency(String currency);
}
