package com.yazlab.kargo.repository;

import com.yazlab.kargo.entity.Cargo;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CargoRepository extends JpaRepository<Cargo, Long> {


    List<Cargo> findByStationId(Long stationId);


    List<Cargo> findByUserId(Long userId);
}