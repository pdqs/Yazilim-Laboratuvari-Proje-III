// Harita Merkezi (Kocaeli)
var map = L.map('map').setView([40.765, 29.94], 12);

// Altlık (Zemin) Harita
L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19, attribution: '© OpenStreetMap'
}).addTo(map);

// Tıklanan noktaları tutacak (Başlangıç ve Bitiş)
var selectedPoints = [];
var routeLayer = null; // Rota çizgisi (Kırmızı olan)

// 1. SENİN GEOSJON HARİTANI YÜKLE (MAVİ YOLLAR)
fetch('export.geojson')
    .then(response => response.json())
    .then(data => {
        L.geoJSON(data, {
            style: { color: "blue", weight: 3, opacity: 0.5 }
        }).addTo(map);
    })
    .catch(err => console.error("Harita yüklenemedi:", err));

// 2. HARİTAYA TIKLAMA OLAYI (ROTA HESAPLAMA)
map.on('click', function(e) {
    var lat = e.latlng.lat;
    var lng = e.latlng.lng;

    // Noktayı işaretle
    L.marker([lat, lng]).addTo(map).bindPopup("Seçilen Nokta").openPopup();
    selectedPoints.push({lat: lat, lng: lng});

    // Eğer 2 nokta seçildiyse ROTA HESAPLA
    if (selectedPoints.length === 2) {
        var start = selectedPoints[0];
        var end = selectedPoints[1];

        console.log("Rota hesaplanıyor...", start, end);

        // Backend'e sor (RouteController'a git)
        fetch(`/api/route?startLat=${start.lat}&startLon=${start.lng}&endLat=${end.lat}&endLon=${end.lng}`)
            .then(response => response.json())
            .then(routeCoordinates => {

                // Varsa eski rotayı sil
                if (routeLayer) map.removeLayer(routeLayer);

                // YENİ ROTAYI ÇİZ (Senin yollarının üzerinden geçer)
                // routeCoordinates: Java'nın senin geojson'dan bulup getirdiği koordinatlar
                routeLayer = L.polyline(routeCoordinates, {
                    color: 'red',   // Rota KIRMIZI olsun
                    weight: 6,      // Daha kalın olsun
                    opacity: 0.9
                }).addTo(map);

                // Haritayı rotaya odakla
                map.fitBounds(routeLayer.getBounds());

                // Seçimi sıfırla ki yeni rota çizebilesin
                selectedPoints = [];
            })
            .catch(err => console.error("Rota hatası:", err));
    }
});