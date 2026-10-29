package com.example.busticketsystem.repository;

import com.example.busticketsystem.model.Bus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface BusRepository extends JpaRepository<Bus, Long> {
    // Custom search method
    List<Bus> findBySourceAndDestination(String source, String destination);

    Optional<Bus> findByBusNumber(String busNumber);
}
