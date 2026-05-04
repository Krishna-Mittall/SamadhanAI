package com.samadhanai.samadhanai.Ai;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.GpsDirectory;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class FakePhotoDetectionService {

    @Value("${spring.ai.openai.api-key}")
    private String groqApiKey;

    private static final String GROQ_URL   = "https://api.groq.com/openai/v1/chat/completions";
    private static final String GROQ_MODEL = "meta-llama/llama-4-scout-17b-16e-instruct";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient   httpClient   = HttpClient.newHttpClient();

    // ✅ STRICT: 500m threshold
    private static final double GPS_MISMATCH_THRESHOLD_KM = 0.5;

    // ─────────────────────────────────────────────────────────────────
    // 📦 Result Object
    // ─────────────────────────────────────────────────────────────────
    public static class FakeDetectionResult {

        private boolean isReal;
        private String  reason;
        private Double  photoGpsLat;
        private Double  photoGpsLng;
        private String  deviceModel;
        private boolean passedFileTypeCheck;
        private boolean passedMetadataCheck;
        private boolean passedGpsCheck;
        private boolean passedAiCheck;

        public boolean isReal()                    { return isReal; }
        public String  getReason()                 { return reason; }
        public Double  getPhotoGpsLat()            { return photoGpsLat; }
        public Double  getPhotoGpsLng()            { return photoGpsLng; }
        public String  getDeviceModel()            { return deviceModel; }
        public boolean isPassedFileTypeCheck()     { return passedFileTypeCheck; }
        public boolean isPassedMetadataCheck()     { return passedMetadataCheck; }
        public boolean isPassedGpsCheck()          { return passedGpsCheck; }
        public boolean isPassedAiCheck()           { return passedAiCheck; }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private final FakeDetectionResult result = new FakeDetectionResult();
            public Builder isReal(boolean v)             { result.isReal = v;               return this; }
            public Builder reason(String v)              { result.reason = v;               return this; }
            public Builder photoGpsLat(Double v)         { result.photoGpsLat = v;          return this; }
            public Builder photoGpsLng(Double v)         { result.photoGpsLng = v;          return this; }
            public Builder deviceModel(String v)         { result.deviceModel = v;          return this; }
            public Builder passedFileTypeCheck(boolean v){ result.passedFileTypeCheck = v;  return this; }
            public Builder passedMetadataCheck(boolean v){ result.passedMetadataCheck = v;  return this; }
            public Builder passedGpsCheck(boolean v)     { result.passedGpsCheck = v;       return this; }
            public Builder passedAiCheck(boolean v)      { result.passedAiCheck = v;        return this; }
            public FakeDetectionResult build()           { return result; }
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // 🔍 MAIN: 4-Layer Strict Fake Detection
    // ─────────────────────────────────────────────────────────────────
    public FakeDetectionResult detectFake(MultipartFile photo,
                                          double reportedLat,
                                          double reportedLng) {

        log.info("Starting strict fake photo detection for: {}", photo.getOriginalFilename());

        FakeDetectionResult.Builder resultBuilder = FakeDetectionResult.builder();

        // ══ LAYER 1: File Type Check (Tika) ══════════════
        boolean fileTypeValid = checkFileType(photo);
        resultBuilder.passedFileTypeCheck(fileTypeValid);

        if (!fileTypeValid) {
            log.warn("Layer 1 FAILED: Invalid file type");
            return resultBuilder
                    .isReal(false)
                    .reason("Invalid file type. Please upload a real photo (JPG/PNG).")
                    .build();
        }
        log.info("Layer 1 PASSED ✅ Valid image file");

        // ══ LAYER 2: EXIF Metadata — STRICT ══════════════
        // No EXIF = WhatsApp/downloaded photo = REJECT
        MetadataCheckResult metadataResult = checkExifMetadata(photo);
        resultBuilder
                .passedMetadataCheck(metadataResult.hasMetadata)
                .photoGpsLat(metadataResult.gpsLat)
                .photoGpsLng(metadataResult.gpsLng)
                .deviceModel(metadataResult.deviceModel);

        if (!metadataResult.hasMetadata) {
            log.warn("Layer 2 FAILED: No EXIF metadata — likely WhatsApp/downloaded photo");
            return resultBuilder
                    .isReal(false)
                    .reason("Photo does not contain camera metadata (EXIF). "
                            + "This photo may have been shared via WhatsApp or downloaded from the internet. "
                            + "Please open your phone's Camera app, take a fresh photo of the problem, "
                            + "and upload it directly.")
                    .build();
        }
        log.info("Layer 2 PASSED ✅ EXIF found. Device: {}", metadataResult.deviceModel);

        // ══ LAYER 3: GPS Cross-Check ═════════════════════
        // GPS present + mismatch → REJECT
        // GPS missing → SKIP (device model already verified in Layer 2)
        if (metadataResult.gpsLat != null && metadataResult.gpsLng != null) {

            // ✅ 0.0,0.0 = GPS lock failed at capture — treat as no GPS, skip check
            if (metadataResult.gpsLat == 0.0 && metadataResult.gpsLng == 0.0) {
                resultBuilder.passedGpsCheck(true);
                log.info("Layer 3 SKIPPED — GPS is 0,0 (lock failed at capture time)");
            }
            else if (reportedLat == 0.0 && reportedLng == 0.0) {
                // ✅ FIX: /analyze endpoint 0.0,0.0 pass karta hai
                // User ne abhi GPS click nahi kiya — GPS check skip karo
                // Real GPS check final submit pe hoga actual lat/lng ke saath
                resultBuilder.passedGpsCheck(true);
                log.info("Layer 3 SKIPPED — No reported location (analyze-only call)");
            }
            else {

                double distanceKm = calculateDistanceKm(
                        metadataResult.gpsLat, metadataResult.gpsLng,
                        reportedLat, reportedLng
                );
                log.info("GPS distance: {} km (max allowed: {} km)", distanceKm, GPS_MISMATCH_THRESHOLD_KM);

                if (distanceKm > GPS_MISMATCH_THRESHOLD_KM) {
                    log.warn("Layer 3 FAILED: GPS mismatch — {} km apart", String.format("%.2f", distanceKm));
                    resultBuilder.passedGpsCheck(false);
                    return resultBuilder
                            .isReal(false)
                            .reason(String.format(
                                    "Location mismatch! Your photo was taken %.0f meters away "
                                            + "from the reported problem location (max allowed: 500 meters). "
                                            + "Please go to the actual problem site and take a fresh photo.",
                                    distanceKm * 1000))
                            .build();
                }
                resultBuilder.passedGpsCheck(true);
                log.info("Layer 3 PASSED ✅ GPS matches — {} m away", String.format("%.0f", distanceKm * 1000));

            } // end else (GPS not 0,0)
        } else {
            // GPS not in EXIF — skip GPS check, device model was verified in Layer 2
            resultBuilder.passedGpsCheck(true);
            log.info("Layer 3 SKIPPED — No GPS in EXIF, device model verified in Layer 2");
        }

        // ══ LAYER 4: AI Vision Check — STRICT ════════════
        // No benefit of doubt — AI fail = reject
        boolean aiCheckPassed = checkWithAI(photo);
        resultBuilder.passedAiCheck(aiCheckPassed);

        if (!aiCheckPassed) {
            log.warn("Layer 4 FAILED: AI detected photo is not a valid civic complaint photo");
            return resultBuilder
                    .isReal(false)
                    .reason("This photo does not appear to show a civic problem. "
                            + "Please upload a photo of the actual issue — "
                            + "such as a pothole, garbage, broken streetlight, sewage overflow, "
                            + "or any other civic problem at the problem site.")
                    .build();
        }
        log.info("Layer 4 PASSED ✅ AI confirms photo is real");
        log.info("✅ All 4 layers passed — photo verified as REAL");

        return resultBuilder
                .isReal(true)
                .reason("Photo verified as authentic")
                .build();
    }

    // ══ LAYER 1: Apache Tika — File Type ═════════════════
    private boolean checkFileType(MultipartFile photo) {
        try {
            Tika tika = new Tika();
            String mimeType = tika.detect(photo.getInputStream());
            log.info("Tika detected MIME: {}", mimeType);
            return mimeType.equals("image/jpeg")
                    || mimeType.equals("image/jpg")
                    || mimeType.equals("image/png")
                    || mimeType.equals("image/heic")
                    || mimeType.equals("image/heif");
        } catch (Exception e) {
            log.error("Tika check failed: {}", e.getMessage());
            return false;
        }
    }

    // ══ LAYER 2: EXIF Metadata Reader ════════════════════
    private MetadataCheckResult checkExifMetadata(MultipartFile photo) {
        MetadataCheckResult result = new MetadataCheckResult();

        try (InputStream inputStream = photo.getInputStream()) {

            Metadata metadata = ImageMetadataReader.readMetadata(inputStream);

            // Check device make/model
            ExifIFD0Directory exifDir =
                    metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);

            if (exifDir != null) {
                String model = exifDir.getString(ExifIFD0Directory.TAG_MODEL);
                String make  = exifDir.getString(ExifIFD0Directory.TAG_MAKE);
                if (model != null || make != null) {
                    result.hasMetadata = true;
                    result.deviceModel = (make  != null ? make  + " " : "")
                            + (model != null ? model       : "");
                    log.info("Device: {}", result.deviceModel);
                }
            }

            // Check GPS
            GpsDirectory gpsDir =
                    metadata.getFirstDirectoryOfType(GpsDirectory.class);
            if (gpsDir != null && gpsDir.getGeoLocation() != null) {
                result.gpsLat = gpsDir.getGeoLocation().getLatitude();
                result.gpsLng = gpsDir.getGeoLocation().getLongitude();
                log.info("GPS in photo: {}, {}", result.gpsLat, result.gpsLng);
            }

            // ✅ Option 2: hasMetadata = true ONLY if device model OR GPS present
            // WhatsApp images have random EXIF dirs but no model/GPS — those are rejected
            if (!result.hasMetadata && result.gpsLat != null) {
                // Has GPS but no device model — still valid (GPS-only camera)
                result.hasMetadata = true;
                result.deviceModel = "GPS-only Device";
            }
            // If neither device model nor GPS → hasMetadata stays false → Layer 2 FAIL

        } catch (Exception e) {
            log.warn("EXIF read failed: {}", e.getMessage());
            result.hasMetadata = false;
        }

        return result;
    }

    // ══ LAYER 4: AI Vision — STRICT (no benefit of doubt) ═
    private boolean checkWithAI(MultipartFile photo) {
        try {
            byte[] photoBytes = photo.getBytes();
            String base64Image = Base64.getEncoder().encodeToString(photoBytes);
            String dataUrl     = "data:image/jpeg;base64," + base64Image;

            String prompt = "You are a photo verification AI for a civic complaint system in India. "
                    + "Analyze this photo carefully.\n\n"
                    + "Say FAKE if the photo is:\n"
                    + "- AI-generated or digitally manipulated\n"
                    + "- A selfie or shows a person's face\n"
                    + "- Food, indoor furniture, random household objects\n"
                    + "- Screenshot or downloaded from internet\n\n"
                    + "Say REAL if the photo shows:\n"
                    + "- Any outdoor scene (even without obvious civic problem)\n"
                    + "- Road, street, footpath, drain, garbage, light pole, water pipe\n"
                    + "- Any public area, colony, market, building exterior\n"
                    + "- Natural lighting with real camera grain/imperfections\n\n"
                    + "Be LENIENT for outdoor photos — if it looks like a genuine outdoor photo, say REAL.\n"
                    + "Only say FAKE for obvious non-civic content like selfies, food, indoor scenes.\n"
                    + "Respond with ONLY one word: REAL or FAKE";

            // ✅ Groq vision API format — direct HTTP call
            Map<String, Object> requestBody = Map.of(
                    "model", GROQ_MODEL,
                    "max_tokens", 10,
                    "messages", List.of(
                            Map.of(
                                    "role", "user",
                                    "content", List.of(
                                            Map.of("type", "text", "text", prompt),
                                            Map.of("type", "image_url",
                                                    "image_url", Map.of("url", dataUrl))
                                    )
                            )
                    )
            );

            String jsonBody = objectMapper.writeValueAsString(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GROQ_URL))
                    .header("Content-Type",  "application/json")
                    .header("Authorization", "Bearer " + groqApiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Groq AI check error {}: {}", response.statusCode(), response.body());
                return false; // STRICT: API error = reject
            }

            Map<?, ?> responseMap = objectMapper.readValue(response.body(), Map.class);
            List<?> choices = (List<?>) responseMap.get("choices");
            Map<?, ?> message = (Map<?, ?>) ((Map<?, ?>) choices.get(0)).get("message");
            String aiResponse = ((String) message.get("content")).trim().toUpperCase();

            log.info("AI photo check response: {}", aiResponse);
            return aiResponse.contains("REAL") && !aiResponse.contains("FAKE");

        } catch (Exception e) {
            // ✅ STRICT: AI timeout/error = REJECT
            log.error("AI check failed: {} — REJECTING (strict mode)", e.getMessage());
            return false;
        }
    }

    // ══ Haversine Formula ═════════════════════════════════
    private double calculateDistanceKm(double lat1, double lng1,
                                       double lat2, double lng2) {
        final int R = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private static class MetadataCheckResult {
        boolean hasMetadata = false;
        Double  gpsLat      = null;
        Double  gpsLng      = null;
        String  deviceModel = null;
    }
}