package com.yazlab.kargo.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yazlab.kargo.entity.Cargo;
import com.yazlab.kargo.entity.Station;
import com.yazlab.kargo.entity.TripRun;
import com.yazlab.kargo.entity.Vehicle;
import com.yazlab.kargo.repository.CargoRepository;
import com.yazlab.kargo.repository.StationRepository;
import com.yazlab.kargo.repository.TripRunRepository;
import com.yazlab.kargo.repository.UserRepository;
import com.yazlab.kargo.repository.VehicleRepository;
import com.yazlab.kargo.service.RouteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/cargo")
public class CargoController {

    @Autowired private CargoRepository cargoRepository;
    @Autowired private StationRepository stationRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private VehicleRepository vehicleRepository;
    @Autowired private TripRunRepository tripRunRepository;
    @Autowired private RouteService routeService;

    private static final double KOU_LAT = 40.82224624200172;
    private static final double KOU_LON = 29.92156586537241;
    private static final String HUB_NAME = "Üniversite";

    private static final int DEFAULT_RENTAL_CAPACITY = 500;      // kg
    private static final double DEFAULT_FUEL_COST = 1.0;         // TL/km
    private static final double DEFAULT_RENTAL_COST = 200.0;     // TL / araç


    private static final int NEIGHBOR_K = 12;


    private static final boolean ASSUME_SYMMETRIC_DISTANCE = true;


    private static final int MAX_TOTAL_ROUTES = 500;

    private final ObjectMapper mapper = new ObjectMapper();


    private static final Map<PairKey, Double> DIST_CACHE = new ConcurrentHashMap<>();
    private static final Map<PairKey, List<String>> PATH_CACHE = new ConcurrentHashMap<>();

    private static final class PairKey {
        final long a;
        final long b;
        PairKey(long a, long b) { this.a = a; this.b = b; }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PairKey)) return false;
            PairKey p = (PairKey) o;
            return a == p.a && b == p.b;
        }
        @Override public int hashCode() { return Objects.hash(a, b); }
    }

    /** fromId/toId: 0 = HUB, diğerleri stationId */
    private double distKm(long fromId, long toId, Map<Long, Station> stationMap) {
        PairKey key = new PairKey(fromId, toId);
        Double cached = DIST_CACHE.get(key);
        if (cached != null) return cached;

        double lat1, lon1, lat2, lon2;

        if (fromId == 0) { lat1 = KOU_LAT; lon1 = KOU_LON; }
        else {
            Station s = stationMap.get(fromId);
            if (s == null) return Double.MAX_VALUE / 4;
            lat1 = s.getLatitude(); lon1 = s.getLongitude();
        }

        if (toId == 0) { lat2 = KOU_LAT; lon2 = KOU_LON; }
        else {
            Station s = stationMap.get(toId);
            if (s == null) return Double.MAX_VALUE / 4;
            lat2 = s.getLatitude(); lon2 = s.getLongitude();
        }

        double d = routeService.getRealRoadDistance(lat1, lon1, lat2, lon2);
        DIST_CACHE.put(key, d);
        if (ASSUME_SYMMETRIC_DISTANCE) DIST_CACHE.put(new PairKey(toId, fromId), d);
        return d;
    }

    private List<String> pathCoords(long fromId, long toId, Map<Long, Station> stationMap) {
        PairKey key = new PairKey(fromId, toId);
        List<String> cached = PATH_CACHE.get(key);
        if (cached != null) return cached;

        double lat1, lon1, lat2, lon2;

        if (fromId == 0) { lat1 = KOU_LAT; lon1 = KOU_LON; }
        else {
            Station s = stationMap.get(fromId);
            if (s == null) return Collections.emptyList();
            lat1 = s.getLatitude(); lon1 = s.getLongitude();
        }

        if (toId == 0) { lat2 = KOU_LAT; lon2 = KOU_LON; }
        else {
            Station s = stationMap.get(toId);
            if (s == null) return Collections.emptyList();
            lat2 = s.getLatitude(); lon2 = s.getLongitude();
        }

        List<String> coords = routeService.getShortestPath(lat1, lon1, lat2, lon2);
        PATH_CACHE.put(key, coords);
        return coords;
    }


    private double totalRouteDistanceKm(List<Long> route, Map<Long, Station> stationMap) {
        if (route == null || route.isEmpty()) return 0.0;
        double sum = 0.0;
        for (int i = 0; i < route.size() - 1; i++) {
            sum += distKm(route.get(i), route.get(i + 1), stationMap);
        }
        sum += distKm(route.get(route.size() - 1), 0, stationMap);
        return sum;
    }


    @GetMapping("/all")
    public ResponseEntity<List<Map<String, Object>>> getAllCargos() {
        List<Cargo> cargos = cargoRepository.findAll();
        List<Map<String, Object>> response = new ArrayList<>();

        for (Cargo c : cargos) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", c.getId());
            item.put("userId", c.getUserId());

            String username = "Bilinmiyor";
            if (c.getUserId() != null) {
                username = userRepository.findById(c.getUserId())
                        .map(u -> u.getUsername())
                        .orElse("Silinmiş Kullanıcı");
            }
            item.put("username", username);

            String stationName = "Belirsiz";
            if (c.getStation() != null) stationName = c.getStation().getName();
            item.put("stationName", stationName);

            item.put("weight", c.getWeight());
            item.put("quantity", c.getQuantity());
            item.put("status", c.getStatus());

            response.add(item);
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/my-cargos/{userId}")
    public ResponseEntity<List<Cargo>> getMyCargos(@PathVariable Long userId) {
        List<Cargo> mine = cargoRepository.findAll().stream()
                .filter(c -> Objects.equals(c.getUserId(), userId))
                .sorted(Comparator.comparing(Cargo::getId).reversed())
                .collect(Collectors.toList());
        return ResponseEntity.ok(mine);
    }

    @PostMapping("/create")
    public ResponseEntity<?> createCargo(@RequestBody Map<String, Object> payload) {
        try {
            if (payload.get("userId") == null || payload.get("stationId") == null || payload.get("weight") == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Eksik parametreler."));
            }

            Long userId = ((Number) payload.get("userId")).longValue();
            Long stationId = ((Number) payload.get("stationId")).longValue();
            int weight = ((Number) payload.get("weight")).intValue();
            int quantity = payload.get("quantity") == null ? 1 : ((Number) payload.get("quantity")).intValue();

            if (weight <= 0) return ResponseEntity.badRequest().body(Map.of("error", "Ağırlık 0'dan büyük olmalı."));
            if (quantity <= 0) return ResponseEntity.badRequest().body(Map.of("error", "Adet 0'dan büyük olmalı."));

            Optional<Station> stationOpt = stationRepository.findById(stationId);
            if (stationOpt.isEmpty()) return ResponseEntity.status(404).body(Map.of("error", "İstasyon bulunamadı."));

            Cargo newCargo = new Cargo();
            newCargo.setUserId(userId);
            newCargo.setStation(stationOpt.get());
            newCargo.setWeight(weight);
            newCargo.setQuantity(quantity);
            newCargo.setStatus("BEKLIYOR");

            cargoRepository.save(newCargo);
            return ResponseEntity.ok(Map.of("status", "CREATED", "id", newCargo.getId()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/station/{id}")
    public ResponseEntity<List<Cargo>> getCargosByStation(@PathVariable Long id) {
        return ResponseEntity.ok(cargoRepository.findAll().stream()
                .filter(c -> c.getStation() != null && c.getStation().getId().equals(id))
                .collect(Collectors.toList()));
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<Map<String, String>> deleteCargo(@PathVariable Long id) {
        if (cargoRepository.existsById(id)) {
            cargoRepository.deleteById(id);
            return ResponseEntity.ok(Map.of("status", "DELETED"));
        }
        return ResponseEntity.status(404).body(Map.of("error", "Kargo bulunamadı"));
    }

    @PostMapping("/reset-all")
    public ResponseEntity<Map<String, String>> resetAllCargos() {
        cargoRepository.deleteAll();
        return ResponseEntity.ok(Map.of("status", "ALL_CLEARED"));
    }

    @GetMapping("/runs")
    public ResponseEntity<List<TripRun>> getAllRuns() {
        return ResponseEntity.ok(tripRunRepository.findAll(Sort.by(Sort.Direction.DESC, "id")));
    }

    @PostMapping("/runs/{id}/rerun")
    public ResponseEntity<Map<String, Object>> rerunSimulation(@PathVariable Long id) {
        Optional<TripRun> runOpt = tripRunRepository.findById(id);
        if (runOpt.isEmpty()) return ResponseEntity.notFound().build();
        try {
            Map<String, Object> params = mapper.readValue(runOpt.get().getParamsJson(), new TypeReference<>() {});
            Double fuel = params.get("fuelCostPerKm") != null ? ((Number) params.get("fuelCostPerKm")).doubleValue() : null;
            Double rental = params.get("rentalCostPerVehicle") != null ? ((Number) params.get("rentalCostPerVehicle")).doubleValue() : null;
            Integer maxV = params.get("maxVehicles") != null ? ((Number) params.get("maxVehicles")).intValue() : null;
            Integer rentCap = params.get("rentalCapacityKg") != null ? ((Number) params.get("rentalCapacityKg")).intValue() : null;
            return deliverAll(fuel, rental, maxV, rentCap);
        } catch (JsonProcessingException e) {
            return ResponseEntity.status(500).body(Map.of("error", "JSON Hatası"));
        }
    }


    @PostMapping("/deliver-all")
    public ResponseEntity<Map<String, Object>> deliverAll(
            @RequestParam(name = "fuelCostPerKm", required = false) Double fuelCostParam,
            @RequestParam(name = "rentalCostPerVehicle", required = false) Double rentalCostParam,
            @RequestParam(name = "maxVehicles", required = false) Integer maxVehiclesParam,
            @RequestParam(name = "rentalCapacityKg", required = false) Integer rentalCapacityParam
    ) {
        final double fuelCostPerKm = (fuelCostParam != null) ? fuelCostParam : DEFAULT_FUEL_COST;
        final double rentalCostPerVehicle = (rentalCostParam != null) ? rentalCostParam : DEFAULT_RENTAL_COST;
        final int rentalCapacityKg = (rentalCapacityParam != null && rentalCapacityParam > 0) ? rentalCapacityParam : DEFAULT_RENTAL_CAPACITY;


        final int maxCompanyVehicles = (maxVehiclesParam != null && maxVehiclesParam > 0) ? maxVehiclesParam : Integer.MAX_VALUE;

        List<Cargo> waitingCargos = cargoRepository.findAll().stream()
                .filter(c -> "BEKLIYOR".equals(c.getStatus()))
                .collect(Collectors.toList());
        if (waitingCargos.isEmpty()) return ResponseEntity.ok(Map.of("status", "EMPTY"));

        Map<Long, Station> stationMap = stationRepository.findAll().stream()
                .collect(Collectors.toMap(Station::getId, s -> s));

        Map<Long, Integer> loadRemaining = new HashMap<>();
        for (Cargo c : waitingCargos) {
            if (c.getStation() == null || c.getStation().getId() == null) continue;
            long stId = c.getStation().getId();
            int w = safeWeight(c);
            if (w > 0) loadRemaining.merge(stId, w, Integer::sum);
        }

        int totalRemaining = loadRemaining.values().stream().mapToInt(v -> v == null ? 0 : v).sum();
        if (totalRemaining <= 0) return ResponseEntity.ok(Map.of("status", "EMPTY"));

        // şirket araçları
        List<Vehicle> fleet = vehicleRepository.findAll(Sort.by(Sort.Direction.ASC, "id"));
        for (Vehicle v : fleet) { v.setCurrentLoad(0); v.setAvailable(true); }
        vehicleRepository.saveAll(fleet);

        Set<Long> unusedCompanyVehicleIds = fleet.stream()
                .map(Vehicle::getId).filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // komşu haritası (yalnız aday filtresi)
        Map<Long, List<Long>> neighborMap = buildNeighborMap(loadRemaining, stationMap, NEIGHBOR_K);

        // hub air distance (izole/uzak kuralı için)
        Map<Long, Double> hubAirKm = new HashMap<>();
        List<Double> airList = new ArrayList<>();
        for (Long id : loadRemaining.keySet()) {
            if (!stationMap.containsKey(id)) continue;
            Station s = stationMap.get(id);
            if (s == null) continue;
            double d = haversineKm(s.getLatitude(), s.getLongitude(), KOU_LAT, KOU_LON);
            hubAirKm.put(id, d);
            airList.add(d);
        }
        double farThreshold = percentile(airList, 75.0); // üst %25 "uzak"

        List<List<Long>> acceptedRoutes = new ArrayList<>();
        List<Integer> acceptedLoads = new ArrayList<>();
        List<LinkedHashMap<Long, Integer>> acceptedPickedMaps = new ArrayList<>();
        List<Boolean> acceptedIsCompany = new ArrayList<>();
        List<Long> acceptedCompanyVehicleIds = new ArrayList<>();

        Map<Long, Integer> loadCopy = new HashMap<>(loadRemaining);

        int safety = 0;
        while (totalRemaining > 0 && safety++ < MAX_TOTAL_ROUTES) {


            int usedCompanyCount = (int) acceptedIsCompany.stream().filter(Boolean::booleanValue).count();

            List<CandidateVehicle> candidates = new ArrayList<>();
            if (usedCompanyCount < maxCompanyVehicles) {
                for (Vehicle v : fleet) {
                    if (v.getId() != null && unusedCompanyVehicleIds.contains(v.getId())) {
                        int cap = (v.getCapacity() == null) ? 0 : v.getCapacity().intValue();
                        if (cap > 0) candidates.add(CandidateVehicle.company(v.getId(), cap));
                    }
                }
            }

            candidates.add(CandidateVehicle.rental(rentalCapacityKg));


            if (loadCopy.values().stream().allMatch(x -> x == null || x <= 0)) break;


            int iterMaxCap = candidates.stream().mapToInt(c -> c.capacityKg).max().orElse(0);
            if (iterMaxCap <= 0) break;


            Long seed = chooseSeedStation(loadCopy, stationMap, neighborMap, hubAirKm, farThreshold, iterMaxCap);
            if (seed == null) break;

            int seedRem = loadCopy.getOrDefault(seed, 0);

            Plan best = null;

            for (CandidateVehicle cand : candidates) {
                if (cand.capacityKg <= 0) continue;


                if (seedRem > 0 && seedRem <= iterMaxCap && cand.capacityKg < seedRem) continue;

                Plan p = buildRoutePlanTwoEndedGreedy(
                        loadCopy, stationMap, neighborMap,
                        cand.capacityKg, seed,
                        hubAirKm, farThreshold, iterMaxCap
                );

                if (p == null || p.loadedKg <= 0 || p.route.isEmpty()) continue;

                double dist = totalRouteDistanceKm(p.route, stationMap);
                double fuel = dist * fuelCostPerKm;
                double rental = cand.isCompany ? 0.0 : rentalCostPerVehicle;
                double total = fuel + rental;

                p.distanceKm = dist;
                p.totalCost = total;
                p.isCompany = cand.isCompany;
                p.companyVehicleId = cand.companyVehicleId;
                p.capacityKg = cand.capacityKg;


                if (best == null) best = p;
                else {
                    if (p.totalCost < best.totalCost - 1e-9) best = p;
                    else if (Math.abs(p.totalCost - best.totalCost) < 1e-9) {
                        if (p.loadedKg > best.loadedKg) best = p;
                        else if (p.loadedKg == best.loadedKg) {
                            if (p.distanceKm < best.distanceKm - 1e-9) best = p;
                            else if (Math.abs(p.distanceKm - best.distanceKm) < 1e-9) {
                                // büyük araçları boşa harcama: daha küçük kapasiteyi seç
                                if (p.capacityKg < best.capacityKg) best = p;
                            }
                        }
                    }
                }
            }

            if (best == null) break;

            int pickedSum = applyPickedAndCount(loadCopy, best.pickedMap);
            if (pickedSum <= 0) break; // ilerleme yoksa çık
            totalRemaining -= pickedSum;

            acceptedRoutes.add(best.route);
            acceptedLoads.add(best.loadedKg);
            acceptedPickedMaps.add(best.pickedMap);
            acceptedIsCompany.add(best.isCompany);
            acceptedCompanyVehicleIds.add(best.isCompany ? best.companyVehicleId : null);

            if (best.isCompany && best.companyVehicleId != null) {
                unusedCompanyVehicleIds.remove(best.companyVehicleId);
            }
        }

        // cost & assignments
        CostResult costResult = computeCost(acceptedRoutes, stationMap, fuelCostPerKm, rentalCostPerVehicle, acceptedIsCompany);

        List<Map<String, Object>> routeAssignments =
                buildRouteAssignments(
                        acceptedRoutes, acceptedPickedMaps, waitingCargos, stationMap,
                        fuelCostPerKm, rentalCostPerVehicle, acceptedIsCompany
                );

        // DB araç güncelle
        for (int i = 0; i < acceptedRoutes.size(); i++) {
            if (acceptedIsCompany.get(i)) {
                Long vid = acceptedCompanyVehicleIds.get(i);
                if (vid == null) continue;
                Vehicle v = fleet.stream().filter(x -> Objects.equals(x.getId(), vid)).findFirst().orElse(null);
                if (v != null) {
                    v.setCurrentLoad(acceptedLoads.get(i));
                    v.setAvailable(false);
                    vehicleRepository.save(v);
                }
            }
        }

        long rentedCount = acceptedIsCompany.stream().filter(b -> !b).count();
        double totalRentalCost = rentedCount * rentalCostPerVehicle;

        // kalan yük varsa
        List<List<Long>> rejectedRoutes = new ArrayList<>();
        if (loadCopy.values().stream().anyMatch(v -> v != null && v > 0)) {
            List<Long> leftoverStations = loadCopy.entrySet().stream()
                    .filter(e -> e.getValue() != null && e.getValue() > 0)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
            if (!leftoverStations.isEmpty()) rejectedRoutes.add(leftoverStations);
        }

        Map<String, Object> resp = new HashMap<>();
        resp.put("status", rejectedRoutes.isEmpty() ? "SUCCESS" : "PARTIAL");

        resp.put("routes", acceptedRoutes);
        resp.put("rejectedRoutes", rejectedRoutes);

        resp.put("fuelCostPerKm", fuelCostPerKm);
        resp.put("rentalCostPerVehicle", rentalCostPerVehicle);
        resp.put("rentalCapacityKg", rentalCapacityKg);
        resp.put("maxCompanyVehicles", (maxCompanyVehicles == Integer.MAX_VALUE ? null : maxCompanyVehicles));

        resp.put("totalDistanceKm", costResult.totalDistanceKm);
        resp.put("totalFuelCost", costResult.totalFuelCost);

        resp.put("rentedVehicleCount", rentedCount);
        resp.put("totalRentalCost", totalRentalCost);
        resp.put("totalCostEstimate", costResult.totalFuelCost + totalRentalCost);

        resp.put("routeCosts", costResult.routeCosts);
        resp.put("routeAssignments", routeAssignments);

        logRun(resp, fuelCostPerKm, rentalCostPerVehicle, maxCompanyVehicles == Integer.MAX_VALUE ? null : maxCompanyVehicles, rentalCapacityKg);

        return ResponseEntity.ok(resp);
    }


    private Plan buildRoutePlanTwoEndedGreedy(
            Map<Long, Integer> loadMap,
            Map<Long, Station> stationMap,
            Map<Long, List<Long>> neighborMap,
            int capacityKg,
            Long seed,
            Map<Long, Double> hubAirKm,
            double farThreshold,
            int iterMaxCap
    ) {
        if (capacityKg <= 0 || seed == null) return null;

        Map<Long, Integer> local = new HashMap<>(loadMap);
        LinkedHashMap<Long, Integer> picked = new LinkedHashMap<>();
        int capLeft = capacityKg;

        Deque<Long> route = new ArrayDeque<>();
        Set<Long> inRoute = new HashSet<>();


        capLeft = pickFromStation(local, seed, capLeft, picked);
        if (picked.getOrDefault(seed, 0) <= 0) return null;

        route.add(seed);
        inRoute.add(seed);

        while (capLeft > 0) {
            Long head = route.peekFirst();
            Long tail = route.peekLast();

            LinkedHashSet<Long> pool = new LinkedHashSet<>();
            for (Long x : neighborMap.getOrDefault(head, Collections.emptyList())) pool.add(x);
            for (Long x : neighborMap.getOrDefault(tail, Collections.emptyList())) pool.add(x);

            if (pool.isEmpty()) {
                Long fallback = maxLoadStation(local, stationMap, inRoute);
                if (fallback != null) pool.add(fallback);
            }

            Long bestSt = null;
            boolean bestToHead = true;
            double bestScore = Double.MAX_VALUE;

            for (Long cand : pool) {
                if (cand == null) continue;
                if (inRoute.contains(cand)) continue;
                if (!stationMap.containsKey(cand)) continue;

                int rem = local.getOrDefault(cand, 0);
                if (rem <= 0) continue;


                double air = hubAirKm.getOrDefault(cand, 0.0);
                boolean far = air >= farThreshold;
                boolean oneShotPossible = rem <= iterMaxCap;
                if (far && oneShotPossible && capLeft < rem) {
                    continue;
                }

                int takeKg = Math.min(capLeft, rem);
                if (takeKg <= 0) continue;


                double extraHead = distKm(cand, head, stationMap);


                double tailToHub = distKm(tail, 0, stationMap);
                double extraTail = distKm(tail, cand, stationMap) + distKm(cand, 0, stationMap) - tailToHub;
                if (extraTail < 0) extraTail = 0;


                double scoreHead = extraHead / Math.max(1, takeKg);
                double scoreTail = extraTail / Math.max(1, takeKg);

                if (scoreHead < bestScore) {
                    bestScore = scoreHead;
                    bestSt = cand;
                    bestToHead = true;
                }
                if (scoreTail < bestScore) {
                    bestScore = scoreTail;
                    bestSt = cand;
                    bestToHead = false;
                }
            }

            if (bestSt == null) break;

            if (bestToHead) route.addFirst(bestSt);
            else route.addLast(bestSt);
            inRoute.add(bestSt);

            capLeft = pickFromStation(local, bestSt, capLeft, picked);
        }

        Plan p = new Plan();
        p.route = new ArrayList<>(route);
        p.pickedMap = picked;
        p.loadedKg = capacityKg - capLeft;
        return p;
    }

    private Long maxLoadStation(Map<Long, Integer> local, Map<Long, Station> stationMap, Set<Long> inRoute) {
        Long best = null;
        int bestLoad = 0;
        for (Map.Entry<Long,Integer> e : local.entrySet()) {
            Long id = e.getKey();
            int rem = e.getValue()==null?0:e.getValue();
            if (rem <= 0) continue;
            if (inRoute.contains(id)) continue;
            if (!stationMap.containsKey(id)) continue;
            if (rem > bestLoad) {
                bestLoad = rem;
                best = id;
            }
        }
        return best;
    }

    private Long chooseSeedStation(
            Map<Long, Integer> loadMap,
            Map<Long, Station> stationMap,
            Map<Long, List<Long>> neighborMap,
            Map<Long, Double> hubAirKm,
            double farThreshold,
            int iterMaxCap
    ) {
        Long bestFar = null;
        double bestFarScore = -1;


        for (Map.Entry<Long, Integer> e : loadMap.entrySet()) {
            Long id = e.getKey();
            int rem = e.getValue() == null ? 0 : e.getValue();
            if (rem <= 0) continue;
            if (!stationMap.containsKey(id)) continue;

            double air = hubAirKm.getOrDefault(id, 0.0);
            if (air < farThreshold) continue;
            if (rem > iterMaxCap) continue;

            double score = air * 1000.0 + rem; // önce uzaklık, sonra yük
            if (score > bestFarScore) {
                bestFarScore = score;
                bestFar = id;
            }
        }
        if (bestFar != null) return bestFar;


        Long best = null;
        double bestScore = -1;

        for (Map.Entry<Long, Integer> e : loadMap.entrySet()) {
            Long id = e.getKey();
            int rem = e.getValue() == null ? 0 : e.getValue();
            if (rem <= 0) continue;

            Station si = stationMap.get(id);
            if (si == null) continue;

            int clusterKg = rem;
            double sumIso = 0.0;
            int isoCount = 0;

            List<Long> neigh = neighborMap.getOrDefault(id, Collections.emptyList());
            for (int i = 0; i < neigh.size() && isoCount < 3; i++) {
                Long nb = neigh.get(i);
                if (nb == null) continue;
                int nbLoad = loadMap.getOrDefault(nb, 0);
                if (nbLoad <= 0) continue;

                Station sj = stationMap.get(nb);
                if (sj == null) continue;

                clusterKg += nbLoad;
                sumIso += haversineKm(si.getLatitude(), si.getLongitude(), sj.getLatitude(), sj.getLongitude());
                isoCount++;
            }

            double isoKm = (isoCount > 0) ? (sumIso / isoCount) : 50.0;
            double density = clusterKg / (1.0 + isoKm);

            // hafif uzaklık bonusu: (ama tek başına uzak diye seed olmasın)
            double air = hubAirKm.getOrDefault(id, 0.0);
            double score = density * 1000.0 + rem * 2.0 + air * 0.5;

            if (score > bestScore) {
                bestScore = score;
                best = id;
            }
        }

        return best;
    }


    private Map<Long, List<Long>> buildNeighborMap(Map<Long, Integer> loadMap, Map<Long, Station> stationMap, int k) {
        List<Long> active = loadMap.entrySet().stream()
                .filter(e -> e.getValue() != null && e.getValue() > 0 && stationMap.containsKey(e.getKey()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        Map<Long, List<Long>> res = new HashMap<>();
        for (Long a : active) {
            Station sa = stationMap.get(a);
            if (sa == null) continue;

            List<Long> sorted = new ArrayList<>(active);
            sorted.remove(a);

            sorted.sort(Comparator.comparingDouble(b -> {
                Station sb = stationMap.get(b);
                if (sb == null) return Double.MAX_VALUE;
                return haversineKm(sa.getLatitude(), sa.getLongitude(), sb.getLatitude(), sb.getLongitude());
            }));

            if (sorted.size() > k) sorted = sorted.subList(0, k);
            res.put(a, sorted);
        }
        return res;
    }

    private double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    private double percentile(List<Double> values, double p) {
        if (values == null || values.isEmpty()) return 0.0;
        List<Double> v = new ArrayList<>(values);
        v.sort(Double::compareTo);
        double idx = (p / 100.0) * (v.size() - 1);
        int lo = (int) Math.floor(idx);
        int hi = (int) Math.ceil(idx);
        if (lo == hi) return v.get(lo);
        double frac = idx - lo;
        return v.get(lo) * (1 - frac) + v.get(hi) * frac;
    }


    private int pickFromStation(Map<Long, Integer> local, Long stId, int capLeft, LinkedHashMap<Long, Integer> pickedMap) {
        int rem = local.getOrDefault(stId, 0);
        if (capLeft <= 0 || rem <= 0) return capLeft;

        int take = Math.min(capLeft, rem);
        if (take <= 0) return capLeft;

        local.put(stId, rem - take);
        pickedMap.merge(stId, take, Integer::sum);
        return capLeft - take;
    }

    private int applyPickedAndCount(Map<Long, Integer> loadMap, LinkedHashMap<Long, Integer> pickedMap) {
        int sum = 0;
        for (Map.Entry<Long, Integer> e : pickedMap.entrySet()) {
            Long stId = e.getKey();
            int picked = e.getValue() == null ? 0 : e.getValue();
            if (picked <= 0) continue;
            int before = loadMap.getOrDefault(stId, 0);
            int dec = Math.min(before, picked);
            loadMap.put(stId, Math.max(0, before - picked));
            sum += dec;
        }
        return sum;
    }

    private CostResult computeCost(List<List<Long>> routes, Map<Long, Station> stationMap,
                                   double fuelCostPerKm, double rentalCostPerVehicle, List<Boolean> isCompanyList) {
        double totalDistanceKm = 0.0;
        double totalFuelCost = 0.0;
        List<Map<String, Object>> routeCosts = new ArrayList<>();

        for (int i = 0; i < routes.size(); i++) {
            List<Long> route = routes.get(i);
            double distKm = totalRouteDistanceKm(route, stationMap);

            double fuelCost = distKm * fuelCostPerKm;
            boolean isCompany = (isCompanyList != null && i < isCompanyList.size() && isCompanyList.get(i));
            double rentalCost = isCompany ? 0.0 : rentalCostPerVehicle;

            totalDistanceKm += distKm;
            totalFuelCost += fuelCost;

            Map<String, Object> rc = new HashMap<>();
            rc.put("routeIndex", i + 1);
            rc.put("stationIds", route);
            rc.put("distanceKm", distKm);
            rc.put("fuelCostPerKm", fuelCostPerKm);
            rc.put("fuelCost", fuelCost);
            rc.put("rentalCost", rentalCost);
            rc.put("totalCost", fuelCost + rentalCost);

            routeCosts.add(rc);
        }

        return new CostResult(totalDistanceKm, totalFuelCost, routeCosts);
    }

    private List<Map<String, Object>> buildRouteAssignments(
            List<List<Long>> routes,
            List<LinkedHashMap<Long, Integer>> pickedMaps,
            List<Cargo> cargos,
            Map<Long, Station> stationMap,
            double fuelCostPerKm,
            double rentalCostPerVehicle,
            List<Boolean> isCompanyList
    ) {
        List<Map<String, Object>> list = new ArrayList<>();
        int vehicleIdx = 1;

        Map<Long, String> usernameCache = new HashMap<>();

        for (int i = 0; i < routes.size(); i++) {
            List<Long> route = routes.get(i);
            Set<Long> routeSet = new HashSet<>(route);

            Set<String> users = cargos.stream()
                    .filter(c -> c.getStation() != null && routeSet.contains(c.getStation().getId()))
                    .map(c -> usernameCache.computeIfAbsent(
                            c.getUserId(),
                            id -> userRepository.findById(id).map(u -> u.getUsername()).orElse("Bilinmiyor")
                    ))
                    .collect(Collectors.toSet());

            double distKm = totalRouteDistanceKm(route, stationMap);
            double fuelCost = distKm * fuelCostPerKm;

            boolean isCompany = (isCompanyList != null && i < isCompanyList.size() && isCompanyList.get(i));
            double rentalCost = isCompany ? 0.0 : rentalCostPerVehicle;
            double totalCost = fuelCost + rentalCost;


            List<String> fullPathCoords = new ArrayList<>();
            if (!route.isEmpty()) {
                for (int k = 0; k < route.size() - 1; k++) {
                    fullPathCoords.addAll(pathCoords(route.get(k), route.get(k + 1), stationMap));
                }
                fullPathCoords.addAll(pathCoords(route.get(route.size() - 1), 0, stationMap));
            }

            LinkedHashMap<Long, Integer> pickedMap = (pickedMaps != null && i < pickedMaps.size())
                    ? pickedMaps.get(i)
                    : new LinkedHashMap<>();


            List<Map<String, Object>> steps = new ArrayList<>();
            int carried = 0;

            for (int s = 0; s < route.size(); s++) {
                Long fromId = route.get(s);
                Long toId = (s + 1 < route.size()) ? route.get(s + 1) : null;

                Station fromSt = stationMap.get(fromId);
                Station toSt = (toId != null) ? stationMap.get(toId) : null;

                String fromName = (fromSt != null && fromSt.getName() != null) ? fromSt.getName() : ("İstasyon " + fromId);
                String toName = (toSt != null && toSt.getName() != null) ? toSt.getName() : (toId != null ? ("İstasyon " + toId) : HUB_NAME);

                int pickedKg = pickedMap.getOrDefault(fromId, 0);
                carried += pickedKg;

                Map<String, Object> stepRow = new LinkedHashMap<>();
                stepRow.put("step", s + 1);
                stepRow.put("from", fromName);
                stepRow.put("to", toName);
                stepRow.put("pickedKg", pickedKg);
                stepRow.put("carriedKg", carried);
                steps.add(stepRow);
            }

            String startName = "Belirsiz";
            if (!route.isEmpty()) {
                Station st = stationMap.get(route.get(0));
                startName = (st != null && st.getName() != null) ? st.getName() : ("İstasyon " + route.get(0));
            }

            Map<String, Object> item = new HashMap<>();
            item.put("vehicleIndex", vehicleIdx++);
            item.put("startStation", startName);
            item.put("stationIds", route);
            item.put("users", users);
            item.put("distanceKm", distKm);
            item.put("fuelCostPerKm", fuelCostPerKm);
            item.put("fuelCost", fuelCost);
            item.put("rentalCost", rentalCost);
            item.put("totalCost", totalCost);
            item.put("detailedPath", fullPathCoords);
            item.put("steps", steps);

            list.add(item);
        }
        return list;
    }

    private void logRun(Map<String, Object> resp, double fuelCostPerKm, double rentalCostPerVehicle, Integer maxCompanyVehicles, Integer rentalCapacityKg) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("fuelCostPerKm", fuelCostPerKm);
            params.put("rentalCostPerVehicle", rentalCostPerVehicle);
            params.put("maxCompanyVehicles", maxCompanyVehicles);
            params.put("rentalCapacityKg", rentalCapacityKg);
            params.put("timestamp", Instant.now().toString());

            Map<String, Object> routesMap = new HashMap<>();
            routesMap.put("routes", resp.get("routes"));
            routesMap.put("rejectedRoutes", resp.get("rejectedRoutes"));

            Map<String, Object> costsMap = new HashMap<>();
            costsMap.put("totalDistanceKm", resp.get("totalDistanceKm"));
            costsMap.put("totalFuelCost", resp.get("totalFuelCost"));
            costsMap.put("totalRentalCost", resp.get("totalRentalCost"));
            costsMap.put("totalCostEstimate", resp.get("totalCostEstimate"));

            TripRun run = new TripRun();
            run.setParamsJson(mapper.writeValueAsString(params));
            run.setRoutesJson(mapper.writeValueAsString(routesMap));
            run.setCostsJson(mapper.writeValueAsString(costsMap));
            tripRunRepository.save(run);
        } catch (Exception ignored) {
            System.err.println("Run loglanamadı: " + ignored.getMessage());
        }
    }

    private int safeWeight(Cargo c) {
        Integer w = c.getWeight(); // int ise de boxing olur
        return (w == null) ? 0 : w;
    }

    private static class CostResult {
        double totalDistanceKm;
        double totalFuelCost;
        List<Map<String, Object>> routeCosts;
        CostResult(double d, double f, List<Map<String, Object>> rc) {
            this.totalDistanceKm = d;
            this.totalFuelCost = f;
            this.routeCosts = rc;
        }
    }

    private static class CandidateVehicle {
        boolean isCompany;
        Long companyVehicleId;
        int capacityKg;

        static CandidateVehicle company(Long id, int cap) {
            CandidateVehicle c = new CandidateVehicle();
            c.isCompany = true;
            c.companyVehicleId = id;
            c.capacityKg = cap;
            return c;
        }

        static CandidateVehicle rental(int cap) {
            CandidateVehicle c = new CandidateVehicle();
            c.isCompany = false;
            c.companyVehicleId = null;
            c.capacityKg = cap;
            return c;
        }
    }

    private static class Plan {
        List<Long> route = new ArrayList<>();
        LinkedHashMap<Long, Integer> pickedMap = new LinkedHashMap<>();
        int loadedKg;

        double distanceKm;
        double totalCost;
        boolean isCompany;
        Long companyVehicleId;
        int capacityKg;
    }
}
