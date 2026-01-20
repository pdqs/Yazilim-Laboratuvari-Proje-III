package com.yazlab.kargo.controller;

import com.yazlab.kargo.entity.Vehicle;
import com.yazlab.kargo.repository.CargoRepository;
import com.yazlab.kargo.repository.TripRunRepository;
import com.yazlab.kargo.repository.VehicleRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/vehicles")
public class VehicleController {

    @Autowired private VehicleRepository vehicleRepository;
    @Autowired private CargoRepository cargoRepository;
    @Autowired private TripRunRepository tripRunRepository;

    @PersistenceContext private EntityManager entityManager;

    private static final double DEFAULT_FUEL_COST = 1.0;


    @GetMapping("/all")
    public List<Vehicle> getAllVehicles() {
        return vehicleRepository.findAll();
    }

    @GetMapping("/stats")
    public Map<String, Object> getFleetStats() {
        List<Vehicle> dbVehicles = vehicleRepository.findAll();

        long totalVehicles = dbVehicles.size();

        double totalCapacity = dbVehicles.stream()
                .map(Vehicle::getCapacity)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum();

        double totalRentalCost = dbVehicles.stream()
                .map(Vehicle::getRentalCost)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum();

        long companyCount = dbVehicles.stream()
                .filter(v -> (v.getRentalCost() == null || v.getRentalCost() == 0.0))
                .count();

        long rentalCount = totalVehicles - companyCount;

        List<Map<String, Object>> vehiclesOut = dbVehicles.stream()
                .sorted(Comparator.comparing(v -> v.getId() == null ? Long.MAX_VALUE : v.getId()))
                .map(this::toVehicleDto)
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("count", totalVehicles);
        response.put("capacity", totalCapacity);
        response.put("cost", totalRentalCost);

        // ekstra (zararsız): istersen UI’da kullanırsın
        response.put("companyCount", companyCount);
        response.put("rentalCount", rentalCount);

        response.put("vehicles", vehiclesOut);
        return response;
    }

    private Map<String, Object> toVehicleDto(Vehicle v) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", v.getId());

        String plaka = v.getPlaka();
        if (plaka == null || plaka.isBlank()) {
            plaka = (v.getId() != null) ? ("Arac-" + v.getId()) : "Arac";
        }
        m.put("plaka", plaka);

        Boolean available = v.getAvailable();
        m.put("available", available != null ? available : true);

        Double cap = v.getCapacity();
        m.put("capacity", cap != null ? cap : 0.0);


        Integer cl = v.getCurrentLoad();
        m.put("currentLoad", cl != null ? cl : 0);

        Double rentalCost = v.getRentalCost();
        m.put("rentalCost", rentalCost != null ? rentalCost : 0.0);

        Double fuel = v.getFuelCostPerKm();
        m.put("fuelCostPerKm", fuel != null ? fuel : DEFAULT_FUEL_COST);

        return m;
    }


    @PostMapping("/add")
    public ResponseEntity<String> addVehicle(@RequestBody Map<String, Object> data) {
        try {
            Vehicle v = new Vehicle();
            v.setPlaka((String) data.get("plaka"));

            double capacity = Double.parseDouble(String.valueOf(data.get("capacity")));
            v.setCapacity(capacity);


            double fuel = DEFAULT_FUEL_COST;
            if (data.get("fuelCostPerKm") != null) {
                fuel = Double.parseDouble(String.valueOf(data.get("fuelCostPerKm")));
            }

            v.setFuelCostPerKm(fuel);
            v.setRentalCost(0.0);
            v.setAvailable(true);
            v.setCurrentLoad(0);

            vehicleRepository.save(v);
            return ResponseEntity.ok("Eklendi.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Hata: " + e.getMessage());
        }
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<String> deleteVehicle(@PathVariable Long id) {
        if (id == null || !vehicleRepository.existsById(id)) {
            return ResponseEntity.status(404).body("Araç bulunamadı.");
        }
        vehicleRepository.deleteById(id);
        return ResponseEntity.ok("Silindi.");
    }


    @PostMapping("/rent-bulk/{count}")
    public ResponseEntity<String> rentBulk(
            @PathVariable int count,
            @RequestParam(name = "rentalCost") Double rentalCost,
            @RequestParam(name = "fuelCost") Double fuelCost,
            @RequestParam(name = "capacity") Double capacity
    ) {
        if (count <= 0) return ResponseEntity.badRequest().body("count <= 0");
        if (capacity == null || capacity <= 0) return ResponseEntity.badRequest().body("capacity <= 0");
        if (rentalCost == null) rentalCost = 0.0;
        if (fuelCost == null) fuelCost = DEFAULT_FUEL_COST;

        long currentCount = vehicleRepository.count();

        for (int i = 0; i < count; i++) {
            Vehicle v = new Vehicle();
            v.setPlaka("KİRALIK-" + (currentCount + i + 1));
            v.setCapacity(capacity);
            v.setFuelCostPerKm(fuelCost);
            v.setRentalCost(rentalCost);
            v.setAvailable(true);
            v.setCurrentLoad(0);
            vehicleRepository.save(v);
        }
        return ResponseEntity.ok("Kiralandı.");
    }


    @PostMapping("/reset")
    @Transactional
    public ResponseEntity<String> resetSystem() {
        try {
            tripRunRepository.deleteAll();
            cargoRepository.deleteAll();
            vehicleRepository.deleteAll();


            try {
                entityManager.createNativeQuery("ALTER TABLE vehicles AUTO_INCREMENT = 1").executeUpdate();
                entityManager.createNativeQuery("ALTER TABLE cargos AUTO_INCREMENT = 1").executeUpdate();
            } catch (Exception ignored) {}


            createCompanyVehicle("ARAÇ-1 (Hafif)", 500.0);
            createCompanyVehicle("ARAÇ-2 (Orta)", 750.0);
            createCompanyVehicle("ARAÇ-3 (Ağır)", 1000.0);

            return ResponseEntity.ok("Sistem sıfırlandı. 3 Şirket aracı hazır.");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Hata: " + e.getMessage());
        }
    }

    private void createCompanyVehicle(String plaka, double capacity) {
        Vehicle v = new Vehicle();
        v.setPlaka(plaka);
        v.setCapacity(capacity);
        v.setFuelCostPerKm(DEFAULT_FUEL_COST);
        v.setRentalCost(0.0);
        v.setAvailable(true);
        v.setCurrentLoad(0);
        vehicleRepository.save(v);
    }
}
