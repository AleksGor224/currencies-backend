package com.exchange.service.open_exchange_impl;

import com.exchange.ExchangeApplication;
import com.exchange.dtos.ExchangeRate;
import com.exchange.repository.CurrencyRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = ExchangeApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(locations="classpath:application-test.properties")
class OpenExchangeIntegrationTests {

    protected WebTestClient webClient;

    @Autowired
    private OpenExchangeCurrencyService currencyService;

    @Autowired
    private CurrencyRepository currencyRepository;

    @LocalServerPort
    protected int randomServerPort;

    ParameterizedTypeReference<List<String>> listStr = new ParameterizedTypeReference<>() {};

    @BeforeEach
    void cleanUp() {
        if (webClient == null) {
            webClient = WebTestClient.bindToServer().baseUrl("http://localhost:" + randomServerPort)
                    .responseTimeout(Duration.ofSeconds(180))
                    .build();
        }
        ReflectionTestUtils.setField(currencyService, "exchangeRates", new HashMap<>());
        currencyRepository.deleteAllInBatch();
    }

    @Test
    void testGetCurrencies() {
        List<String> response = webClient.get()
                .uri("/api/currencies/list")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(listStr)
                .returnResult()
                .getResponseBody();

        Assertions.assertNotNull(response);
        Assertions.assertTrue(response.isEmpty());
        Assertions.assertTrue(currencyRepository.findAll().isEmpty());

        currencyService.updateExchangeRates();

        response = webClient.get()
                .uri("/api/currencies/list")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(listStr)
                .returnResult()
                .getResponseBody();

        Assertions.assertNotNull(response);
        Assertions.assertEquals(170, response.size());

    }

    @Test
    void testGetExchangeRates() {
        ExchangeRate response = webClient.get()
                .uri("/api/currencies/exchange-rates/USD")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(ExchangeRate.class)
                .returnResult()
                .getResponseBody();

        Assertions.assertNotNull(response);
        Assertions.assertNotNull(response.getRates());
        Assertions.assertNotNull(response.getBase());
        Assertions.assertEquals("USD", response.getBase());

        response = webClient.get()
                .uri("/api/currencies/exchange-rates/GBP")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(ExchangeRate.class)
                .returnResult()
                .getResponseBody();

        Assertions.assertNotNull(response);
        Assertions.assertNotNull(response.getRates());
        Assertions.assertNotNull(response.getBase());
        Assertions.assertEquals("GBP", response.getBase());

        response = webClient.get()
                .uri("/api/currencies/exchange-rates/EUR")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(ExchangeRate.class)
                .returnResult()
                .getResponseBody();

        Assertions.assertNotNull(response);
        Assertions.assertNotNull(response.getRates());
        Assertions.assertNotNull(response.getBase());
        Assertions.assertEquals("EUR", response.getBase());

        response = webClient.get()
                .uri("/api/currencies/exchange-rates/CAD")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(ExchangeRate.class)
                .returnResult()
                .getResponseBody();

        Assertions.assertNotNull(response);
        Assertions.assertNotNull(response.getRates());
        Assertions.assertNotNull(response.getBase());
        Assertions.assertEquals("CAD", response.getBase());

        response = webClient.get()
                .uri("/api/currencies/exchange-rates/UAH")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(ExchangeRate.class)
                .returnResult()
                .getResponseBody();

        Assertions.assertNotNull(response);
        Assertions.assertNotNull(response.getRates());
        Assertions.assertNotNull(response.getBase());
        Assertions.assertEquals("UAH", response.getBase());

        response = webClient.get()
                .uri("/api/currencies/exchange-rates/uah")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(ExchangeRate.class)
                .returnResult()
                .getResponseBody();

        Assertions.assertNotNull(response);
        Assertions.assertNotNull(response.getRates());
        Assertions.assertNotNull(response.getBase());
        Assertions.assertEquals("UAH", response.getBase());
    }

    @Test
    void testAddCurrency() {
        webClient.post()
                .uri("/api/currencies/add")
                .body(BodyInserters.fromValue("GBP"))
                .exchange()
                .expectStatus()
                .isCreated();

        List<String> response = webClient.get()
                .uri("/api/currencies/list")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(listStr)
                .returnResult()
                .getResponseBody();

        Assertions.assertNotNull(response);
        Assertions.assertEquals(1, response.size());
        Assertions.assertEquals("GBP", response.get(0));
        Assertions.assertTrue(currencyRepository.findById("GBP").isPresent());

        webClient.post()
                .uri("/api/currencies/add")
                .body(BodyInserters.fromValue("XXX"))
                .exchange()
                .expectStatus()
                //It is a bag, because we can execute a request only for usd, so everytime we will receive a data for currency,
                // doesn't matter what's name of it will be provided
                .isCreated();

        //bug works fine, cudos for me
        Assertions.assertTrue(currencyRepository.findById("XXX").isPresent());
    }
}
