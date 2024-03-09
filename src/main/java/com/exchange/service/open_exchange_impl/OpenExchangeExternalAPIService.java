package com.exchange.service.open_exchange_impl;

import com.exchange.dtos.ExchangeRate;
import com.exchange.service.ConfigurationService;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

@Service
public class OpenExchangeExternalAPIService {

    private static final String LATEST_ENDPOINT = "/api/latest.json";
    private static final String CURRENCIES_ENDPOINT = "/api/currencies.json";

    private final RestTemplate restTemplate;
    private final ConfigurationService configurationService;
    private final ParameterizedTypeReference<Map<String, String>> currencyMapResponseType = new ParameterizedTypeReference<>() {
    };

    public OpenExchangeExternalAPIService(RestTemplate restTemplate,
                                          ConfigurationService configurationService) {
        this.restTemplate = restTemplate;
        this.configurationService = configurationService;
    }

    public ExchangeRate fetchExchangeRate(String currency) {
        String url = configurationService.getOpenExchangeBaseUrl() + LATEST_ENDPOINT;
        UriComponentsBuilder ucb = UriComponentsBuilder.fromHttpUrl(url)
                .queryParam("app_id", configurationService.getOpenExchangeAppId())

                //We are using a free plan of openExchange api,
                // so we can't change the base param here, because we will receive a subscription error(upgrade your plan).
                //Eventually for each request we will use 'base' param with 'USD' val(that's free),
                // that means every currency will be rated as USD.

                .queryParam("base", "USD"/* !HARDCODE! Here should be the param 'currency' instead of "USD"*/);
        return restTemplate.getForObject(ucb.toUriString(), ExchangeRate.class);
    }

    public Map<String, String> getCurrencies() {
        String url = configurationService.getOpenExchangeBaseUrl() + CURRENCIES_ENDPOINT;
        UriComponentsBuilder ucb = UriComponentsBuilder.fromHttpUrl(url)
                .queryParam("app_id", configurationService.getOpenExchangeAppId());

        ResponseEntity<Map<String, String>> responseEntity = restTemplate.exchange(
                ucb.toUriString(),
                HttpMethod.GET,
                null,
                currencyMapResponseType);

        return responseEntity.getBody();
    }
}
