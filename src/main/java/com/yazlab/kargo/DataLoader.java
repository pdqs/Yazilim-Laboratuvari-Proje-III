package com.yazlab.kargo; // Paket ismine dikkat et

import com.yazlab.kargo.entity.Vehicle;
import com.yazlab.kargo.repository.VehicleRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataLoader {

    @Bean
    public CommandLineRunner loadData(VehicleRepository vehicleRepository) {
        return args -> {
            if (vehicleRepository.count() == 0) {
                System.out.println("⚠Veritabanı boş, varsayılan araçlar yükleniyor...");

                createVehicle(vehicleRepository, "ARAÇ-1 (Hafif)", 500.0);
                createVehicle(vehicleRepository, "ARAÇ-2 (Orta)", 750.0);
                createVehicle(vehicleRepository, "ARAÇ-3 (Ağır)", 1000.0);

                System.out.println("Varsayılan araçlar başarıyla oluşturuldu!");
            }
        };
    }

    private void createVehicle(VehicleRepository repo, String plaka, double capacity) {
        Vehicle v = new Vehicle();
        v.setPlaka(plaka);
        v.setCapacity(capacity);
        v.setFuelCostPerKm(1.0);
        v.setRentalCost(0.0);
        v.setAvailable(true);
        v.setCurrentLoad(0);
        repo.save(v);
    }
}