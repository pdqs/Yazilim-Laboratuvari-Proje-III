package com.yazlab.kargo.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "vehicles")
public class Vehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String plaka;
    private Double capacity;


    private int currentLoad = 0;


    private Double fuelCostPerKm;
    private Double rentalCost;
    private Boolean available;


    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getPlaka() { return plaka; }
    public void setPlaka(String plaka) { this.plaka = plaka; }

    public Double getCapacity() { return capacity; }
    public void setCapacity(Double capacity) { this.capacity = capacity; }

    public Double getFuelCostPerKm() { return fuelCostPerKm; }
    public void setFuelCostPerKm(Double fuelCostPerKm) { this.fuelCostPerKm = fuelCostPerKm; }

    public Double getRentalCost() { return rentalCost; }
    public void setRentalCost(Double rentalCost) { this.rentalCost = rentalCost; }

    public Boolean getAvailable() { return available; }
    public void setAvailable(Boolean available) { this.available = available; }


    public int getCurrentLoad() {
        return currentLoad;
    }

    public void setCurrentLoad(int currentLoad) {
        this.currentLoad = currentLoad;
    }

}