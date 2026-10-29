package com.example.busticketsystem.model;

import jakarta.persistence.*;

@Entity
@Table(name = "buses")
public class Bus {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String busName;
    @Column(nullable = false)
    private String busNumber;
    @Column(nullable = false)
    private String source;
    @Column(nullable = false)
    private String destination;
    private String departureTime;
    private String arrivalTime;
    @Column(nullable = false)
    private double price;
    @Column(nullable = false)
    private int totalSeats;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "route_id")
    private Route route;

    public Bus() {
    }

    public Bus(Long id, String busName, String busNumber, String source, String destination,
               String departureTime, String arrivalTime, double price, int totalSeats) {
        this.id = id;
        this.busName = busName;
        this.busNumber = busNumber;
        this.source = source;
        this.destination = destination;
        this.departureTime = departureTime;
        this.arrivalTime = arrivalTime;
        this.price = price;
        this.totalSeats = totalSeats;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getBusName() { return busName; }
    public void setBusName(String busName) { this.busName = busName; }
    public String getBusNumber() { return busNumber; }
    public void setBusNumber(String busNumber) { this.busNumber = busNumber; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }
    public String getDepartureTime() { return departureTime; }
    public void setDepartureTime(String departureTime) { this.departureTime = departureTime; }
    public String getArrivalTime() { return arrivalTime; }
    public void setArrivalTime(String arrivalTime) { this.arrivalTime = arrivalTime; }
    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }
    public int getTotalSeats() { return totalSeats; }
    public void setTotalSeats(int totalSeats) { this.totalSeats = totalSeats; }
    public Route getRoute() { return route; }
    public void setRoute(Route route) {
        this.route = route;
        if (route != null) {
            this.source = route.getSource();
            this.destination = route.getDestination();
            this.price = route.getTransportationFee();
        }
    }
}
