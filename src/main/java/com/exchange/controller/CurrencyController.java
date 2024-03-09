package com.exchange.controller;

import com.exchange.dtos.ExchangeRate;
import com.exchange.service.open_exchange_impl.OpenExchangeCurrencyService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;

@RestController
@RequestMapping("/api/currencies")
public class CurrencyController {
    private final OpenExchangeCurrencyService currencyService;

    public CurrencyController(OpenExchangeCurrencyService currencyService) {
        this.currencyService = currencyService;
    }

    // Get currency list used in project
    @GetMapping("/list")
    public ResponseEntity<Collection<String>> getCurrencies() {
        Collection<String> currencies = currencyService.getCurrencies();
        return new ResponseEntity<>(currencies, HttpStatus.OK);
    }

    // Get exchange rate for specified currency
    @GetMapping("/exchange-rates/{currency}")
    public ResponseEntity<ExchangeRate> getExchangeRates(@PathVariable("currency") String currency) {
        ExchangeRate exchangeRate = currencyService.getExchangeRates(currency);
        if (exchangeRate != null) {
            return new ResponseEntity<>(exchangeRate, HttpStatus.OK);
        } else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    // Add a new currency
    @PostMapping("/add")
    public ResponseEntity<String> addCurrency(@RequestBody String currency) {
        try {
            currencyService.addCurrency(currency);
            return new ResponseEntity<>("Currency added successfully", HttpStatus.CREATED);
        } catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }
}
