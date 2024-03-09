package com.exchange.service.open_exchange_impl;

import com.exchange.dtos.ExchangeRate;
import com.exchange.repository.BulkDBUpdater;
import com.exchange.repository.CurrencyRepository;
import com.exchange.repository.entities.Currency;
import com.exchange.service.ConfigurationService;
import com.exchange.service.ICurrencyService;
import com.exchange.service.TransactionalInvoker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class OpenExchangeCurrencyService implements ICurrencyService {

    private final BulkDBUpdater bulkDBUpdater;
    private final CurrencyRepository currencyRepository;
    private final ConfigurationService configurationService;
    private final OpenExchangeExternalAPIService openExchangeExternalAPIService;
    private final TransactionalInvoker transactionalInvoker;
    final ExecutorService workersExecutor;

    private final ObjectMapper objectMapper = new ObjectMapper();
    Map<String, ExchangeRate> exchangeRates = new HashMap<>();

    private static final TypeReference<Map<String, Double>> currencyRateType = new TypeReference<>() {
    };

    @Value("${scheduler.fixedRate}")
    private Duration interval;

    public OpenExchangeCurrencyService(OpenExchangeExternalAPIService openExchangeExternalAPIService,
                                       TransactionalInvoker transactionalInvoker,
                                       ConfigurationService configurationService,
                                       CurrencyRepository currencyRepository,
                                       BulkDBUpdater bulkDBUpdater) {
        this.openExchangeExternalAPIService = openExchangeExternalAPIService;
        this.transactionalInvoker = transactionalInvoker;
        this.configurationService = configurationService;
        this.currencyRepository = currencyRepository;
        this.bulkDBUpdater = bulkDBUpdater;
        workersExecutor = Executors.newFixedThreadPool(4);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        //if - failed on the last iteration || last update was too long ago
        if (configurationService.getOpenExchangeFailOnLastIteration() ||
                System.currentTimeMillis() - configurationService.getFetchLastTimestamp() > interval.toMillis()) {
            //case yes - fetch data from the third party
            updateExchangeRates();
        } else {
            //case no - fetch cached data from db
            initCachedCurrenciesData();
        }
    }

    @Override
    public Set<String> getCurrencies() {
        return exchangeRates.keySet();
    }

    @Override
    public ExchangeRate getExchangeRates(String currency) {
        if (exchangeRates.containsKey(currency)) {
            return exchangeRates.get(currency);
        }

        ExchangeRate rate = openExchangeExternalAPIService.fetchExchangeRate(currency);

        if (rate == null || rate.getBase() == null || rate.getRates().isEmpty()) {
            log.warn("Currency '{}' not found", currency);
            throw new RuntimeException("Currency not found");
        }

        //crutch, let's imagine that this IF doesn't exist
        if (!currency.equals(rate.getBase())) {
            rate.setBase(currency.toUpperCase());
        }
        prepareAndSaveNewCurrency(currency, rate);
        return exchangeRates.get(currency.toUpperCase(Locale.ROOT));
    }

    @Override
    public void addCurrency(String currency) {
        if (exchangeRates.containsKey(currency)) {
            log.warn("Currency '{}' already exists", currency);
            return;
        }
        ExchangeRate rate = openExchangeExternalAPIService.fetchExchangeRate(currency);

        if (rate == null || rate.getBase() == null || rate.getRates().isEmpty()) {
            log.warn("Currency '{}' not found and won't be added", currency);
            throw new RuntimeException("Currency not found");
        }

        //crutch, let's imagine that this IF doesn't exist
        if (!currency.equals(rate.getBase())) {
            rate.setBase(currency.toUpperCase());
        }
        prepareAndSaveNewCurrency(currency, rate);
    }

    @Scheduled(fixedRateString = "${scheduler.fixedRate}")
    public void updateExchangeRates() {
        log.debug("Update exchange rates task is started");
        Thread.currentThread().setName("Scheduler-Currency");

        long taskTimestamp = System.currentTimeMillis();
        MutableBoolean failOnIteration = new MutableBoolean(false);
        try {
            log.debug("Update exchange rates step 1");
            Map<String, String> currentCurrencies = getCurrenciesFromOpenExchange(failOnIteration);

            log.debug("Update exchange rates step 2");
            List<Currency> entitiesList = prepareEntitiesUsingThirdParty(taskTimestamp, currentCurrencies, failOnIteration);

            log.debug("Update exchange rates step 3");
            updateDataInBulk(failOnIteration, entitiesList);

            log.debug("Update exchange rates step 4");
            cleanNotRelevantData(taskTimestamp, failOnIteration);

        } finally {
            configurationService.setOpenExchangeFailOnLastIteration(failOnIteration.getValue());
            configurationService.setFetchLastTimestamp(taskTimestamp);
            initCachedCurrenciesData();

            log.debug("Update exchange rates task is finished");
        }
    }

    List<Currency> prepareEntitiesUsingThirdParty(long taskTimestamp, Map<String, String> currentCurrencies, MutableBoolean failOnIteration) {
        List<Currency> entitiesList = new CopyOnWriteArrayList<>();

        CountDownLatch countDownLatch = new CountDownLatch(currentCurrencies.size());

        for (Map.Entry<String, String> entry : currentCurrencies.entrySet()) {
            Runnable task = prepareCurrencyDataFetchTask(taskTimestamp, failOnIteration, entitiesList, countDownLatch, entry);
            workersExecutor.submit(task);
        }

        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            log.error("Interrupted!", e);
            failOnIteration.setTrue();
        }
        return entitiesList;
    }

    void cleanNotRelevantData(long taskTimestamp, MutableBoolean failOnIteration) {
        try {
            transactionalInvoker.invokeTransactional(() -> {
                int removed = currencyRepository.deleteByUpdatedAtBefore(taskTimestamp);
                log.info("Removed {} old values", removed);
            });
        } catch (Exception ex) {
            log.error("Exception on removing old data. Exception message '{}'", ex.getMessage(), ex);
            failOnIteration.setTrue();
        }
    }

    Runnable prepareCurrencyDataFetchTask(long taskTimestamp, MutableBoolean failOnIteration, List<Currency> entitiesList, CountDownLatch countDownLatch, Map.Entry<String, String> entry) {
        return () -> {
            log.debug("Currency data fetch task is started for value '{}'", entry.getKey());
            ExchangeRate rate;
            try {
                //fetching data per currency
                rate = openExchangeExternalAPIService.fetchExchangeRate(entry.getKey());
                entitiesList.add(prepareCurrencyEntity(entry.getKey(), rate, entry.getValue(), taskTimestamp));
            } catch (Exception ex) {
                log.error("Currency '{}' not be able to update. " +
                                "The currency will be skipped and updated on the next iteration. Exception message is '{}'",
                        entry.getKey(), ex.getMessage());
                failOnIteration.setTrue();
            } finally {
                countDownLatch.countDown();
                log.debug("Currency data fetch task is finished for value '{}'", entry.getKey());
            }
        };
    }

    Map<String, String> getCurrenciesFromOpenExchange(MutableBoolean failOnIteration) {
        Map<String, String> currentCurrencies;
        try {
            currentCurrencies = openExchangeExternalAPIService.getCurrencies();
        } catch (Exception ex) {
            failOnIteration.setTrue();
            log.error("Exception on getting currencies from third-party. Currencies not be able to update", ex);
            throw new RuntimeException(ex);
        }
        return currentCurrencies;
    }

    void prepareAndSaveNewCurrency(String currency, ExchangeRate rate) {
        String currencyFullName = openExchangeExternalAPIService.getCurrencies().get(currency);

        long currTime = System.currentTimeMillis();
        Currency currencyEntity = prepareCurrencyEntity(currency, rate, currencyFullName, currTime);
        currencyRepository.save(currencyEntity);
        exchangeRates.put(currency, rate);
    }

    Currency prepareCurrencyEntity(String currency, ExchangeRate rate, String currencyFullName, long currTime) {
        Currency currencyEntity = new Currency();
        currencyEntity.setCurrencyName(currency);
        currencyEntity.setCurrencyFullName(currencyFullName);
        currencyEntity.setCreatedAt(currTime);
        currencyEntity.setUpdatedAt(currTime);
        try {
            currencyEntity.setRates(objectMapper.writeValueAsString(rate.getRates()));
        } catch (Exception ex) {
            log.error("Jackson parsing rates exception for currency '{}'", rate.getBase(), ex);
        }
        return currencyEntity;
    }

    void initCachedCurrenciesData() {
        this.exchangeRates = currencyRepository.findAll().stream().map(this::map).collect(Collectors.toMap(ExchangeRate::getBase, Function.identity()));
    }

    ExchangeRate map(Currency from) {
        try {
            return new ExchangeRate(
                    from.getUpdatedAt(),
                    from.getCurrencyName(),
                    objectMapper.readValue(from.getRates(), currencyRateType));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private void updateDataInBulk(MutableBoolean failOnIteration, List<Currency> entitiesList) {
        try {
            transactionalInvoker.invokeTransactional(() -> bulkDBUpdater.insertOrUpdateBulkData(entitiesList));
            log.info("Updated {} currencies", entitiesList.size());
        } catch (Exception ex) {
            log.error("Exception on currencies bulk update. Data won't be updated. Exception message '{}'", ex.getMessage(), ex);
            failOnIteration.setTrue();
            throw new RuntimeException(ex);
        }
    }
}