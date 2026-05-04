package com.samadhanai.samadhanai.Location;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@Slf4j
public class LocationService {

    // ✅ No API key needed — Nominatim is 100% free
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String NOMINATIM_URL =
            "https://nominatim.openstreetmap.org/reverse";

    // ─────────────────────────────────────────────────────────────────
    // 📦 Result Object
    // ✅ FIX: Renamed LocationDetails → LocationResult
    //         (ComplaintService uses LocationService.LocationResult)
    // ─────────────────────────────────────────────────────────────────
    public static class LocationResult {

        private String ward;
        private String area;
        private String city;
        private String state;
        private String pincode;
        private String fullAddress;
        private boolean success;

        public String getWard()        { return ward; }
        public String getArea()        { return area; }
        public String getCity()        { return city; }
        public String getState()       { return state; }
        public String getPincode()     { return pincode; }
        public String getFullAddress() { return fullAddress; }
        public boolean isSuccess()     { return success; }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private final LocationResult loc = new LocationResult();
            public Builder ward(String w)        { loc.ward = w;        return this; }
            public Builder area(String a)        { loc.area = a;        return this; }
            public Builder city(String c)        { loc.city = c;        return this; }
            public Builder state(String s)       { loc.state = s;       return this; }
            public Builder pincode(String p)     { loc.pincode = p;     return this; }
            public Builder fullAddress(String f) { loc.fullAddress = f; return this; }
            public Builder success(boolean s)    { loc.success = s;     return this; }
            public LocationResult build()        { return loc; }
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // 🗺️ MAIN METHOD: Get Location from GPS Coordinates
    // ✅ FIX: Renamed getLocationDetails() → reverseGeocode()
    //         (ComplaintService uses locationService.reverseGeocode())
    //
    // Uses Nominatim (OpenStreetMap) — Free, No API key needed
    // Rate limit: 1 request/second — perfect for dev + demo
    // ─────────────────────────────────────────────────────────────────
    public LocationResult reverseGeocode(double latitude, double longitude) {

        log.info("Fetching location for coordinates: {}, {}", latitude, longitude);

        try {
            String url = NOMINATIM_URL +
                    "?lat=" + latitude +
                    "&lon=" + longitude +
                    "&format=json" +
                    "&addressdetails=1";

            // ⚠️ Nominatim requires User-Agent — without this request will fail
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "SamadhanAI/1.0 (samadhanai.complaints@gmail.com)");
            headers.set("Accept-Language", "en");

            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, String.class);

            log.info("Nominatim response received");
            return parseNominatimResponse(response.getBody(), latitude, longitude);

        } catch (Exception e) {
            log.error("Nominatim API call failed: {}", e.getMessage());
            return getFallbackLocation(latitude, longitude);
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // 🔍 Parse Nominatim Response
    // ─────────────────────────────────────────────────────────────────
    private LocationResult parseNominatimResponse(String responseJson,
                                                  double lat, double lng) {
        try {
            JsonNode root    = objectMapper.readTree(responseJson);
            JsonNode address = root.path("address");

            if (address.isMissingNode()) {
                log.warn("No address in Nominatim response");
                return getFallbackLocation(lat, lng);
            }

            // Ward — Nominatim uses "suburb" for locality/ward
            String ward = address.path("suburb").asText(null);
            if (ward == null) ward = address.path("residential").asText(null);
            if (ward == null) ward = address.path("hamlet").asText(null);

            // Area
            String area = address.path("neighbourhood").asText(null);
            if (area == null) area = address.path("quarter").asText(null);

            // City
            String city = address.path("city").asText(null);
            if (city == null) city = address.path("town").asText(null);
            if (city == null) city = address.path("village").asText(null);
            if (city == null) city = address.path("county").asText(null);

            // State
            String state = address.path("state").asText(null);

            // Pincode
            String pincode = address.path("postcode").asText("000000");

            // Full address
            String fullAddress = root.path("display_name").asText("Unknown Location");

            // Fallbacks
            if (ward == null && area != null) ward = area;
            if (ward == null && city != null) ward = city;
            if (area == null && city != null) area = city;

            log.info("Location parsed → Ward: {}, City: {}, Pin: {}",
                    ward, city, pincode);

            return LocationResult.builder()
                    .ward(ward    != null ? ward    : "Unknown Ward")
                    .area(area    != null ? area    : "Unknown Area")
                    .city(city    != null ? city    : "Unknown City")
                    .state(state  != null ? state   : "Unknown State")
                    .pincode(pincode)
                    .fullAddress(fullAddress)
                    .success(true)
                    .build();

        } catch (Exception e) {
            log.error("Failed to parse Nominatim response: {}", e.getMessage());
            return getFallbackLocation(lat, lng);
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // 🆘 Fallback — Nominatim fail hone par bhi complaint save hoti hai
    // ─────────────────────────────────────────────────────────────────
    private LocationResult getFallbackLocation(double lat, double lng) {
        log.warn("Using fallback location for: {}, {}", lat, lng);
        return LocationResult.builder()
                .ward("Unknown Ward")
                .area("Unknown Area")
                .city("Unknown City")
                .state("Unknown State")
                .pincode("000000")
                .fullAddress("Location: " + lat + ", " + lng)
                .success(false)
                .build();
    }
}