package com.samadhanai.samadhanai.Email.Service;

import com.samadhanai.samadhanai.Common.Enums.ComplaintStatus;
import com.samadhanai.samadhanai.Common.Enums.DepartmentType;
import com.samadhanai.samadhanai.Complaint.Model.Complaint;
import com.samadhanai.samadhanai.Email.Dto.EmailStatusDTO;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender         mailSender;
    private final ComplaintLetterBuilder letterBuilder;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${app.upload.dir}")
    private String uploadDir;

    private static final String FROM_NAME   = "SamadhanAI Civic Platform";
    private static final String DATE_FORMAT = "dd MMM yyyy, hh:mm a";
    private static final String SHORT_DATE  = "dd MMM yyyy";

    // ─────────────────────────────────────────────────────────────────
    // 📧 Feature 3: Send Complaint Email to Department
    // ✅ UPDATED: Reply-To header added — department ka reply
    //             seedha hamare Gmail inbox mein aayega
    //             EmailReplyListenerService IMAP se read karega
    // ─────────────────────────────────────────────────────────────────
    public EmailStatusDTO sendComplaintToDepartment(Complaint complaint) {

        log.info("Sending complaint email for: {}", complaint.getReferenceId());

        String deptEmail = getDepartmentEmail(complaint.getAssignedDepartment());

        try {
            ComplaintLetterBuilder.LetterResult letter =
                    letterBuilder.buildComplaintLetter(complaint);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail, FROM_NAME);
            helper.setTo(deptEmail);

            // ✅ KEY CHANGE: Reply-To set karo — same as our Gmail inbox
            // Jab department "Reply" kare → mail aayegi is address pe
            // EmailReplyListenerService IMAP se yahi inbox check karta hai
            message.setReplyTo(new InternetAddress[]{
                    new InternetAddress(fromEmail, FROM_NAME)
            });

            // ✅ Subject mein Reference ID clearly likho
            // EmailReplyListenerService regex se SAI-XXXXXXXX extract karega
            // Department ke reply subject mein bhi ye ID rehti hai ("Re: ...")
            helper.setSubject(letter.getSubject());

            // CC citizen — so they have a copy
            if (complaint.getUserEmail() != null) {
                helper.setCc(complaint.getUserEmail());
            }

            helper.setText(letter.getBodyHtml(), true);

            // ✅ Attach all photos (main + extra)
            attachAllPhotos(helper, complaint,
                    "complaint_" + complaint.getReferenceId());

            mailSender.send(message);
            log.info("✅ Complaint email sent to: {} for: {}",
                    deptEmail, complaint.getReferenceId());

            return EmailStatusDTO.builder()
                    .sent(true)
                    .sentTo(deptEmail)
                    .ccTo(complaint.getUserEmail())
                    .sentAt(LocalDateTime.now())
                    .subject(letter.getSubject())
                    .complaintReferenceId(complaint.getReferenceId())
                    .build();

        } catch (MessagingException e) {
            log.error("❌ Email failed for {}: {} | Full Error: {}",
                    complaint.getReferenceId(), e.getMessage(), e.toString());
            e.printStackTrace();
            return EmailStatusDTO.builder()
                    .sent(false)
                    .sentTo(deptEmail)
                    .failureReason("Email delivery failed: " + e.getMessage())
                    .complaintReferenceId(complaint.getReferenceId())
                    .build();

        } catch (Exception e) {
            log.error("❌ Unexpected error sending email: {}", e.getMessage());
            return EmailStatusDTO.builder()
                    .sent(false)
                    .sentTo(deptEmail)
                    .failureReason("Unexpected error: " + e.getMessage())
                    .complaintReferenceId(complaint.getReferenceId())
                    .build();
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // 📧 Feature 7: Status Update Email to Citizen
    // ✅ NO CHANGE NEEDED — already works correctly
    // ─────────────────────────────────────────────────────────────────
    public void sendStatusUpdateToCitizen(Complaint complaint,
                                          ComplaintStatus oldStatus,
                                          ComplaintStatus newStatus) {

        log.info("Sending status update email: {} → {} for: {}",
                oldStatus, newStatus, complaint.getReferenceId());

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper =
                    new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail, FROM_NAME);
            helper.setTo(complaint.getUserEmail());
            helper.setSubject("📋 Update on Your Complaint "
                    + complaint.getReferenceId()
                    + " — Status: " + newStatus.name());

            helper.setText(
                    buildStatusUpdateEmail(complaint, oldStatus, newStatus), true);

            mailSender.send(message);
            log.info("✅ Status update email sent to: {}",
                    complaint.getUserEmail());

        } catch (Exception e) {
            log.error("Failed to send status update email: {}", e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // 📧 Feature 8: Reminder Email to Department
    // ✅ UPDATED: Reply-To header added here too
    //             Agar department reminder pe reply kare →
    //             woh bhi EmailReplyListenerService catch kar lega
    // ─────────────────────────────────────────────────────────────────
    public EmailStatusDTO sendReminderToDepartment(Complaint complaint) {

        log.info("Sending reminder email for: {}", complaint.getReferenceId());

        String deptEmail = getDepartmentEmail(complaint.getAssignedDepartment());

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper =
                    new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail, FROM_NAME);
            helper.setTo(deptEmail);

            // ✅ Reply-To — reminder pe reply bhi catch ho jaaye
            message.setReplyTo(new InternetAddress[]{
                    new InternetAddress(fromEmail, FROM_NAME)
            });

            if (complaint.getUserEmail() != null) {
                helper.setCc(complaint.getUserEmail());
            }

            helper.setSubject("⚠️ REMINDER: Complaint Pending 15+ Days — "
                    + complaint.getReferenceId());

            helper.setText(buildReminderEmail(complaint), true);

            // ✅ Attach all photos
            attachAllPhotos(helper, complaint,
                    "reminder_" + complaint.getReferenceId());

            mailSender.send(message);
            log.info("✅ Reminder sent to: {} for: {}",
                    deptEmail, complaint.getReferenceId());

            return EmailStatusDTO.builder()
                    .sent(true)
                    .sentTo(deptEmail)
                    .sentAt(LocalDateTime.now())
                    .complaintReferenceId(complaint.getReferenceId())
                    .build();

        } catch (Exception e) {
            log.error("❌ Reminder failed for {}: {}",
                    complaint.getReferenceId(), e.getMessage());
            return EmailStatusDTO.builder()
                    .sent(false)
                    .sentTo(deptEmail)
                    .failureReason(e.getMessage())
                    .complaintReferenceId(complaint.getReferenceId())
                    .build();
        }
    }

    // ─────────────────────────────────────────────────────────────────
// 🎨 Status Update Email — Simple Structured
// ─────────────────────────────────────────────────────────────────
    private String buildStatusUpdateEmail(Complaint complaint,
                                          ComplaintStatus oldStatus,
                                          ComplaintStatus newStatus) {

        String statusEmoji = switch (newStatus) {
            case RESOLVED    -> "✅";
            case IN_PROGRESS -> "🔄";
            case REJECTED    -> "❌";
            default          -> "⏳";
        };

        String statusColor = switch (newStatus) {
            case RESOLVED    -> "#16a34a";
            case IN_PROGRESS -> "#2563eb";
            case REJECTED    -> "#dc2626";
            default          -> "#d97706";
        };

        // How was it resolved
        String resolvedByLine = "";
        if (newStatus == ComplaintStatus.RESOLVED && complaint.getResolvedBy() != null) {
            resolvedByLine = switch (complaint.getResolvedBy()) {
                case ADMIN            -> "<tr><td>Resolved By</td><td>Admin</td></tr>";
                case USER             -> "<tr><td>Resolved By</td><td>You (Citizen confirmed)</td></tr>";
                case DEPARTMENT_EMAIL -> "<tr><td>Resolved By</td><td>Auto-resolved via Department Email Reply</td></tr>";
            };
        }

        // Resolution note
        String noteLine = complaint.getResolutionNote() != null
                ? "<tr><td>Department Note</td><td><em>"
                + complaint.getResolutionNote()
                + "</em></td></tr>"
                : "";

        String dateStr = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern(DATE_FORMAT));

        return """
        <!DOCTYPE html>
        <html>
        <head>
          <meta charset="UTF-8"/>
          <style>
            body { font-family: Arial, sans-serif; font-size: 14px;
                   color: #1e293b; background: #f8fafc; margin: 0; padding: 20px; }
            .container { max-width: 580px; margin: 0 auto;
                         background: #ffffff; border: 1px solid #e2e8f0;
                         border-radius: 8px; padding: 28px; }
            .header { background: #1e40af; color: #fff;
                      padding: 16px 20px; border-radius: 6px;
                      margin-bottom: 24px; }
            .header h2 { margin: 0; font-size: 18px; }
            .header p  { margin: 4px 0 0; font-size: 12px; color: #bfdbfe; }
            .status-box { text-align: center; padding: 20px;
                          border: 2px solid %s; border-radius: 8px;
                          margin-bottom: 20px; }
            .status-box .old { color: #94a3b8; font-size: 13px; }
            .status-box .arrow { color: #94a3b8; margin: 0 8px; font-size: 18px; }
            .status-box .new { color: %s; font-size: 20px; font-weight: bold; }
            .section-title { font-size: 12px; font-weight: bold;
                             color: #64748b; text-transform: uppercase;
                             letter-spacing: 0.05em; margin: 20px 0 8px; }
            table.details { width: 100%%; border-collapse: collapse; }
            table.details td { padding: 8px 10px;
                               border-bottom: 1px solid #f1f5f9;
                               vertical-align: top; font-size: 13px; }
            table.details td:first-child { color: #64748b; font-size: 12px;
                                           font-weight: bold; width: 38%%;
                                           text-transform: uppercase;
                                           letter-spacing: 0.04em; }
            table.details td:last-child { color: #1e293b; font-weight: 600; }
            .footer { margin-top: 24px; padding-top: 16px;
                      border-top: 1px solid #e2e8f0;
                      text-align: center; font-size: 11px; color: #94a3b8; }
          </style>
        </head>
        <body>
        <div class="container">

          <div class="header">
            <h2>🏛️ SamadhanAI — Complaint Status Update</h2>
            <p>Your complaint status has been updated</p>
          </div>

          <p style="margin-bottom:16px;">
            Hello <strong>%s</strong>,
          </p>

          <!-- Status Change -->
          <div class="status-box">
            <span class="old">%s</span>
            <span class="arrow">→</span>
            <span class="new">%s %s</span>
          </div>

          <!-- Complaint Details -->
          <div class="section-title">📋 Complaint Details</div>
          <table class="details">
            <tr><td>Reference ID</td><td>%s</td></tr>
            <tr><td>Problem</td><td>%s</td></tr>
            <tr><td>Location</td><td>%s</td></tr>
            <tr><td>Department</td><td>%s</td></tr>
            <tr><td>Updated On</td><td>%s</td></tr>
            %s
            %s
          </table>

          <div class="footer">
            SamadhanAI · Ref: %s<br/>
            Track your complaint at samadhanai.in/track.html?ref=%s
          </div>

        </div>
        </body>
        </html>
        """.formatted(
                statusColor, statusColor,
                complaint.getUserName(),
                oldStatus.name(),
                statusEmoji, newStatus.name(),
                complaint.getReferenceId(),
                complaint.getComplaintType(),
                complaint.getFullAddress() != null
                        ? complaint.getFullAddress() : complaint.getWard(),
                getDepartmentDisplayName(complaint.getAssignedDepartment()),
                dateStr,
                resolvedByLine,
                noteLine,
                complaint.getReferenceId(),
                complaint.getReferenceId()
        );
    }

    // ─────────────────────────────────────────────────────────────────
// 🎨 Reminder Email — Simple Structured
// ─────────────────────────────────────────────────────────────────
    private String buildReminderEmail(Complaint complaint) {

        long daysPending = java.time.temporal.ChronoUnit.DAYS.between(
                complaint.getCreatedAt(), LocalDateTime.now());

        String dateStr = complaint.getCreatedAt()
                .format(DateTimeFormatter.ofPattern(SHORT_DATE));

        return """
        <!DOCTYPE html>
        <html>
        <head>
          <meta charset="UTF-8"/>
          <style>
            body { font-family: Arial, sans-serif; font-size: 14px;
                   color: #1e293b; background: #f8fafc; margin: 0; padding: 20px; }
            .container { max-width: 600px; margin: 0 auto;
                         background: #ffffff; border: 1px solid #e2e8f0;
                         border-radius: 8px; padding: 28px; }
            .header { background: #dc2626; color: #fff;
                      padding: 16px 20px; border-radius: 6px;
                      margin-bottom: 24px; }
            .header h2 { margin: 0; font-size: 18px; }
            .header p  { margin: 4px 0 0; font-size: 12px; color: #fca5a5; }
            .warn-box { background: #fef2f2; border: 1px solid #dc2626;
                        border-radius: 6px; padding: 14px 16px;
                        margin-bottom: 20px; }
            .warn-box p { margin: 0; font-size: 15px;
                          font-weight: bold; color: #dc2626; }
            .warn-box small { font-size: 12px; color: #b91c1c; }
            .section-title { font-size: 12px; font-weight: bold;
                             color: #64748b; text-transform: uppercase;
                             letter-spacing: 0.05em; margin: 20px 0 8px; }
            table.details { width: 100%%; border-collapse: collapse; }
            table.details td { padding: 8px 10px;
                               border-bottom: 1px solid #f1f5f9;
                               vertical-align: top; font-size: 13px; }
            table.details td:first-child { color: #64748b; font-size: 12px;
                                           font-weight: bold; width: 38%%;
                                           text-transform: uppercase;
                                           letter-spacing: 0.04em; }
            table.details td:last-child { color: #1e293b; font-weight: 600; }
            .reply-box { background: #fffbeb; border: 1px solid #fde68a;
                         border-radius: 6px; padding: 12px 16px;
                         margin-top: 20px; font-size: 13px; color: #92400e; }
            .footer { margin-top: 24px; padding-top: 16px;
                      border-top: 1px solid #e2e8f0;
                      text-align: center; font-size: 11px; color: #94a3b8; }
          </style>
        </head>
        <body>
        <div class="container">

          <div class="header">
            <h2>⚠️ SamadhanAI — Action Required</h2>
            <p>Civic complaint pending for %d days — no action taken</p>
          </div>

          <!-- Warning -->
          <div class="warn-box">
            <p>This complaint has NOT been resolved for <strong>%d days</strong>.</p>
            <small>Immediate action is required by your department.</small>
          </div>

          <!-- Complaint Details -->
          <div class="section-title">📋 Complaint Details</div>
          <table class="details">
            <tr><td>Reference ID</td><td style="color:#dc2626;font-weight:bold;">%s</td></tr>
            <tr><td>Problem Type</td><td>%s</td></tr>
            <tr><td>Location</td><td>%s</td></tr>
            <tr><td>Ward</td><td>%s</td></tr>
            <tr><td>Pincode</td><td>%s</td></tr>
            <tr><td>Submitted On</td><td>%s</td></tr>
            <tr><td>Days Pending</td><td style="color:#dc2626;font-weight:bold;">%d days</td></tr>
          </table>

          <!-- Citizen Details -->
          <div class="section-title">👤 Citizen Details</div>
          <table class="details">
            <tr><td>Name</td><td>%s</td></tr>
            <tr><td>Email</td><td>%s</td></tr>
            <tr><td>Phone</td><td>%s</td></tr>
          </table>

          <!-- Reply Hint -->
          <div class="reply-box">
            💡 <strong>To auto-resolve this complaint:</strong><br/>
            Simply reply to this email mentioning that the work is completed
            (e.g. "Work completed", "Issue resolved", "Kaam ho gaya").<br/>
            Our system will automatically update the complaint status.
          </div>

          <div class="footer">
            SamadhanAI · Ref: %s<br/>
            This is an automated reminder. Photos are attached for reference.
          </div>

        </div>
        </body>
        </html>
        """.formatted(
                daysPending, daysPending,
                complaint.getReferenceId(),
                complaint.getComplaintType(),
                complaint.getFullAddress() != null ? complaint.getFullAddress() : "N/A",
                complaint.getWard()        != null ? complaint.getWard()        : "N/A",
                complaint.getPincode()     != null ? complaint.getPincode()     : "N/A",
                dateStr,
                daysPending,
                complaint.getUserName(),
                complaint.getUserEmail(),
                complaint.getUserPhone()   != null ? complaint.getUserPhone()   : "N/A",
                complaint.getReferenceId()
        );
    }

    // ─────────────────────────────────────────────────────────────────
    // 🔧 Attach all photos (main + extra)
    // ─────────────────────────────────────────────────────────────────
    private void attachAllPhotos(MimeMessageHelper helper,
                                 Complaint complaint,
                                 String prefix)
            throws MessagingException {

        int photoNum = 1;

        // Main photo
        if (complaint.getPhotoPath() != null) {
            File f = Paths.get(uploadDir, complaint.getPhotoPath()).toFile();
            if (f.exists()) {
                helper.addAttachment(
                        prefix + "_photo_" + photoNum + ".jpg",
                        new FileSystemResource(f));
                log.info("✅ Attached photo {}: {}", photoNum, f.getName());
                photoNum++;
            } else {
                log.warn("⚠️ Main photo not found: {}", f.getAbsolutePath());
            }
        }

        // Extra photos
        if (complaint.getExtraPhotoPaths() != null
                && !complaint.getExtraPhotoPaths().isBlank()) {
            for (String path : complaint.getExtraPhotoPaths().split(",")) {
                if (path.isBlank()) continue;
                File f = Paths.get(uploadDir, path.trim()).toFile();
                if (f.exists()) {
                    helper.addAttachment(
                            prefix + "_photo_" + photoNum + ".jpg",
                            new FileSystemResource(f));
                    log.info("✅ Attached extra photo {}: {}", photoNum, f.getName());
                    photoNum++;
                } else {
                    log.warn("⚠️ Extra photo not found: {}", f.getAbsolutePath());
                }
            }
        }
        log.info("Total {} photo(s) attached", photoNum - 1);
}

    // ─────────────────────────────────────────────────────────────────
    // 🔧 Department Helpers
    // ─────────────────────────────────────────────────────────────────
    private String getDepartmentEmail(DepartmentType dept) {
//        if (dept == null) return "mc.indore@mp.gov.in";
        if (dept == null) return "krishnamittal969145@gmail.com";
        return switch (dept) {
            case PWD                   -> "krishnamittal969145@gmail.com";
            case MUNICIPAL_CORPORATION -> "krishnamittal969145@gmail.com";
            case ELECTRICITY_BOARD     -> "krishnamittal969145@gmail.com";
            case WATER_SUPPLY          -> "krishnamittal969145@gmail.com";
            case SANITATION            -> "krishnamittal969145@gmail.com";
        };
    }

//    return switch (dept) {
//        case PWD                   -> "pwd.indore@mp.gov.in";
//        case MUNICIPAL_CORPORATION -> "mc.indore@mp.gov.in";
//        case ELECTRICITY_BOARD     -> "mpcz.indore@mp.gov.in";
//        case WATER_SUPPLY          -> "phed.indore@mp.gov.in";
//        case SANITATION            -> "sanitation.indore@mp.gov.in";
//    };

    private String getDepartmentDisplayName(DepartmentType dept) {
        if (dept == null) return "Municipal Corporation";
        return switch (dept) {
            case PWD                   -> "Public Works Department";
            case MUNICIPAL_CORPORATION -> "Municipal Corporation";
            case ELECTRICITY_BOARD     -> "Electricity Board";
            case WATER_SUPPLY          -> "Water Supply Department";
            case SANITATION            -> "Sanitation Department";
        };
    }
}