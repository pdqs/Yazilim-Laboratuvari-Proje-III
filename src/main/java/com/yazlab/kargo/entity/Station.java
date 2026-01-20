package com.yazlab.kargo.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "stations")
public class Station {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;


    private double latitude;
    private double longitude;


}