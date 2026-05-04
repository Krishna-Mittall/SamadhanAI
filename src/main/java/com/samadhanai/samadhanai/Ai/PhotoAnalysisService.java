package com.samadhanai.samadhanai.Ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samadhanai.samadhanai.Common.Enums.ComplaintType;
import com.samadhanai.samadhanai.Common.Enums.DepartmentType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class PhotoAnalysisService {

    @Value("${spring.ai.openai.api-key}")
    private String groqApiKey;

    private static final String GROQ_URL   = "https://api.groq.com/openai/v1/chat/completions";
    private static final String GROQ_MODEL = "meta-llama/llama-4-scout-17b-16e-instruct";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient   httpClient   = HttpClient.newHttpClient();

    // ─────────────────────────────────────────────────────────────────
    // 📦 Result Object
    // ─────────────────────────────────────────────────────────────────
    public static class PhotoAnalysisResult {

        private String        title;
        private String        description;
        private ComplaintType  complaintType;
        private DepartmentType department;
        private Integer        confidenceScore;
        private boolean        analysisSuccess;
        private String         errorMessage;

        public String        getTitle()           { return title; }
        public String        getDescription()     { return description; }
        public ComplaintType  getComplaintType()  { return complaintType; }
        public DepartmentType getDepartment()     { return department; }
        public Integer        getConfidenceScore(){ return confidenceScore; }
        public boolean        isAnalysisSuccess() { return analysisSuccess; }
        public String         getErrorMessage()   { return errorMessage; }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private final PhotoAnalysisResult r = new PhotoAnalysisResult();
            public Builder title(String v)               { r.title = v;           return this; }
            public Builder description(String v)         { r.description = v;     return this; }
            public Builder complaintType(ComplaintType v){ r.complaintType = v;   return this; }
            public Builder department(DepartmentType v)  { r.department = v;      return this; }
            public Builder confidenceScore(Integer v)    { r.confidenceScore = v; return this; }
            public Builder analysisSuccess(boolean v)    { r.analysisSuccess = v; return this; }
            public Builder errorMessage(String v)        { r.errorMessage = v;    return this; }
            public PhotoAnalysisResult build()           { return r; }
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // 🤖 MAIN: Analyze Photo using Groq Vision API (direct HTTP call)
    // ─────────────────────────────────────────────────────────────────
    public PhotoAnalysisResult analyzePhoto(MultipartFile photo, String userDescription) {
        log.info("Starting AI photo analysis...");
        try {
            String base64Image = Base64.getEncoder().encodeToString(photo.getBytes());
            String mimeType    = photo.getContentType() != null ? photo.getContentType() : "image/jpeg";
            String dataUrl     = "data:" + mimeType + ";base64," + base64Image;

            String prompt = buildAnalysisPrompt(userDescription);

            // ✅ Groq vision API format — content is array (text + image_url)
            Map<String, Object> requestBody = Map.of(
                    "model", GROQ_MODEL,
                    "max_tokens", 500,
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

            log.info("Groq API response status: {}", response.statusCode());

            if (response.statusCode() != 200) {
                log.error("Groq API error: {}", response.body());
                return getFallbackResult(userDescription);
            }

            // Parse response
            Map<?, ?> responseMap = objectMapper.readValue(response.body(), Map.class);
            List<?> choices = (List<?>) responseMap.get("choices");
            Map<?, ?> message = (Map<?, ?>) ((Map<?, ?>) choices.get(0)).get("message");
            String aiResponse = (String) message.get("content");

            log.info("Groq AI response received, parsing...");
            return parseAiResponse(aiResponse);

        } catch (Exception e) {
            log.error("AI analysis failed: {}", e.getMessage());
            return getFallbackResult(userDescription);
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // 📝 Build Prompt
    // ─────────────────────────────────────────────────────────────────
    private String buildAnalysisPrompt(String userDescription) {
        return """
                You are an AI assistant for a civic complaint system in India called SamadhanAI.

                A citizen has uploaded a photo of a civic problem and described it as:
                "%s"

                Analyze the photo carefully and respond in EXACTLY this format (no extra text):

                TITLE: [Short 10-word title of the problem in English]
                DESCRIPTION: [2-3 sentence formal description suitable for a government complaint letter]
                COMPLAINT_TYPE: [ONE of the types listed below]
                DEPARTMENT: [ONE of: PWD, MUNICIPAL_CORPORATION, ELECTRICITY_BOARD, WATER_SUPPLY, SANITATION]
                CONFIDENCE: [Number between 60 and 100 representing your confidence]

                Available COMPLAINT_TYPE values:
                POTHOLE, BROKEN_ROAD, WATERLOGGING,
                WATER_LEAK, NO_WATER_SUPPLY, DIRTY_WATER_SUPPLY,
                OPEN_MANHOLE, SEWAGE_OVERFLOW, BLOCKED_DRAIN,
                GARBAGE, GARBAGE_DUMP, DEAD_ANIMAL,
                BROKEN_STREETLIGHT, POWER_OUTAGE, FALLEN_ELECTRIC_WIRE,
                BROKEN_FOOTPATH, ENCROACHMENT, STRAY_ANIMALS,
                FALLEN_TREE, PARK_MAINTENANCE, OTHER

                Department mapping:
                - POTHOLE, BROKEN_ROAD, WATERLOGGING, BROKEN_FOOTPATH → PWD
                - GARBAGE, GARBAGE_DUMP, DEAD_ANIMAL, ENCROACHMENT, STRAY_ANIMALS, FALLEN_TREE, PARK_MAINTENANCE, OTHER → MUNICIPAL_CORPORATION
                - BROKEN_STREETLIGHT, POWER_OUTAGE, FALLEN_ELECTRIC_WIRE → ELECTRICITY_BOARD
                - WATER_LEAK, NO_WATER_SUPPLY, DIRTY_WATER_SUPPLY → WATER_SUPPLY
                - OPEN_MANHOLE, SEWAGE_OVERFLOW, BLOCKED_DRAIN → SANITATION

                Respond ONLY in the above format. No extra explanation.
                """.formatted(userDescription != null ? userDescription : "");
    }

    // ─────────────────────────────────────────────────────────────────
    // 🔍 Parse AI Response
    // ─────────────────────────────────────────────────────────────────
    private PhotoAnalysisResult parseAiResponse(String aiResponse) {
        log.info("Parsing AI response: {}", aiResponse);
        try {
            String title           = extractField(aiResponse, "TITLE:");
            String description     = extractField(aiResponse, "DESCRIPTION:");
            String complaintTypeStr = extractField(aiResponse, "COMPLAINT_TYPE:");
            String departmentStr   = extractField(aiResponse, "DEPARTMENT:");
            String confidenceStr   = extractField(aiResponse, "CONFIDENCE:");

            ComplaintType  complaintType = parseComplaintType(complaintTypeStr);
            DepartmentType department    = parseDepartmentType(departmentStr);
            int            confidence    = parseConfidence(confidenceStr);

            log.info("Parsed → Type: {}, Dept: {}, Confidence: {}%", complaintType, department, confidence);

            return PhotoAnalysisResult.builder()
                    .title(title)
                    .description(description)
                    .complaintType(complaintType)
                    .department(department)
                    .confidenceScore(confidence)
                    .analysisSuccess(true)
                    .build();

        } catch (Exception e) {
            log.error("Failed to parse AI response: {}", e.getMessage());
            return getFallbackResult("Unknown problem detected");
        }
    }

    private String extractField(String response, String fieldName) {
        for (String line : response.split("\n")) {
            if (line.trim().startsWith(fieldName)) {
                return line.substring(line.indexOf(fieldName) + fieldName.length()).trim();
            }
        }
        return "Unknown";
    }

    private ComplaintType parseComplaintType(String value) {
        try { return ComplaintType.valueOf(value.trim().toUpperCase()); }
        catch (Exception e) { log.warn("Unknown complaint type: {} → OTHER", value); return ComplaintType.OTHER; }
    }

    private DepartmentType parseDepartmentType(String value) {
        try { return DepartmentType.valueOf(value.trim().toUpperCase()); }
        catch (Exception e) { log.warn("Unknown department: {} → MUNICIPAL", value); return DepartmentType.MUNICIPAL_CORPORATION; }
    }

    private int parseConfidence(String value) {
        try { return Math.max(0, Math.min(100, Integer.parseInt(value.trim()))); }
        catch (Exception e) { return 75; }
    }

    // ─────────────────────────────────────────────────────────────────
    // 🆘 Fallback
    // ─────────────────────────────────────────────────────────────────
    private PhotoAnalysisResult getFallbackResult(String userDescription) {
        log.warn("Using fallback result due to AI failure");
        return PhotoAnalysisResult.builder()
                .title("Civic Problem Reported: " + userDescription)
                .description("A civic problem has been reported. Manual review required. Description: " + userDescription)
                .complaintType(ComplaintType.OTHER)
                .department(DepartmentType.MUNICIPAL_CORPORATION)
                .confidenceScore(50)
                .analysisSuccess(false)
                .errorMessage("AI analysis unavailable. Manual review needed.")
                .build();
    }
}