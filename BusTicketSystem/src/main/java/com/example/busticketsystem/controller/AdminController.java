package com.example.busticketsystem.controller;

import com.example.busticketsystem.model.Bus;
import com.example.busticketsystem.model.Route;
import com.example.busticketsystem.repository.BookingRepository;
import com.example.busticketsystem.repository.BusRepository;
import com.example.busticketsystem.repository.RouteRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "*")
public class AdminController {
    private final BusRepository busRepository;
    private final BookingRepository bookingRepository;
    private final RouteRepository routeRepository;

    public AdminController(BusRepository busRepository, BookingRepository bookingRepository,
                           RouteRepository routeRepository) {
        this.busRepository = busRepository;
        this.bookingRepository = bookingRepository;
        this.routeRepository = routeRepository;
    }

    @GetMapping("/buses")
    public List<Bus> getAllBuses() {
        return busRepository.findAll();
    }

    @GetMapping("/buses/{id}")
    public Bus getBus(@PathVariable Long id) {
        return busRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bus not found"));
    }

    @PostMapping("/buses")
    public ResponseEntity<Bus> createBus(@RequestBody BusRequest request) {
        validateBus(request);
        if (busRepository.findByBusNumber(request.busNumber().trim()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A bus with that plate number already exists");
        }
        Bus bus = new Bus();
        applyBusDetails(bus, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(busRepository.save(bus));
    }

    @PutMapping("/buses/{id}")
    public Bus updateBus(@PathVariable Long id, @RequestBody BusRequest request) {
        validateBus(request);
        Bus bus = busRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bus not found"));
        busRepository.findByBusNumber(request.busNumber().trim())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "A bus with that plate number already exists");
                });
        applyBusDetails(bus, request);
        return busRepository.save(bus);
    }

    @DeleteMapping("/buses/{id}")
    public ResponseEntity<Void> deleteBus(@PathVariable Long id) {
        if (!busRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        if (bookingRepository.existsByBus_Id(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A bus with existing bookings cannot be deleted");
        }
        busRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/routes")
    public List<Route> getRoutes() {
        return routeRepository.findAll();
    }

    @PostMapping("/routes")
    public ResponseEntity<Route> createRoute(@RequestBody RouteRequest request) {
        validateRoute(request);
        String source = request.source().trim();
        String destination = request.destination().trim();
        if (routeRepository.findBySourceIgnoreCaseAndDestinationIgnoreCase(source, destination).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This route already exists");
        }
        Route route = new Route();
        route.setSource(source);
        route.setDestination(destination);
        route.setTransportationFee(request.transportationFee());
        return ResponseEntity.status(HttpStatus.CREATED).body(routeRepository.save(route));
    }

    @PutMapping("/routes/{id}/fee")
    @Transactional
    public Route updateRouteFee(@PathVariable Long id, @RequestBody FeeRequest request) {
        if (request == null || request.transportationFee() < 0
                || !Double.isFinite(request.transportationFee())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Transportation fee must be zero or more");
        }
        Route route = routeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Route not found"));
        route.setTransportationFee(request.transportationFee());
        Route savedRoute = routeRepository.save(route);

        busRepository.findAll().stream()
                .filter(bus -> bus.getRoute() != null && bus.getRoute().getId().equals(id))
                .forEach(bus -> {
                    bus.setPrice(savedRoute.getTransportationFee());
                    busRepository.save(bus);
                });
        return savedRoute;
    }

    private void applyBusDetails(Bus bus, BusRequest request) {
        Route route = routeRepository.findById(request.routeId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selected route not found"));
        bus.setBusName(request.busName().trim());
        bus.setBusNumber(request.busNumber().trim());
        bus.setTotalSeats(request.totalSeats());
        bus.setDepartureTime(request.departureTime() == null ? "" : request.departureTime().trim());
        bus.setArrivalTime(request.arrivalTime() == null ? "" : request.arrivalTime().trim());
        bus.setRoute(route);
    }

    private static void validateBus(BusRequest request) {
        if (request == null || blank(request.busName()) || blank(request.busNumber())
                || request.routeId() == null || request.totalSeats() < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Bus name, plate number, route, and positive seat count are required");
        }
    }

    private static void validateRoute(RouteRequest request) {
        if (request == null || blank(request.source()) || blank(request.destination())
                || request.source().trim().equalsIgnoreCase(request.destination().trim())
                || request.transportationFee() < 0 || !Double.isFinite(request.transportationFee())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provide a valid route and fee");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public record BusRequest(String busName, String busNumber, int totalSeats, Long routeId,
                             String departureTime, String arrivalTime) {
    }

    public record RouteRequest(String source, String destination, double transportationFee) {
    }

    public record FeeRequest(double transportationFee) {
    }
}
