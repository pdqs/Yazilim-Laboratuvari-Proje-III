package com.yazlab.kargo.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "cargos")
public class Cargo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private int weight;

    private int quantity = 1;


    @ManyToOne
    @JoinColumn(name = "station_id")
    private Station station;

    private String status;

    private LocalDateTime requestDate;

    @PrePersist
    protected void onCreate() {
        requestDate = LocalDateTime.now();
        if (status == null) status = "BEKLIYOR";
    }
}