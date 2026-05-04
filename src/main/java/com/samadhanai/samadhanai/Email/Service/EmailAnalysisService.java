package com.samadhanai.samadhanai.Email.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class EmailAnalysisService {

    @Value("${spring.ai.openai.api-key}")
    private String groqApiKey;

    private static final String GROQ_URL   = "https://api.groq.com/openai/v1/chat/completions";
    private static final String GROQ_MODEL = "llama-3.3-70b-versatile"; // text only, no vision needed

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient   httpClient   = HttpClient.newHttpClient();

    // ─────────────────────────────────────────────────────────────────
    // 🤖 MAIN: Analyze department email reply
    // Returns true if email confirms problem is RESOLVED
    // ─────────────────────────────────────────────────────────────────
    public boolean isResolvedConfirmation(String emailSubject, String emailBody) {

        log.info("Analyzing department email reply for resolution confirmation...");

        // Safety check — empty email
        if ((emailBody == null || emailBody.isBlank()) &&
                (emailSubject == null || emailSubject.isBlank())) {
            log.warn("Email body and subject both empty — cannot analyze");
            return false;
        }

        try {
            String combinedText = buildEmailText(emailSubject, emailBody);
            String prompt       = buildPrompt(combinedText);

            Map<String, Object> requestBody = Map.of(
                    "model",      GROQ_MODEL,
                    "max_tokens", 10,
                    "temperature", 0,  // deterministic response
                    "messages", List.of(
                            Map.of(
                                    "role",    "user",
                                    "content", prompt
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

            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Groq API error {}: {}", response.statusCode(), response.body());
                return false; // Safe default — don't auto-resolve on error
            }

            // Parse response
            Map<?, ?> responseMap = objectMapper.readValue(response.body(), Map.class);
            List<?>   choices     = (List<?>) responseMap.get("choices");
            Map<?, ?>  message    = (Map<?, ?>) ((Map<?, ?>) choices.get(0)).get("message");
            String     aiReply    = ((String) message.get("content")).trim().toUpperCase();

            log.info("Email analysis result: '{}'", aiReply);

            // RESOLVED only if AI clearly says RESOLVED
            boolean isResolved = aiReply.contains("RESOLVED") && !aiReply.contains("NOT_RESOLVED");

            if (isResolved) {
                log.info("✅ AI confirmed: Department email indicates RESOLVED");
            } else {
                log.info("❌ AI says: NOT RESOLVED or unclear — no action");
            }

            return isResolved;

        } catch (Exception e) {
            // Safe default — don't auto-resolve on exception
            log.error("Email analysis failed: {} — defaulting to NOT RESOLVED", e.getMessage());
            return false;
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // 📝 Build prompt
    // ─────────────────────────────────────────────────────────────────
    private String buildPrompt(String emailText) {
        return """
            You are an AI assistant for SamadhanAI — an Indian civic complaint system.
            
            A government department has replied to a civic complaint email.
            Analyze the reply email text below and determine if the department
            is CONFIRMING that the problem has been RESOLVED/FIXED.
            
            Signs of RESOLVED:
            - "work completed", "issue fixed", "problem resolved", "kaam ho gaya",
              "samasya hal ho gayi", "action taken", "repair done", "cleaned", "restored",
              "attended to", "addressed", "solved", "rectified", "completed work"
            
            Signs of NOT RESOLVED:
            - Asking for more time, escalating, acknowledging receipt only,
              asking for more details, out of office replies, auto-replies,
              "will look into it", "under review", "noted", "received"
            
            Be STRICT. Only say RESOLVED if you are clearly sure the problem is fixed.
            When in doubt say NOT_RESOLVED.
            
            Email text:
            ---
            %s
            ---
            
            Respond with ONLY one word: RESOLVED or NOT_RESOLVED
            """.formatted(emailText);
    }

    // ─────────────────────────────────────────────────────────────────
    // 🔧 Combine subject + body for analysis
    // ─────────────────────────────────────────────────────────────────
    private String buildEmailText(String subject, String body) {
        StringBuilder sb = new StringBuilder();

        if (subject != null && !subject.isBlank()) {
            sb.append("Subject: ").append(subject).append("\n\n");
        }
        if (body != null && !body.isBlank()) {
            // Limit to 2000 chars — enough for AI to analyze
            String trimmedBody = body.length() > 2000
                    ? body.substring(0, 2000) + "..."
                    : body;
            sb.append("Body:\n").append(trimmedBody);
        }

        return sb.toString();
    }
}