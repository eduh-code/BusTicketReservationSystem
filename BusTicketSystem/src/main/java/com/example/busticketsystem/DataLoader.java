package com.example.busticketsystem;

import com.example.busticketsystem.model.Bus;
import com.example.busticketsystem.model.Route;
import com.example.busticketsystem.repository.BusRepository;
import com.example.busticketsystem.repository.RouteRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataLoader {
    @Bean
    CommandLineRunner initDatabase(BusRepository busRepository, RouteRepository routeRepository) {
        return args -> {
            if (busRepository.count() == 0) {
                busRepository.save(new Bus(null, "Kilimanjaro Express", "T 123 ABC", "Dar es Salaam",
                        "Dodoma", "06:00 AM", "12:00 PM", 20000, 40));
                busRepository.save(new Bus(null, "ABC Express", "T 456 DEF", "Dar es Salaam",
                        "Dodoma", "08:30 AM", "02:30 PM", 25000, 34));
                busRepository.save(new Bus(null, "Swift Bus", "T 789 GHI", "Dar es Salaam",
                        "Dodoma", "10:00 AM", "04:00 PM", 22000, 40));
            }

            // Import route records from existing bus rows, preserving their current fare.
            for (Bus bus : busRepository.findAll()) {
                Route route = routeRepository
                        .findBySourceIgnoreCaseAndDestinationIgnoreCase(bus.getSource(), bus.getDestination())
                        .orElseGet(() -> {
                            Route newRoute = new Route();
                            newRoute.setSource(bus.getSource());
                            newRoute.setDestination(bus.getDestination());
                            newRoute.setTransportationFee(bus.getPrice());
                            return routeRepository.save(newRoute);
                        });
                if (bus.getRoute() == null || !bus.getRoute().getId().equals(route.getId())
                        || Double.compare(bus.getPrice(), route.getTransportationFee()) != 0) {
                    bus.setRoute(route);
                    busRepository.save(bus);
                }
            }
        };
    }
}
