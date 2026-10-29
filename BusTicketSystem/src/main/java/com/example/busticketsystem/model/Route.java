package com.example.busticketsystem.model;

import jakarta.persistence.*;

@Entity
@Table(name = "routes", uniqueConstraints =
        @UniqueConstraint(name = "uk_routes_source_destination", columnNames = {"source", "destination"}))
public class Route {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String source;
    @Column(nullable = false)
    private String destination;
    @Column(nullable = false)
    private double transportationFee;

    public Route() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }
    public double getTransportationFee() { return transportationFee; }
    public void setTransportationFee(double transportationFee) { this.transportationFee = transportationFee; }
}
