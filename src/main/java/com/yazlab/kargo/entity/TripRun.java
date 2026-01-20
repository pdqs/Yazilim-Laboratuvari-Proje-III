package com.yazlab.kargo.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "trip_run")
public class TripRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Instant createdAt = Instant.now();

    @Lob
    private String paramsJson;

    @Lob
    private String routesJson;

    @Lob
    private String costsJson;

    public Long getId() { return id; }
    public Instant getCreatedAt() { return createdAt; }
    public String getParamsJson() { return paramsJson; }
    public String getRoutesJson() { return routesJson; }
    public String getCostsJson() { return costsJson; }
    public void setId(Long id) { this.id = id; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setParamsJson(String paramsJson) { this.paramsJson = paramsJson; }
    public void setRoutesJson(String routesJson) { this.routesJson = routesJson; }
    public void setCostsJson(String costsJson) { this.costsJson = costsJson; }
}