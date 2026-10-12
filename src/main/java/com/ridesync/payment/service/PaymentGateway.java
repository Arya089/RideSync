package com.ridesync.payment.service;

import com.ridesync.common.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;
import java.util.UUID;

@Component
public class PaymentGateway {

    private final boolean mock;
    private final String keyId;
    private final RestClient client;

    public PaymentGateway(@Value("${app.payment.mock}") boolean mock,
                          @Value("${app.payment.key-id}") String keyId,
                          @Value("${app.payment.key-secret}") String keySecret) {
        this.mock = mock;
        this.keyId = keyId;
        this.client = RestClient.builder()
                .baseUrl("https://api.razorpay.com/v1")
                .defaultHeaders(h -> h.setBasicAuth(keyId, keySecret))
                .build();
    }

    public String keyId() {
        return keyId;
    }

    /** Creates a Razorpay order for the amount (in paise) and returns its id. */
    public String createOrder(long amountPaise, String receipt) {
        if (mock) {
            return "order_mock_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        }
        try {
            Map<?, ?> response = client.post()
                    .uri("/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("amount", amountPaise, "currency", "INR", "receipt", receipt))
                    .retrieve()
                    .body(Map.class);
            if (response == null || response.get("id") == null) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Payment gateway returned no order id");
            }
            return response.get("id").toString();
        } catch (RestClientException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Payment gateway error: " + e.getMessage());
        }
    }
}