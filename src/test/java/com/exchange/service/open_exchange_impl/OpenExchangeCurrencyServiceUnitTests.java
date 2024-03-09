package com.exchange.service.open_exchange_impl;

import com.exchange.dtos.ExchangeRate;
import com.exchange.repository.BulkDBUpdater;
import com.exchange.repository.CurrencyRepository;
import com.exchange.repository.entities.Currency;
import com.exchange.service.ConfigurationService;
import com.exchange.service.TransactionalInvoker;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;

import java.util.*;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class OpenExchangeCurrencyServiceUnitTests {

    @Mock
    private OpenExchangeExternalAPIService openExchangeExternalAPIService;

    @Mock
    private TransactionalInvoker transactionalInvoker;

    @Mock
    private ConfigurationService configurationService;

    @Mock
    private CurrencyRepository currencyRepository;

    @Mock
    private BulkDBUpdater bulkDBUpdater;

    private OpenExchangeCurrencyService service;

    @BeforeEach
    public void setUp() {
        service = new OpenExchangeCurrencyService(openExchangeExternalAPIService, transactionalInvoker, configurationService, currencyRepository, bulkDBUpdater);
    }

    @Test
    void testGetCurrencies() {
        // Arrange
        Map<String, ExchangeRate> exchangeRates = new HashMap<>();
        exchangeRates.put("USD", new ExchangeRate());
        exchangeRates.put("EUR", new ExchangeRate());
        service.exchangeRates = exchangeRates;

        // Act
        Set<String> currencies = service.getCurrencies();

        // Assert
        assertEquals(2, currencies.size());
        assertTrue(currencies.contains("USD"));
        assertTrue(currencies.contains("EUR"));
    }

    @Test
    void testGetExchangeRates_whenCurrencyExists() {
        // Arrange
        ExchangeRate exchangeRate = new ExchangeRate();
        service.exchangeRates.put("USD", exchangeRate);

        // Act
        ExchangeRate result = service.getExchangeRates("USD");

        // Assert
        assertEquals(exchangeRate, result);
    }

    @Test
    void testGetExchangeRates_whenCurrencyDoesNotExist() {
        // Arrange
        ExchangeRate exchangeRate = new ExchangeRate();
        exchangeRate.setBase("GBP");
        exchangeRate.setRates(Map.of("USD", 1.23));
        when(openExchangeExternalAPIService.fetchExchangeRate("GBP")).thenReturn(exchangeRate);

        // Act
        ExchangeRate result = service.getExchangeRates("GBP");

        // Assert
        assertNotNull(result);
        assertTrue(service.exchangeRates.containsKey("GBP"));
    }

    @Test
    void testGetExchangeRates_whenExceptionIsThrown() {
        // Arrange
        when(openExchangeExternalAPIService.fetchExchangeRate("GBP")).thenThrow(new RuntimeException());

        // Act
        assertThrows(RuntimeException.class, () -> service.getExchangeRates("GBP"));

        // Assert
        assertFalse(service.exchangeRates.containsKey("GBP"));
    }

    @Test
    void testPrepareAndSaveNewCurrency() {
        // Arrange
        ExchangeRate exchangeRate = new ExchangeRate();
        exchangeRate.setBase("GBP");
        exchangeRate.setRates(Map.of("USD", 1.23));
        when(openExchangeExternalAPIService.getCurrencies()).thenReturn(Map.of("GBP", "British Pound"));

        // Act
        service.prepareAndSaveNewCurrency("GBP", exchangeRate);

        // Assert
        verify(currencyRepository).save(any(Currency.class));
        assertTrue(service.exchangeRates.containsKey("GBP"));
    }

    @Test
    void testPrepareCurrencyEntity() {
        // Arrange
        ExchangeRate exchangeRate = new ExchangeRate();
        exchangeRate.setBase("GBP");
        exchangeRate.setRates(Map.of("USD", 1.23));

        // Act
        Currency currency = service.prepareCurrencyEntity("GBP", exchangeRate, "British Pound", System.currentTimeMillis());

        // Assert
        assertEquals("GBP", currency.getCurrencyName());
        assertEquals("British Pound", currency.getCurrencyFullName());
        assertNotNull(currency.getRates());
    }

    @Test
    void testInitCachedCurrenciesData() {
        // Arrange
        Currency currency1 = new Currency();
        currency1.setCurrencyName("USD");
        currency1.setRates("{\"USD\":1.0}");
        Currency currency2 = new Currency();
        currency2.setCurrencyName("EUR");
        currency2.setRates("{\"EUR\":1.0}");
        when(currencyRepository.findAll()).thenReturn(List.of(currency1, currency2));

        // Act
        service.initCachedCurrenciesData();

        // Assert
        assertEquals(2, service.exchangeRates.size());
        assertTrue(service.exchangeRates.containsKey("USD"));
        assertTrue(service.exchangeRates.containsKey("EUR"));
    }

    @Test
    void testMap() {
        // Arrange
        Currency currency = new Currency();
        currency.setCurrencyName("USD");
        currency.setRates("{\"USD\":1.0}");

        // Act
        ExchangeRate exchangeRate = service.map(currency);

        // Assert
        assertEquals("USD", exchangeRate.getBase());
        assertEquals(Map.of("USD", 1.0), exchangeRate.getRates());
    }

    @Test
    void testPrepareCurrencyDataFetchTask() {
        // Arrange
        MutableBoolean failOnIteration = new MutableBoolean(false);
        List<Currency> entitiesList = new ArrayList<>();
        CountDownLatch countDownLatch = new CountDownLatch(1);
        Map.Entry<String, String> entry = Map.entry("GBP", "British Pound");
        ExchangeRate exchangeRate = new ExchangeRate();
        exchangeRate.setBase("GBP");
        exchangeRate.setRates(Map.of("USD", 1.23));
        when(openExchangeExternalAPIService.fetchExchangeRate("GBP")).thenReturn(exchangeRate);

        // Act
        Runnable task = service.prepareCurrencyDataFetchTask(System.currentTimeMillis(), failOnIteration, entitiesList, countDownLatch, entry);

        // Assert
        task.run();
        verify(openExchangeExternalAPIService).fetchExchangeRate("GBP");
        assertEquals(1, entitiesList.size());
        assertEquals(0, countDownLatch.getCount());
    }

    @Test
    void testGetCurrenciesFromOpenExchange() {
        // Arrange
        when(openExchangeExternalAPIService.getCurrencies()).thenReturn(Map.of("GBP", "British Pound"));

        // Act
        Map<String, String> currencies = service.getCurrenciesFromOpenExchange(new MutableBoolean(false));

        // Assert
        assertEquals(1, currencies.size());
        assertTrue(currencies.containsKey("GBP"));
    }

    @Test
    void testGetCurrenciesFromOpenExchange_whenExceptionIsThrown() {
        // Arrange
        when(openExchangeExternalAPIService.getCurrencies()).thenThrow(new RuntimeException());

        // Act
        assertThrows(RuntimeException.class, () -> service.getCurrenciesFromOpenExchange(new MutableBoolean(false)));
    }

    @Test
    void testAddCurrency_whenCurrencyExists() {
        // Arrange
        service.exchangeRates.put("USD", new ExchangeRate());

        // Act
        service.addCurrency("USD");

        // Assert
        verify(openExchangeExternalAPIService, never()).fetchExchangeRate(any());
        verify(currencyRepository, never()).save(any());
    }

    @Test
    void testAddCurrency_whenCurrencyDoesNotExist() {
        // Arrange
        ExchangeRate exchangeRate = new ExchangeRate();
        exchangeRate.setBase("GBP");
        exchangeRate.setRates(Map.of("USD", 1.23));
        when(openExchangeExternalAPIService.fetchExchangeRate("GBP")).thenReturn(exchangeRate);

        // Act
        service.addCurrency("GBP");

        // Assert
        verify(openExchangeExternalAPIService).fetchExchangeRate("GBP");
        verify(currencyRepository).save(any(Currency.class));
        assertTrue(service.exchangeRates.containsKey("GBP"));
    }

    @Test
    void testAddCurrency_whenExceptionIsThrown() {
        // Arrange
        when(openExchangeExternalAPIService.fetchExchangeRate("GBP")).thenThrow(new RuntimeException());

        // Act
        assertThrows(RuntimeException.class, () -> service.addCurrency("GBP"));

        // Assert
        assertFalse(service.exchangeRates.containsKey("GBP"));
    }

    @Test
    void testPrepareEntitiesUsingThirdParty() {
        // Arrange
        long taskTimestamp = System.currentTimeMillis();
        MutableBoolean failOnIteration = new MutableBoolean(false);
        Map<String, String> currentCurrencies = Map.of("GBP", "British Pound");
        CountDownLatch countDownLatch = new CountDownLatch(currentCurrencies.size());
        ExchangeRate exchangeRate = new ExchangeRate();
        exchangeRate.setBase("GBP");
        exchangeRate.setRates(Map.of("USD", 1.23));
        when(openExchangeExternalAPIService.fetchExchangeRate("GBP")).thenReturn(exchangeRate);

        // Act
        List<Currency> entitiesList = service.prepareEntitiesUsingThirdParty(taskTimestamp, currentCurrencies, failOnIteration);

        // Assert
        verify(openExchangeExternalAPIService, times(currentCurrencies.size())).fetchExchangeRate(any());
        assertEquals(currentCurrencies.size(), entitiesList.size());
    }

    @Test
    void testCleanNotRelevantData() {
        // Arrange
        long taskTimestamp = System.currentTimeMillis();
        MutableBoolean failOnIteration = new MutableBoolean(false);

        // Act
        service.cleanNotRelevantData(taskTimestamp, failOnIteration);

        // Assert
        verify(transactionalInvoker).invokeTransactional(any());
    }

    @Test
    void testUpdateExchangeRates() {
        // Arrange
        ExchangeRate exchangeRate = new ExchangeRate();
        exchangeRate.setBase("GBP");
        exchangeRate.setRates(Map.of("USD", 1.23));
        when(openExchangeExternalAPIService.getCurrencies()).thenReturn(Map.of("GBP", "British Pound"));
        when(openExchangeExternalAPIService.fetchExchangeRate("GBP")).thenReturn(exchangeRate);

        // Act
        service.updateExchangeRates();

        // Assert
        verify(openExchangeExternalAPIService).getCurrencies();
        verify(configurationService).setFetchLastTimestamp(anyLong());
    }
}