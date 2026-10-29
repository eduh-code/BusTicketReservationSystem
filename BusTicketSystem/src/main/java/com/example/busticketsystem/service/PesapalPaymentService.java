package com.example.busticketsystem.service;

import com.example.busticketsystem.model.Booking;
import com.example.busticketsystem.model.Payment;
import com.example.busticketsystem.repository.PaymentRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.nio.charset.StandardCharsets;

@Service
public class PesapalPaymentService {
    private final RestClient restClient;
    private final PaymentRepository paymentRepository;
    private final String baseUrl;
    private final String consumerKey;
    private final String consumerSecret;
    private final String ipnId;
    private final String callbackUrl;

    public PesapalPaymentService(
            RestClient.Builder restClientBuilder,
            PaymentRepository paymentRepository,
            @Value("${pesapal.base-url}") String baseUrl,
            @Value("${pesapal.consumer-key:}") String consumerKey,
            @Value("${pesapal.consumer-secret:}") String consumerSecret,
            @Value("${pesapal.ipn-id:}") String ipnId,
            @Value("${pesapal.callback-url:}") String callbackUrl) {
        this.restClient = restClientBuilder.build();
        this.paymentRepository = paymentRepository;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.consumerKey = consumerKey;
        this.consumerSecret = consumerSecret;
        this.ipnId = ipnId;
        this.callbackUrl = callbackUrl;
    }

    public Payment createCheckout(Booking booking, String phoneNumber, Payment existingPayment) {
        requireConfiguration();
        String merchantReference = "BUS-" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 16).toUpperCase();
        String token = requestToken();
        String firstName = booking.getPassengerName().trim();
        String lastName = "Passenger";
        int nameSeparator = firstName.indexOf(' ');
        if (nameSeparator > 0) {
            lastName = firstName.substring(nameSeparator + 1);
            firstName = firstName.substring(0, nameSeparator);
        }

        Map<String, Object> billingAddress = new HashMap<>();
        billingAddress.put("email_address", booking.getPassengerEmail());
        billingAddress.put("phone_number", phoneNumber);
        billingAddress.put("country_code", "TZ");
        billingAddress.put("first_name", firstName);
        billingAddress.put("last_name", lastName);

        Map<String, Object> orderRequest = new HashMap<>();
        orderRequest.put("id", merchantReference);
        orderRequest.put("currency", "TZS");
        orderRequest.put("amount", BigDecimal.valueOf(booking.getTotalPrice()));
        orderRequest.put("description", "Bus booking " + booking.getId());
        orderRequest.put("callback_url", callbackUrl);
        orderRequest.put("notification_id", ipnId);
        orderRequest.put("billing_address", billingAddress);

        JsonNode order = restClient.post()
                .uri(baseUrl + "/api/Transactions/SubmitOrderRequest")
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(orderRequest)
                .retrieve()
                .body(JsonNode.class);

        if (order == null || hasProviderError(order)) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Pesapal could not create the payment request");
        }
        String trackingId = text(order, "order_tracking_id");
        String redirectUrl = text(order, "redirect_url");
        if (trackingId.isBlank() || redirectUrl.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Pesapal returned an incomplete payment request");
        }

        if (!isPesapalCheckoutUrl(redirectUrl)) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Pesapal returned an untrusted checkout URL");
        }
        Payment payment = existingPayment == null ? new Payment() : existingPayment;
        payment.setBooking(booking);
        payment.setPaymentMethod("Mobile money via Pesapal");
        payment.setPhoneNumber(phoneNumber);
        payment.setTransactionId(null);
        payment.setTicketNumber(null);
        payment.setMerchantReference(merchantReference);
        payment.setProviderTrackingId(trackingId);
        payment.setCheckoutUrl(redirectUrl);
        payment.setStatus("PENDING");
        payment.setPaymentDate(LocalDateTime.now());
        return paymentRepository.save(payment);
    }

    public Payment refreshPaymentStatus(String trackingId, String merchantReference) {
        Payment payment = paymentRepository.findByProviderTrackingId(trackingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Payment transaction not found"));
        if (!payment.getMerchantReference().equals(merchantReference)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Payment reference does not match the transaction");
        }
        if ("COMPLETED".equals(payment.getStatus())) {
            return payment;
        }

        JsonNode statusResponse = restClient.get()
                .uri(UriComponentsBuilder.fromHttpUrl(baseUrl + "/api/Transactions/GetTransactionStatus")
                        .queryParam("orderTrackingId", trackingId)
                        .build(true)
                        .toUri())
                .headers(headers -> headers.setBearerAuth(requestToken()))
                .retrieve()
                .body(JsonNode.class);
        if (statusResponse == null || hasProviderError(statusResponse)) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Could not verify payment status with Pesapal");
        }

        String providerStatus = text(statusResponse, "status_code");
        String description = text(statusResponse, "payment_status_description");
        if ("1".equals(providerStatus) || "COMPLETED".equalsIgnoreCase(description)) {
            payment.setStatus("COMPLETED");
            payment.setTransactionId(firstNonBlank(
                    text(statusResponse, "confirmation_code"),
                    text(statusResponse, "payment_account"),
                    trackingId));
            payment.setPaymentMethod(firstNonBlank(text(statusResponse, "payment_method"), payment.getPaymentMethod()));
            payment.setTicketNumber("TZ-" + UUID.nameUUIDFromBytes(trackingId.getBytes(StandardCharsets.UTF_8))
                    .toString().substring(0, 8).toUpperCase());
            payment.setPaymentDate(LocalDateTime.now());
        } else if ("2".equals(providerStatus) || "3".equals(providerStatus)
                || "FAILED".equalsIgnoreCase(description) || "INVALID".equalsIgnoreCase(description)) {
            payment.setStatus("FAILED");
        } else {
            payment.setStatus("PENDING");
        }
        return paymentRepository.save(payment);
    }

    private String requestToken() {
        JsonNode auth = restClient.post()
                .uri(baseUrl + "/api/Auth/RequestToken")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("consumer_key", consumerKey, "consumer_secret", consumerSecret))
                .retrieve()
                .body(JsonNode.class);
        String token = auth == null ? "" : text(auth, "token");
        if (token.isBlank() || hasProviderError(auth)) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Pesapal authentication failed; check the configured merchant credentials");
        }
        return token;
    }

    private void requireConfiguration() {
        if (consumerKey.isBlank() || consumerSecret.isBlank() || ipnId.isBlank() || callbackUrl.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Live payments are not configured. Set Pesapal credentials, IPN ID, and public callback URL.");
        }
        try {
            java.net.URI callback = java.net.URI.create(callbackUrl);
            if (!"https".equalsIgnoreCase(callback.getScheme()) || callback.getHost() == null
                    || callback.getHost().equalsIgnoreCase("localhost")
                    || callback.getHost().equals("127.0.0.1")) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "PESAPAL_CALLBACK_URL must be a public HTTPS URL reachable by Pesapal");
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? "" : value.asText();
    }

    private static boolean hasProviderError(JsonNode node) {
        return node == null || (node.has("error") && !node.get("error").isNull());
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static boolean isPesapalCheckoutUrl(String value) {
        try {
            java.net.URI uri = java.net.URI.create(value);
            String host = uri.getHost();
            return "https".equalsIgnoreCase(uri.getScheme()) && host != null
                    && (host.equalsIgnoreCase("pesapal.com") || host.toLowerCase().endsWith(".pesapal.com"));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
