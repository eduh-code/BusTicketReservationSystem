package com.example.busticketsystem.repository;

import com.example.busticketsystem.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByBooking_Id(Long bookingId);
    Optional<Payment> findByTicketNumber(String ticketNumber);
    Optional<Payment> findByProviderTrackingId(String providerTrackingId);
}
