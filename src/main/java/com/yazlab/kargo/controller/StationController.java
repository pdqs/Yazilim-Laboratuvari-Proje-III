package com.yazlab.kargo.controller;

import com.yazlab.kargo.entity.Station;
import com.yazlab.kargo.repository.StationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stations")
public class StationController {

    @Autowired
    private StationRepository stationRepository;


    @GetMapping("/all")
    public List<Station> getAllStations() {
        return stationRepository.findAll();
    }


    @PostMapping("/add")
    public ResponseEntity<String> addStation(@RequestBody Map<String, Object> data) {
        String name = (String) data.get("name");
        double lat = Double.parseDouble(data.get("latitude").toString());
        double lng = Double.parseDouble(data.get("longitude").toString());

        Station station = new Station();
        station.setName(name);
        station.setLatitude(lat);
        station.setLongitude(lng);

        stationRepository.save(station);

        return ResponseEntity.ok("İstasyon başarıyla eklendi!");
    }


    @DeleteMapping("/delete/{id}")
    public ResponseEntity<String> deleteStation(@PathVariable Long id) {
        stationRepository.deleteById(id);
        return ResponseEntity.ok("Silindi");
    }


    @DeleteMapping("/delete-all")
    public ResponseEntity<String> deleteAll() {
        stationRepository.deleteAll();
        return ResponseEntity.ok("Tüm istasyonlar temizlendi.");

    }


    @PutMapping("/update/{id}")
    public ResponseEntity<String> updateStation(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String newName = body.get("name");

        return stationRepository.findById(id).map(station -> {
            station.setName(newName);
            stationRepository.save(station);
            return ResponseEntity.ok("İsim güncellendi.");
        }).orElse(ResponseEntity.notFound().build());
    }

}