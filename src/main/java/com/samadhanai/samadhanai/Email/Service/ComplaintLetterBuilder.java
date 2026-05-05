package com.samadhanai.samadhanai.Email.Service;

import com.samadhanai.samadhanai.Common.Enums.DepartmentType;
import com.samadhanai.samadhanai.Complaint.Model.Complaint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Slf4j
public class ComplaintLetterBuilder {
    private final ChatClient chatClient;

    public static class LetterResult {
        private String subject;
        private String bodyText;
        private String bodyHtml;

        public String getSubject()  { return subject; }
        public String getBodyText() { return bodyText; }
        public String getBodyHtml() { return bodyHtml; }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private final LetterResult r = new LetterResult();
            public Builder subject(String s)  { r.subject = s;  return this; }
            public Builder bodyText(String t) { r.bodyText = t; return this; }
            public Builder bodyHtml(String h) { r.bodyHtml = h; return this; }
            public LetterResult build()       { return r; }
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // 🤖 MAIN: Build complaint letter using AI
    // ─────────────────────────────────────────────────────────────────

    public LetterResult buildComplaintLetter(Complaint complaint) {

        log.info("Building complaint letter for: {}", complaint.getReferenceId());

        // ── Step 1: AI generates the formal letter body ───
        String aiLetterBody;
        try {
            aiLetterBody = chatClient.prompt()
                    .user(buildLetterPrompt(complaint))
                    .call()
                    .content();
            log.info("AI letter generated for: {}", complaint.getReferenceId());
        } catch (Exception e) {
            log.error("AI letter generation failed: {} — using fallback", e.getMessage());
            aiLetterBody = buildFallbackLetterBody(complaint);
        }

        // ── Step 2: Build subject ─────────────────────────
        String subject = buildSubject(complaint);

        // ── Step 3: Build structured HTML ─────────────────
        String htmlBody = buildStructuredHtml(complaint, aiLetterBody);

        return LetterResult.builder()
                .subject(subject)
                .bodyText(aiLetterBody)
                .bodyHtml(htmlBody)
                .build();
    }

    // ─────────────────────────────────────────────────────────────────
    // 📝 Subject line — ref ID clearly in subject
    //    so department reply subject mein bhi rehta hai ("Re: ...")
    //    EmailReplyListenerService extract kar leta hai
    // ─────────────────────────────────────────────────────────────────
    private String buildSubject(Complaint complaint) {
        return String.format(
                "Civic Complaint [%s] — %s at %s",
                complaint.getReferenceId(),
                complaint.getComplaintType(),
                complaint.getWard() != null ? complaint.getWard() : complaint.getCity()
        );
    }

    // ─────────────────────────────────────────────────────────────────
    // 📝 AI Prompt — formal complaint letter
    // ─────────────────────────────────────────────────────────────────
    private String buildLetterPrompt(Complaint complaint) {

        String dateStr  = complaint.getCreatedAt()
                .format(DateTimeFormatter.ofPattern("dd MMMM yyyy"));
        String deptName = getDepartmentFullName(complaint.getAssignedDepartment());

        return """
            Write a formal civic complaint letter in India for government submission.
            
            Details:
            - Reference ID   : %s
            - Problem Type   : %s
            - Location       : %s
            - Ward           : %s
            - Citizen Name   : %s
            - Citizen Email  : %s
            - Date           : %s
            - Department     : %s
            - Description    : %s
            
            Rules:
            1. Start with "To," and department name
            2. Use "Respected Sir/Madam,"
            3. Clearly describe the problem and location
            4. Mention Reference ID: %s
            5. Request action within 7 working days
            6. Mention that photos are attached
            7. End with "Yours faithfully," and citizen name
            8. Keep it under 250 words
            9. Professional tone — mix of English and respectful Hindi phrases is fine
            10. Do NOT include any extra commentary — just the letter
            """.formatted(
                complaint.getReferenceId(),
                complaint.getComplaintType(),
                complaint.getFullAddress(),
                complaint.getWard(),
                complaint.getUserName(),
                complaint.getUserEmail(),
                dateStr,
                deptName,
                complaint.getUserDescription(),
                complaint.getReferenceId()
        );
    }

    // ─────────────────────────────────────────────────────────────────
    // 🎨 Simple Structured HTML Email
    //    No gradients, no shadows — clean table layout
    //    All important details clearly visible
    // ─────────────────────────────────────────────────────────────────
    private String buildStructuredHtml(Complaint complaint, String letterBody) {

        String dateStr = complaint.getCreatedAt()
                .format(DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a"));

        String deptName = getDepartmentFullName(complaint.getAssignedDepartment());

        // Photo verification line
        boolean photoVerified = Boolean.TRUE.equals(complaint.getPhotoVerified());
        String photoVerifLine = photoVerified
                ? "✅ Verified (EXIF + GPS + AI checked)"
                : "⚠️ Not fully verified (no EXIF metadata)";

        // AI confidence
        String confidence = complaint.getAiConfidenceScore() != null
                ? complaint.getAiConfidenceScore() + "%"
                : "N/A";

        // Letter body — convert newlines to <br>
        String htmlLetter = letterBody
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\n", "<br/>");

        return """
            <!DOCTYPE html>
            <html>
            <head>
              <meta charset="UTF-8"/>
              <style>
                body { font-family: Arial, sans-serif; font-size: 14px;
                       color: #1e293b; background: #f8fafc; margin: 0; padding: 20px; }
                .container { max-width: 640px; margin: 0 auto;
                             background: #ffffff; border: 1px solid #e2e8f0;
                             border-radius: 8px; padding: 28px; }
                .header { background: #1e40af; color: #ffffff;
                          padding: 16px 20px; border-radius: 6px;
                          margin-bottom: 24px; }
                .header h2 { margin: 0; font-size: 18px; }
                .header p  { margin: 4px 0 0; font-size: 12px;
                             color: #bfdbfe; }
                .ref-box { background: #eff6ff; border: 1px solid #bfdbfe;
                           border-radius: 6px; padding: 12px 16px;
                           margin-bottom: 20px; }
                .ref-box .ref-id { font-size: 20px; font-weight: bold;
                                   color: #1e40af; letter-spacing: 1px; }
                .ref-box .ref-lbl { font-size: 11px; color: #64748b;
                                    text-transform: uppercase;
                                    letter-spacing: 0.05em; }
                .section-title { font-size: 12px; font-weight: bold;
                                 color: #64748b; text-transform: uppercase;
                                 letter-spacing: 0.05em; margin: 20px 0 8px; }
                table.details { width: 100%%; border-collapse: collapse; }
                table.details td { padding: 8px 10px;
                                   border-bottom: 1px solid #f1f5f9;
                                   vertical-align: top; }
                table.details td:first-child { color: #64748b; font-size: 12px;
                                               font-weight: bold; width: 38%%;
                                               text-transform: uppercase;
                                               letter-spacing: 0.04em; }
                table.details td:last-child  { color: #1e293b; font-size: 13px;
                                               font-weight: 600; }
                .letter-box { background: #f8fafc; border: 1px solid #e2e8f0;
                              border-radius: 6px; padding: 16px 20px;
                              margin: 16px 0; line-height: 1.8;
                              font-size: 13px; color: #374151; }
                .action-box { background: #fff7ed; border: 1px solid #f97316;
                              border-radius: 6px; padding: 12px 16px;
                              margin-top: 20px; }
                .action-box p { margin: 0; font-size: 13px;
                                color: #c2410c; font-weight: bold; }
                .action-box small { color: #9a3412; font-size: 12px; }
                .footer { margin-top: 24px; padding-top: 16px;
                          border-top: 1px solid #e2e8f0;
                          text-align: center; font-size: 11px; color: #94a3b8; }
              </style>
            </head>
            <body>
            <div class="container">

              <!-- Header -->
              <div class="header">
                <h2>🏛️ SamadhanAI — Civic Complaint</h2>
                <p>AI-Powered Civic Complaint Management System · India</p>
              </div>

              <!-- Reference ID Box -->
              <div class="ref-box">
                <div class="ref-lbl">Reference ID</div>
                <div class="ref-id">%s</div>
              </div>

              <!-- Complaint Details -->
              <div class="section-title">📋 Complaint Details</div>
              <table class="details">
                <tr>
                  <td>Problem Type</td>
                  <td>%s</td>
                </tr>
                <tr>
                  <td>Department</td>
                  <td>%s</td>
                </tr>
                <tr>
                  <td>Location</td>
                  <td>%s</td>
                </tr>
                <tr>
                  <td>Ward / Area</td>
                  <td>%s</td>
                </tr>
                <tr>
                  <td>City / State</td>
                  <td>%s, %s</td>
                </tr>
                <tr>
                  <td>Pincode</td>
                  <td>%s</td>
                </tr>
                <tr>
                  <td>Filed On</td>
                  <td>%s</td>
                </tr>
                <tr>
                  <td>Coordinates</td>
                  <td>%s, %s</td>
                </tr>
                <tr>
                  <td>Google Maps</td>
                  <td><a href="https://maps.google.com/?q=%s,%s" style="color:#2563eb;">
                  View on Map →</a></td>
                </tr>
                <tr>
                  <td>AI Confidence</td>
                  <td>%s</td>
                </tr>
                <tr>
                  <td>Photo Verified</td>
                  <td>%s</td>
                </tr>
              </table>

              <!-- Citizen Details -->
              <div class="section-title">👤 Citizen Details</div>
              <table class="details">
                <tr>
                  <td>Name</td>
                  <td>%s</td>
                </tr>
                <tr>
                  <td>Email</td>
                  <td>%s</td>
                </tr>
                <tr>
                  <td>Phone</td>
                  <td>%s</td>
                </tr>
                <tr>
                  <td>Description</td>
                  <td>%s</td>
                </tr>
              </table>

              <!-- Formal Letter -->
              <div class="section-title">📄 Formal Complaint Letter</div>
              <div class="letter-box">%s</div>

              <!-- Action Required -->
              <div class="action-box">
                <p>⏰ Please take action within 7 working days.</p>
                <small>
                  Photos are attached to this email.<br/>
                  Citizen has been CC'd and will be notified of status updates.<br/>
                  <strong>Reply to this email to confirm resolution —
                  our system will automatically update the complaint status.</strong>
                </small>
              </div>

              <!-- Footer -->
              <div class="footer">
                SamadhanAI · AI-Powered Civic Complaint Platform · India<br/>
                samadhanai.complaints@gmail.com · Ref: %s
              </div>

            </div>
            </body>
            </html>
            """.formatted(
                complaint.getReferenceId(),
                complaint.getComplaintType(),
                deptName,
                complaint.getFullAddress() != null ? complaint.getFullAddress() : "N/A",
                complaint.getWard()        != null ? complaint.getWard()        : "N/A",
                complaint.getCity()        != null ? complaint.getCity()        : "N/A",
                complaint.getState()       != null ? complaint.getState()       : "N/A",
                complaint.getPincode()     != null ? complaint.getPincode()     : "N/A",
                dateStr,
                // Coordinates + Google Maps link
                complaint.getLatitude()  != null ? complaint.getLatitude().toString()  : "N/A",
                complaint.getLongitude() != null ? complaint.getLongitude().toString() : "N/A",
                complaint.getLatitude()  != null ? complaint.getLatitude().toString()  : "0",
                complaint.getLongitude() != null ? complaint.getLongitude().toString() : "0",
                confidence,
                photoVerifLine,
                complaint.getUserName(),
                complaint.getUserEmail(),
                complaint.getUserPhone()   != null ? complaint.getUserPhone()   : "N/A",
                complaint.getUserDescription() != null
                        ? complaint.getUserDescription() : "N/A",
                htmlLetter,
                complaint.getReferenceId()
        );
    }

    // ─────────────────────────────────────────────────────────────────
    // 🆘 Fallback letter body — if AI fails
    // ─────────────────────────────────────────────────────────────────
    private String buildFallbackLetterBody(Complaint complaint) {

        String deptName = getDepartmentFullName(complaint.getAssignedDepartment());
        String dateStr  = complaint.getCreatedAt()
                .format(DateTimeFormatter.ofPattern("dd MMMM yyyy"));

        return """
            To,
            The %s

            Subject: Civic Complaint regarding %s at %s [Ref: %s]

            Respected Sir/Madam,

            I, %s, wish to bring to your kind attention a civic issue
            that requires immediate action from your department.

            Problem     : %s
            Location    : %s
            Ward        : %s
            Date        : %s
            Reference ID: %s

            Description:
            %s

            Photos of the problem have been attached to this email for your reference.

            I request you to kindly take necessary action within 7 working days
            and update the complaint status on the SamadhanAI portal.

            Yours faithfully,
            %s
            %s
            %s
            """.formatted(
                deptName,
                complaint.getComplaintType(),
                complaint.getWard(),
                complaint.getReferenceId(),
                complaint.getUserName(),
                complaint.getComplaintType(),
                complaint.getFullAddress(),
                complaint.getWard(),
                dateStr,
                complaint.getReferenceId(),
                complaint.getUserDescription(),
                complaint.getUserName(),
                complaint.getUserEmail(),
                complaint.getUserPhone() != null ? complaint.getUserPhone() : ""
        );
    }

    // ─────────────────────────────────────────────────────────────────
    // 🔧 Helper
    // ─────────────────────────────────────────────────────────────────
    private String getDepartmentFullName(DepartmentType dept) {
        if (dept == null) return "Municipal Corporation";
        return switch (dept) {
            case PWD                   -> "Public Works Department (PWD)";
            case MUNICIPAL_CORPORATION -> "Municipal Corporation";
            case ELECTRICITY_BOARD     -> "Madhya Pradesh Central Zone Electricity";
            case WATER_SUPPLY          -> "Public Health Engineering Department (PHED)";
            case SANITATION            -> "Sanitation and Drainage Department";
        };
    }
}