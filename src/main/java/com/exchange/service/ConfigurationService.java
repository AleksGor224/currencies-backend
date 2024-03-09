package com.exchange.service;

import com.exchange.repository.ConfigurationRepository;
import com.exchange.repository.entities.Configuration;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class ConfigurationService {

    private final ConfigurationRepository configurationRepository;

    public ConfigurationService(ConfigurationRepository configurationRepository) {
        this.configurationRepository = configurationRepository;
    }

    private static final String LAST_FETCH_TIMESTAMP = "fetch.lastTimestamp";
    private static final String OPEN_EXCHANGE_APP_ID = "fetch.openExchange.appId";
    private static final String OPEN_EXCHANGE_BASE_URL = "fetch.openExchange.baseUrl";
    private static final String OPEN_EXCHANGE_FAIL_ON_LAST_ITERATION = "fetch.openExchange.failOnLastIteration";

    public Long getFetchLastTimestamp() {
        return Long.parseLong(getConfigurationValue(LAST_FETCH_TIMESTAMP, "0"));
    }

    public void setFetchLastTimestamp(Long value) {
        saveConfiguration(LAST_FETCH_TIMESTAMP, Objects.toString(value, null));
    }

    public String getOpenExchangeBaseUrl() {
        return getConfigurationValue(OPEN_EXCHANGE_BASE_URL, null);
    }

    public void setOpenExchangeBaseUrl(String value) {
        saveConfiguration(OPEN_EXCHANGE_BASE_URL, Objects.toString(value, null));
    }

    public String getOpenExchangeAppId() {
        return getConfigurationValue(OPEN_EXCHANGE_APP_ID, null);
    }

    public void setOpenExchangeAppId(String value) {
        saveConfiguration(OPEN_EXCHANGE_APP_ID, Objects.toString(value, null));
    }

    public boolean getOpenExchangeFailOnLastIteration() {
        return Boolean.parseBoolean(getConfigurationValue(OPEN_EXCHANGE_FAIL_ON_LAST_ITERATION, "false"));
    }

    public void setOpenExchangeFailOnLastIteration(boolean value) {
        saveConfiguration(OPEN_EXCHANGE_FAIL_ON_LAST_ITERATION, String.valueOf(value));
    }

    private void saveConfiguration(String key, String value) {
        Configuration configuration = new Configuration();
        configuration.setKey(key);
        configuration.setValue(value);
        configurationRepository.save(configuration);
    }

    private String getConfigurationValue(String key, String defaultValue) {
        return configurationRepository.findById(key)
                .map(Configuration::getValue)
                .orElse(defaultValue);
    }
}
