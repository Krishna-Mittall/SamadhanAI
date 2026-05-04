package com.samadhanai.samadhanai.Email.Dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class EmailStatusDTO {
    // ─────────────────────────────────────────
    // 📧 Email Result
    // ─────────────────────────────────────────

    // Was email sent successfully?
    private boolean sent;

    // Which email address it was sent to
    // e.g. "pwd.indore@mp.gov.in"
    private String sentTo;

    // CC to user's own email so they have a record
    private String ccTo;

    // When was it sent
    private LocalDateTime sentAt;

    // Subject line used
    private String subject;

    // ─────────────────────────────────────────
    // ❌ If Failed
    // ─────────────────────────────────────────

    // Reason if email failed
    private String failureReason;

    // ─────────────────────────────────────────
    // 📋 Reference
    // ─────────────────────────────────────────

    // Complaint reference ID this email belongs to
    private String complaintReferenceId;
}
