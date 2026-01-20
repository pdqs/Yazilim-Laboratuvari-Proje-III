package com.yazlab.kargo;

import com.yazlab.kargo.entity.Vehicle;
import com.yazlab.kargo.repository.CargoRepository; // <-- EKLENDİ
import com.yazlab.kargo.repository.TripRunRepository;
import com.yazlab.kargo.repository.VehicleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class SystemStartupRunner implements CommandLineRunner {

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private TripRunRepository tripRunRepository;

    @Override
    public void run(String... args) throws Exception {
        System.out.println(">>> SİSTEM BAŞLATILIYOR: Tam temizlik yapılıyor...");


        vehicleRepository.deleteAll();


        createCompanyVehicle("ARAÇ-1 (Hafif)", 500.0);
        createCompanyVehicle("ARAÇ-2 (Orta)", 750.0);
        createCompanyVehicle("ARAÇ-3 (Ağır)", 1000.0);

        System.out.println(">>> TEMİZLİK BİTTİ: Sistem 'Sıfır' durumda açıldı.");
    }

    private void createCompanyVehicle(String plaka, double capacity) {
        Vehicle v = new Vehicle();
        v.setPlaka(plaka);
        v.setCapacity(capacity);
        v.setFuelCostPerKm(1.0);
        v.setRentalCost(0.0);
        v.setAvailable(true);
        v.setCurrentLoad(0);
        vehicleRepository.save(v);
    }
}