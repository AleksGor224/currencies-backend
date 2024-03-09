package com.exchange.controller;

import com.exchange.dtos.ExchangeRate;
import com.exchange.service.open_exchange_impl.OpenExchangeCurrencyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CurrencyControllerTest {

    @Mock
    private OpenExchangeCurrencyService currencyService;

    private CurrencyController controller;

    @BeforeEach
    public void setUp() {
        controller = new CurrencyController(currencyService);
    }

    @Test
    void testGetCurrencies() {
        // Arrange
        Set<String> currencies = Set.of("USD", "GBP", "EUR");
        when(currencyService.getCurrencies()).thenReturn(currencies);

        // Act
        ResponseEntity<Collection<String>> response = controller.getCurrencies();

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(currencies, response.getBody());
        verify(currencyService).getCurrencies();
    }

    @Test
    void testGetExchangeRates_whenCurrencyExists() {
        // Arrange
        ExchangeRate exchangeRate = new ExchangeRate();
        exchangeRate.setBase("USD");
        exchangeRate.setRates(Map.of("GBP", 1.23));
        when(currencyService.getExchangeRates("USD")).thenReturn(exchangeRate);

        // Act
        ResponseEntity<ExchangeRate> response = controller.getExchangeRates("USD");

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(exchangeRate, response.getBody());
        verify(currencyService).getExchangeRates("USD");
    }

    @Test
    void testGetExchangeRates_whenCurrencyDoesNotExist() {
        // Arrange
        when(currencyService.getExchangeRates("USD")).thenReturn(null);

        // Act
        ResponseEntity<ExchangeRate> response = controller.getExchangeRates("USD");

        // Assert
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNull(response.getBody());
        verify(currencyService).getExchangeRates("USD");
    }

    @Test
    void testAddCurrency_whenCurrencyDoesNotExist() {
        // Arrange
        doNothing().when(currencyService).addCurrency("USD");

        // Act
        ResponseEntity<String> response = controller.addCurrency("USD");

        // Assert
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("Currency added successfully", response.getBody());
        verify(currencyService).addCurrency("USD");
    }
}