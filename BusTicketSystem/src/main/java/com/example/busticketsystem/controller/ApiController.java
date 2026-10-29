package com.example.busticketsystem.controller;

import com.example.busticketsystem.model.Booking;
import com.example.busticketsystem.model.Bus;
import com.example.busticketsystem.repository.BookingRepository;
import com.example.busticketsystem.repository.BusRepository;
import com.example.busticketsystem.repository.PaymentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ApiController {
    private final BusRepository busRepository;
    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;

    public ApiController(BusRepository busRepository, BookingRepository bookingRepository,
                         PaymentRepository paymentRepository) {
        this.busRepository = busRepository;
        this.bookingRepository = bookingRepository;
        this.paymentRepository = paymentRepository;
    }

    @GetMapping("/search")
    public List<Bus> searchBuses(@RequestParam String source, @RequestParam String destination) {
        return busRepository.findBySourceAndDestination(source.trim(), destination.trim());
    }

    @GetMapping("/buses/{busId}/booked-seats")
    public List<Integer> getBookedSeats(@PathVariable Long busId) {
        if (!busRepository.existsById(busId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Bus not found");
        }
        return bookingRepository.findByBus_Id(busId).stream()
                .flatMap(booking -> parseSeats(booking.getSelectedSeats()).stream())
                .distinct()
                .sorted()
                .toList();
    }

    @PostMapping("/book")
    public ResponseEntity<Map<String, Object>> bookSeats(@RequestBody Map<String, Object> payload) {
        if (payload == null) {
            throw new IllegalArgumentException("Booking payload is required");
        }
        String passengerName = requiredText(payload, "passengerName");
        String passengerEmail = requiredText(payload, "passengerEmail");
        List<Integer> seats = parseSeats(requiredText(payload, "seats"));
        long busId = parseLong(payload.get("busId"), "busId");

        Bus bus = busRepository.findById(busId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bus not found"));

        if (!Double.isFinite(bus.getPrice()) || bus.getPrice() < 0 || bus.getTotalSeats() < 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bus has invalid seat or price information");
        }
        if (seats.stream().anyMatch(seat -> seat < 1 || seat > bus.getTotalSeats())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "One or more seats are outside this bus's seat range");
        }

        // Recheck availability on the server; the browser's seat display can be stale.
        Set<Integer> alreadyBooked = bookingRepository.findByBus_Id(busId).stream()
                .flatMap(booking -> parseSeats(booking.getSelectedSeats()).stream())
                .collect(Collectors.toSet());
        if (seats.stream().anyMatch(alreadyBooked::contains)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "One or more selected seats are already booked");
        }

        // Calculate from the stored bus fare rather than trusting the submitted amount.
        BigDecimal calculatedPrice = BigDecimal.valueOf(bus.getPrice())
                .multiply(BigDecimal.valueOf(seats.size()))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal submittedPrice = parsePrice(payload.get("totalPrice")).setScale(2, RoundingMode.HALF_UP);
        if (calculatedPrice.compareTo(submittedPrice) != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Submitted total does not match the selected seats");
        }

        Booking booking = new Booking();
        booking.setBus(bus);
        booking.setSelectedSeats(seats.stream().map(String::valueOf).collect(Collectors.joining(", ")));
        booking.setTotalPrice(calculatedPrice.doubleValue());
        booking.setPassengerName(passengerName);
        booking.setPassengerEmail(passengerEmail);
        booking.setBookingDate(LocalDateTime.now());
        bookingRepository.save(booking);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("message", "Booking successful", "bookingId", booking.getId()));
    }

    @GetMapping("/bookings")
    public List<Booking> findBookingsByEmail(@RequestParam String email) {
        if (email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Passenger email is required");
        }
        return bookingRepository.findByPassengerEmailIgnoreCase(email.trim());
    }

    @DeleteMapping("/bookings/{id}")
    public ResponseEntity<Void> cancelBooking(@PathVariable Long id) {
        if (!bookingRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        if (paymentRepository.findByBooking_Id(id).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A paid booking cannot be cancelled here");
        }
        bookingRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleInvalidPayload(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
    }

    private static String requiredText(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException(key + " is required");
        }
        return value.toString().trim();
    }

    private static long parseLong(Object value, String fieldName) {
        try {
            if (value == null) {
                throw new NumberFormatException();
            }
            return Long.parseLong(value.toString());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(fieldName + " must be a valid integer");
        }
    }

    private static BigDecimal parsePrice(Object value) {
        try {
            if (value == null) {
                throw new NumberFormatException();
            }
            return new BigDecimal(value.toString());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("totalPrice must be a valid number");
        }
    }

    private static List<Integer> parseSeats(String value) {
        try {
            List<Integer> seats = Arrays.stream(value.split(","))
                    .map(String::trim)
                    .map(Integer::parseInt)
                    .toList();
            if (seats.isEmpty() || seats.stream().anyMatch(seat -> seat < 1)
                    || seats.stream().distinct().count() != seats.size()) {
                throw new IllegalArgumentException("Seats must be unique positive numbers");
            }
            return seats;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("seats must be comma-separated seat numbers");
        }
    }
}
