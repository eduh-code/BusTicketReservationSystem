package com.example.busticketsystem.controller;

import com.example.busticketsystem.model.Booking;
import com.example.busticketsystem.model.Payment;
import com.example.busticketsystem.repository.BookingRepository;
import com.example.busticketsystem.repository.PaymentRepository;
import com.example.busticketsystem.service.PesapalPaymentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.regex.Pattern;

@Controller
public class PaymentController {
    private static final Pattern TANZANIAN_MOBILE = Pattern.compile("^0[67]\\d{8}$");

    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final PesapalPaymentService pesapalPaymentService;

    public PaymentController(BookingRepository bookingRepository, PaymentRepository paymentRepository,
                             PesapalPaymentService pesapalPaymentService) {
        this.bookingRepository = bookingRepository;
        this.paymentRepository = paymentRepository;
        this.pesapalPaymentService = pesapalPaymentService;
    }

    @GetMapping("/payment")
    public String paymentPage(@RequestParam Long bookingId, Model model) {
        Booking booking = getBooking(bookingId);
        paymentRepository.findByBooking_Id(bookingId).ifPresent(payment -> {
            if ("COMPLETED".equals(payment.getStatus())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "This booking has already been paid");
            }
        });
        model.addAttribute("booking", booking);
        return "payment";
    }

    @PostMapping("/api/payments")
    @ResponseBody
    public ResponseEntity<Map<String, String>> startPayment(@RequestBody PaymentRequest request) {
        if (request == null || request.bookingId() == null || request.phoneNumber() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Booking and phone number are required");
        }

        String phoneNumber = request.phoneNumber().replaceAll("[\\s-]", "");
        if (!TANZANIAN_MOBILE.matcher(phoneNumber).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Enter a 10-digit Tanzanian mobile number starting with 06 or 07");
        }

        Booking booking = getBooking(request.bookingId());
        Payment existingPayment = paymentRepository.findByBooking_Id(booking.getId()).orElse(null);
        if (existingPayment != null && "COMPLETED".equals(existingPayment.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This booking has already been paid");
        }
        Payment payment = existingPayment != null && "PENDING".equals(existingPayment.getStatus())
                && existingPayment.getCheckoutUrl() != null
                ? existingPayment
                : pesapalPaymentService.createCheckout(booking, phoneNumber, existingPayment);

        return ResponseEntity.ok(Map.of(
                "status", payment.getStatus(),
                "redirectUrl", payment.getCheckoutUrl()
        ));
    }

    @GetMapping("/payment/callback")
    public String paymentCallback(
            @RequestParam(name = "OrderTrackingId") String trackingId,
            @RequestParam(name = "OrderMerchantReference") String merchantReference,
            Model model) {
        Payment payment = pesapalPaymentService.refreshPaymentStatus(trackingId, merchantReference);
        if ("COMPLETED".equals(payment.getStatus())) {
            return "redirect:/ticket/" + payment.getTicketNumber();
        }
        model.addAttribute("payment", payment);
        model.addAttribute("booking", payment.getBooking());
        return "payment-result";
    }

    @GetMapping("/api/payments/ipn")
    @ResponseBody
    public Map<String, Object> paymentNotification(
            @RequestParam(name = "OrderTrackingId") String trackingId,
            @RequestParam(name = "OrderMerchantReference") String merchantReference,
            @RequestParam(name = "OrderNotificationType", required = false, defaultValue = "IPNCHANGE")
            String notificationType) {
        pesapalPaymentService.refreshPaymentStatus(trackingId, merchantReference);
        return Map.of(
                "orderNotificationType", notificationType,
                "orderTrackingId", trackingId,
                "orderMerchantReference", merchantReference,
                "status", 200
        );
    }

    @GetMapping("/api/payments/status")
    @ResponseBody
    public Map<String, String> paymentStatus(
            @RequestParam(name = "OrderTrackingId") String trackingId,
            @RequestParam(name = "OrderMerchantReference") String merchantReference) {
        var payment = pesapalPaymentService.refreshPaymentStatus(trackingId, merchantReference);
        return Map.of(
                "status", payment.getStatus(),
                "ticketNumber", payment.getTicketNumber() == null ? "" : payment.getTicketNumber()
        );
    }

    @GetMapping("/ticket/{ticketNumber}")
    public String ticket(@PathVariable String ticketNumber, Model model) {
        Payment payment = paymentRepository.findByTicketNumber(ticketNumber)
                .filter(item -> "COMPLETED".equals(item.getStatus()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Paid ticket not found"));
        model.addAttribute("payment", payment);
        model.addAttribute("booking", payment.getBooking());
        model.addAttribute("bus", payment.getBooking().getBus());
        return "ticket";
    }

    private Booking getBooking(Long bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
    }

    public record PaymentRequest(Long bookingId, String phoneNumber) {
    }
}
