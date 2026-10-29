package com.example.busticketsystem.repository;

import com.example.busticketsystem.model.Booking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    List<Booking> findByPassengerEmailIgnoreCase(String passengerEmail);

    List<Booking> findByBus_Id(Long busId);

    boolean existsByBus_Id(Long busId);
}
