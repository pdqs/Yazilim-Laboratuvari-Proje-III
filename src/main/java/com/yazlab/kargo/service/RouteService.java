// SENİN GÖNDERDİĞİN RouteService HALİ (çalışıyor demiştin)
// Burada tek kritik: loadFromGeoJson + processCoordinates zaten doğru.

package com.yazlab.kargo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.*;

@Service
public class RouteService {

    private Map<Long, Node> nodes = new HashMap<>();
    private Map<Long, List<Edge>> adjList = new HashMap<>();

    private Map<String, Long> uniqueNodeMap = new HashMap<>();
    private long nodeIdCounter = 1;

    private Map<String, Double> distanceCache = new HashMap<>();

    @PostConstruct
    public void init() {
        loadFromGeoJson("export.geojson");
    }

    public double getRealRoadDistance(double startLat, double startLon, double endLat, double endLon) {
        Long startNodeId = findNearestNode(startLat, startLon);
        Long endNodeId = findNearestNode(endLat, endLon);

        if (startNodeId == null || endNodeId == null) {
            return haversine(startLat, startLon, endLat, endLon);
        }
        if (startNodeId.equals(endNodeId)) return 0.0;

        String cacheKey = startNodeId + "-" + endNodeId;
        if (distanceCache.containsKey(cacheKey)) return distanceCache.get(cacheKey);

        PathResult result = dijkstra(startNodeId, endNodeId);
        if (result.distance == Double.MAX_VALUE) {
            return haversine(startLat, startLon, endLat, endLon);
        }

        distanceCache.put(cacheKey, result.distance);
        distanceCache.put(endNodeId + "-" + startNodeId, result.distance);
        return result.distance;
    }

    public List<String> getShortestPath(double startLat, double startLon, double endLat, double endLon) {
        Long startNodeId = findNearestNode(startLat, startLon);
        Long endNodeId = findNearestNode(endLat, endLon);

        if (startNodeId == null || endNodeId == null) return new ArrayList<>();

        PathResult result = dijkstra(startNodeId, endNodeId);

        List<String> pathCoords = new ArrayList<>();
        for (Long nodeId : result.path) {
            Node n = nodes.get(nodeId);
            if (n != null) pathCoords.add(n.lat + "," + n.lon);
        }
        return pathCoords;
    }

    public double getRoundTripDistanceKm(
            double hubLat, double hubLon,
            List<Long> stationIds,
            Map<Long, com.yazlab.kargo.entity.Station> stationMap
    ) {
        if (stationIds == null || stationIds.isEmpty()) return 0.0;

        double total = 0.0;

        var first = stationMap.get(stationIds.get(0));
        if (first != null) total += getRealRoadDistance(hubLat, hubLon, first.getLatitude(), first.getLongitude());

        for (int i = 0; i < stationIds.size() - 1; i++) {
            var a = stationMap.get(stationIds.get(i));
            var b = stationMap.get(stationIds.get(i + 1));
            if (a == null || b == null) continue;
            total += getRealRoadDistance(a.getLatitude(), a.getLongitude(), b.getLatitude(), b.getLongitude());
        }

        var last = stationMap.get(stationIds.get(stationIds.size() - 1));
        if (last != null) total += getRealRoadDistance(last.getLatitude(), last.getLongitude(), hubLat, hubLon);

        return total;
    }

    private void loadFromGeoJson(String fileName) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            File file = new File(fileName);
            if (!file.exists()) {
                System.err.println("UYARI: export.geojson dosyası bulunamadı! Mock data yükleniyor.");
                loadMockData();
                return;
            }

            JsonNode root = mapper.readTree(file);
            JsonNode features = root.path("features");
            if (features.isMissingNode()) {
                System.err.println("GeoJSON formatı hatalı: 'features' alanı yok.");
                return;
            }

            for (JsonNode feature : features) {
                JsonNode geometry = feature.path("geometry");
                String type = geometry.path("type").asText();
                JsonNode coordinates = geometry.path("coordinates");

                if ("LineString".equalsIgnoreCase(type)) {
                    processCoordinates(coordinates);
                } else if ("Polygon".equalsIgnoreCase(type)) {
                    if (coordinates.size() > 0) processCoordinates(coordinates.get(0));
                } else if ("MultiPolygon".equalsIgnoreCase(type)) {
                    for (JsonNode poly : coordinates) {
                        if (poly.size() > 0) processCoordinates(poly.get(0));
                    }
                }
            }

            System.out.println("GeoJSON Başarıyla Yüklendi!");
            System.out.println("   -> Toplam Nokta (Node): " + nodes.size());
            System.out.println("   -> Toplam Bağlantı (Edge): " + adjList.values().stream().mapToInt(List::size).sum());

        } catch (IOException e) {
            e.printStackTrace();
            loadMockData();
        }
    }

    private void processCoordinates(JsonNode coordinates) {
        Long prevNodeId = null;

        for (JsonNode coord : coordinates) {
            double lon = coord.get(0).asDouble();
            double lat = coord.get(1).asDouble();

            String key = String.format("%.6f,%.6f", lat, lon);
            Long currentNodeId;

            if (uniqueNodeMap.containsKey(key)) {
                currentNodeId = uniqueNodeMap.get(key);
            } else {
                currentNodeId = nodeIdCounter++;
                nodes.put(currentNodeId, new Node(lat, lon));
                uniqueNodeMap.put(key, currentNodeId);
            }

            if (prevNodeId != null && !prevNodeId.equals(currentNodeId)) {
                double dist = haversine(
                        nodes.get(prevNodeId).lat, nodes.get(prevNodeId).lon,
                        nodes.get(currentNodeId).lat, nodes.get(currentNodeId).lon
                );
                addEdge(prevNodeId, currentNodeId, dist);
                addEdge(currentNodeId, prevNodeId, dist);
            }

            prevNodeId = currentNodeId;
        }
    }

    private void loadMockData() {
        nodes.put(1L, new Node(40.8222, 29.9215));
        nodes.put(2L, new Node(40.7654, 29.9408));
        addEdge(1L, 2L, 8.5);
        addEdge(2L, 1L, 8.5);
    }

    private PathResult dijkstra(Long startId, Long endId) {
        Map<Long, Double> distances = new HashMap<>();
        Map<Long, Long> previous = new HashMap<>();
        PriorityQueue<NodeDistance> pq = new PriorityQueue<>(Comparator.comparingDouble(nd -> nd.dist));

        distances.put(startId, 0.0);
        pq.add(new NodeDistance(startId, 0.0));
        Set<Long> visited = new HashSet<>();

        while (!pq.isEmpty()) {
            NodeDistance current = pq.poll();
            Long u = current.nodeId;
            if (u.equals(endId)) break;
            if (visited.contains(u)) continue;
            visited.add(u);

            if (adjList.containsKey(u)) {
                for (Edge edge : adjList.get(u)) {
                    if (visited.contains(edge.targetNodeId)) continue;
                    double newDist = distances.get(u) + edge.weight;
                    if (newDist < distances.getOrDefault(edge.targetNodeId, Double.MAX_VALUE)) {
                        distances.put(edge.targetNodeId, newDist);
                        previous.put(edge.targetNodeId, u);
                        pq.add(new NodeDistance(edge.targetNodeId, newDist));
                    }
                }
            }
        }

        List<Long> path = new LinkedList<>();
        Long curr = endId;
        if (!previous.containsKey(curr) && !startId.equals(endId)) return new PathResult(new ArrayList<>(), Double.MAX_VALUE);
        while (curr != null) {
            path.add(0, curr);
            curr = previous.get(curr);
        }
        return new PathResult(path, distances.getOrDefault(endId, Double.MAX_VALUE));
    }

    private Long findNearestNode(double lat, double lon) {
        Long bestNode = null;
        double minDist = Double.MAX_VALUE;
        for (Map.Entry<Long, Node> entry : nodes.entrySet()) {
            double d = haversine(lat, lon, entry.getValue().lat, entry.getValue().lon);
            if (d < minDist) {
                minDist = d;
                bestNode = entry.getKey();
            }
        }
        return bestNode;
    }

    private double haversine(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a =
                Math.sin(dLat/2)*Math.sin(dLat/2) +
                        Math.cos(Math.toRadians(lat1))*Math.cos(Math.toRadians(lat2)) *
                                Math.sin(dLon/2)*Math.sin(dLon/2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        return R * c;
    }

    private void addEdge(Long from, Long to, double weight) {
        adjList.computeIfAbsent(from, k -> new ArrayList<>()).add(new Edge(to, weight));
    }

    private static class Node { double lat, lon; Node(double lat, double lon) { this.lat = lat; this.lon = lon; } }
    private static class Edge { Long targetNodeId; double weight; Edge(Long targetNodeId, double weight) { this.targetNodeId = targetNodeId; this.weight = weight; } }
    private static class NodeDistance { Long nodeId; double dist; NodeDistance(Long nodeId, double dist) { this.nodeId = nodeId; this.dist = dist; } }
    private static class PathResult { List<Long> path; double distance; PathResult(List<Long> path, double distance) { this.path = path; this.distance = distance; } }
}
