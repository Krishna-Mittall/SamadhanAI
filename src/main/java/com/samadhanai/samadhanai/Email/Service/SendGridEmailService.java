package com.samadhanai.samadhanai.Email.Service;

import com.samadhanai.samadhanai.Common.Enums.ComplaintStatus;
import com.samadhanai.samadhanai.Common.Enums.DepartmentType;
import com.samadhanai.samadhanai.Complaint.Model.Complaint;
import com.samadhanai.samadhanai.Email.Dto.EmailStatusDTO;
import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import com.sendgrid.helpers.mail.objects.Attachments;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
@RequiredArgsConstructor
@Slf4j
public class SendGridEmailService {

    private final ComplaintLetterBuilder letterBuilder;

    @Value("${sendgrid.api.key}")
    private String sendGridApiKey;

    @Value("${sendgrid.from.email}")
    private String fromEmail;

    @Value("${sendgrid.from.name}")
    private String fromName;

    @Value("${app.upload.dir}")
    private String uploadDir;

    public EmailStatusDTO sendComplaintToDepartment(Complaint complaint) {
        log.info("🚀 Starting SendGrid email process for: {}", complaint.getReferenceId());

        String deptEmail = getDepartmentEmail(complaint.getAssignedDepartment());
        log.info("📨 Target Email: {} for Department: {}", deptEmail, complaint.getAssignedDepartment());

        try {
            log.info("📝 Building complaint letter...");
            ComplaintLetterBuilder.LetterResult letter = letterBuilder.buildComplaintLetter(complaint);
            log.info("✅ Letter built successfully. Subject: {}", letter.getSubject());

            Email from = new Email(fromEmail, fromName);
            Email to = new Email(deptEmail);
            
            Content content = new Content("text/html", letter.getBodyHtml());
            Mail mail = new Mail(from, letter.getSubject(), to, content);

            // Add CC if citizen email exists
            if (complaint.getUserEmail() != null) {
                mail.personalization.get(0).addCc(new Email(complaint.getUserEmail()));
                log.info("📋 CC set to: {}", complaint.getUserEmail());
            }

            // Add attachments
            // Main photo (Cloudinary URL or legacy path)
            if (complaint.getPhotoPath() != null) {
                addAttachment(mail, complaint.getPhotoPath(), 
                    "complaint_" + complaint.getReferenceId() + "_photo_1.jpg");
                log.info("✅ Attached photo: {}", complaint.getPhotoPath());
            }

            // Extra photos (Cloudinary URLs or legacy paths)
            if (complaint.getExtraPhotoPaths() != null && !complaint.getExtraPhotoPaths().isBlank()) {
                int photoNum = 2;
                for (String path : complaint.getExtraPhotoPaths().split(",")) {
                    if (path.isBlank()) continue;
                    addAttachment(mail, path.trim(), 
                        "complaint_" + complaint.getReferenceId() + "_photo_" + photoNum + ".jpg");
                    log.info("✅ Attached extra photo: {}", path.trim());
                    photoNum++;
                }
            }

            SendGrid sg = new SendGrid(sendGridApiKey);
            Request request = new Request();

            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());

            log.info("🚀 SENDING EMAIL via SendGrid API to: {} for: {}", deptEmail, complaint.getReferenceId());
            log.info("⏰ Email send attempt started at: {}", LocalDateTime.now());

            Response response = sg.api(request);

            if (response.getStatusCode() == 202) {
                log.info("🎉 SUCCESS - Email sent to: {} for: {}", deptEmail, complaint.getReferenceId());
                log.info("⏰ Email send completed at: {}", LocalDateTime.now());
                
                return EmailStatusDTO.builder()
                    .sent(true)
                    .sentTo(deptEmail)
                    .ccTo(complaint.getUserEmail())
                    .sentAt(LocalDateTime.now())
                    .subject(letter.getSubject())
                    .complaintReferenceId(complaint.getReferenceId())
                    .build();
            } else {
                log.error("❌ SendGrid API error: {} - {}", response.getStatusCode(), response.getBody());
                return EmailStatusDTO.builder()
                    .sent(false)
                    .sentTo(deptEmail)
                    .failureReason("SendGrid error: " + response.getStatusCode())
                    .complaintReferenceId(complaint.getReferenceId())
                    .build();
            }

        } catch (Exception e) {
            log.error("❌ Error sending email via SendGrid: {}", e.getMessage(), e);
            return EmailStatusDTO.builder()
                .sent(false)
                .sentTo(deptEmail)
                .failureReason("SendGrid error: " + e.getMessage())
                .complaintReferenceId(complaint.getReferenceId())
                .build();
        }
    }

    private void addAttachment(Mail mail, String filePath, String filename) throws IOException {
        byte[] fileContent = null;
        
        // Check if it's a URL (Cloudinary) or local file path
        if (filePath.startsWith("http://") || filePath.startsWith("https://")) {
            // Download from Cloudinary URL
            log.info("📥 Downloading photo from URL: {}", filePath);
            try (InputStream is = new URL(filePath).openStream()) {
                fileContent = is.readAllBytes();
                log.info("✅ Downloaded {} bytes from URL", fileContent.length);
            } catch (Exception e) {
                log.error("❌ Failed to download from URL: {}", filePath, e);
                return;
            }
        } else {
            // Local file path (legacy photos)
            File file = new File(uploadDir + filePath);
            if (file.exists()) {
                fileContent = Files.readAllBytes(Paths.get(uploadDir + filePath));
                log.info("✅ Read {} bytes from local file", fileContent.length);
            } else {
                log.warn("⚠️ Attachment not found: {}", filePath);
                return;
            }
        }
        
        if (fileContent != null && fileContent.length > 0) {
            Attachments attachment = new Attachments();
            attachment.setContent(Base64.getEncoder().encodeToString(fileContent));
            attachment.setType("image/jpeg");
            attachment.setFilename(filename);
            attachment.setDisposition("attachment");
            
            mail.addAttachments(attachment);
            log.info("✅ Attached: {} ({} bytes)", filename, fileContent.length);
        }
    }

    public void sendStatusUpdateToCitizen(Complaint complaint, 
                                          ComplaintStatus oldStatus, 
                                          ComplaintStatus newStatus) {
        log.info("🚀 SendGrid status update for: {} → {} for: {}", 
                oldStatus, newStatus, complaint.getReferenceId());
        
        try {
            Email from = new Email(fromEmail, fromName);
            Email to = new Email(complaint.getUserEmail());
            
            String statusEmoji = switch (newStatus) {
                case RESOLVED -> "✅";
                case IN_PROGRESS -> "🔄";
                case REJECTED -> "❌";
                default -> "⏳";
            };
            
            String subject = String.format("📋 Update on Your Complaint %s — Status: %s %s", 
                    complaint.getReferenceId(), statusEmoji, newStatus.name());
            
            Content content = new Content("text/html", buildStatusUpdateEmail(complaint, oldStatus, newStatus));
            Mail mail = new Mail(from, subject, to, content);
            
            SendGrid sg = new SendGrid(sendGridApiKey);
            Request request = new Request();
            
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());
            
            Response response = sg.api(request);
            
            if (response.getStatusCode() == 202) {
                log.info("✅ Status update email sent to: {}", complaint.getUserEmail());
            } else {
                log.error("❌ Status update failed: {} - {}", response.getStatusCode(), response.getBody());
            }
            
        } catch (Exception e) {
            log.error("❌ SendGrid status update error: {}", e.getMessage());
        }
    }
    
    public EmailStatusDTO sendReminderToDepartment(Complaint complaint) {
        log.info("🚀 SendGrid reminder for: {}", complaint.getReferenceId());
        
        String deptEmail = getDepartmentEmail(complaint.getAssignedDepartment());
        
        try {
            Email from = new Email(fromEmail, fromName);
            Email to = new Email(deptEmail);
            
            String subject = String.format("⚠️ REMINDER: Complaint Pending 15+ Days — %s", 
                    complaint.getReferenceId());
            
            Content content = new Content("text/html", buildReminderEmail(complaint));
            Mail mail = new Mail(from, subject, to, content);
            
            // Add CC if citizen email exists
            if (complaint.getUserEmail() != null) {
                mail.personalization.get(0).addCc(new Email(complaint.getUserEmail()));
            }
            
            // Add attachments (Cloudinary URL or legacy path)
            if (complaint.getPhotoPath() != null) {
                addAttachment(mail, complaint.getPhotoPath(), 
                    "reminder_" + complaint.getReferenceId() + "_photo_1.jpg");
            }
            
            SendGrid sg = new SendGrid(sendGridApiKey);
            Request request = new Request();
            
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());
            
            Response response = sg.api(request);
            
            if (response.getStatusCode() == 202) {
                log.info("✅ Reminder sent to: {} for: {}", deptEmail, complaint.getReferenceId());
                return EmailStatusDTO.builder()
                        .sent(true)
                        .sentTo(deptEmail)
                        .sentAt(LocalDateTime.now())
                        .complaintReferenceId(complaint.getReferenceId())
                        .build();
            } else {
                log.error("❌ Reminder failed: {} - {}", response.getStatusCode(), response.getBody());
                return EmailStatusDTO.builder()
                        .sent(false)
                        .sentTo(deptEmail)
                        .failureReason("SendGrid API error: " + response.getBody())
                        .complaintReferenceId(complaint.getReferenceId())
                        .build();
            }
            
        } catch (Exception e) {
            log.error("❌ SendGrid reminder error: {}", e.getMessage());
            return EmailStatusDTO.builder()
                    .sent(false)
                    .sentTo(deptEmail)
                    .failureReason(e.getMessage())
                    .complaintReferenceId(complaint.getReferenceId())
                    .build();
        }
    }
    
    private String buildStatusUpdateEmail(Complaint complaint, 
                                          ComplaintStatus oldStatus, 
                                          ComplaintStatus newStatus) {
        String statusEmoji = switch (newStatus) {
            case RESOLVED -> "✅";
            case IN_PROGRESS -> "🔄";
            case REJECTED -> "❌";
            default -> "⏳";
        };
        
        String statusColor = switch (newStatus) {
            case RESOLVED -> "#16a34a";
            case IN_PROGRESS -> "#2563eb";
            case REJECTED -> "#dc2626";
            default -> "#d97706";
        };
        
        return String.format("""
            <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
                <div style="background: %s; color: white; padding: 20px; text-align: center;">
                    <h2>%s Your Complaint %s</h2>
                    <p>Status changed from %s to %s</p>
                </div>
                <div style="padding: 20px; background: #f8f9fa;">
                    <h3>Complaint Details:</h3>
                    <p><strong>Reference ID:</strong> %s</p>
                    <p><strong>Type:</strong> %s</p>
                    <p><strong>Location:</strong> %s</p>
                    <p><strong>Submitted:</strong> %s</p>
                </div>
                <div style="padding: 20px; text-align: center; color: #6c757d;">
                    <p>SamadhanAI Civic Platform</p>
                </div>
            </div>
            """, 
            statusColor, statusEmoji, complaint.getReferenceId(), 
            oldStatus.name(), newStatus.name(),
            complaint.getReferenceId(), complaint.getComplaintType(),
            complaint.getFullAddress() != null ? complaint.getFullAddress() : 
                (complaint.getWard() != null ? complaint.getWard() : "N/A"), 
            complaint.getCreatedAt());
    }
    
    private String buildReminderEmail(Complaint complaint) {
        long daysPending = java.time.temporal.ChronoUnit.DAYS.between(
                complaint.getCreatedAt(), LocalDateTime.now());
        
        return String.format("""
            <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
                <div style="background: #dc2626; color: white; padding: 20px; text-align: center;">
                    <h2>⚠️ ACTION REQUIRED</h2>
                    <p>Complaint Pending for %d Days</p>
                </div>
                <div style="padding: 20px; background: #fff3cd; border: 1px solid #ffeaa7;">
                    <h3>Reminder Details:</h3>
                    <p><strong>Reference ID:</strong> %s</p>
                    <p><strong>Type:</strong> %s</p>
                    <p><strong>Location:</strong> %s</p>
                    <p><strong>Days Pending:</strong> %d days</p>
                    <p><strong>Citizen:</strong> %s</p>
                    <p><strong>Email:</strong> %s</p>
                </div>
                <div style="padding: 20px; text-align: center; color: #6c757d;">
                    <p>SamadhanAI Civic Platform</p>
                </div>
            </div>
            """, 
            daysPending, complaint.getReferenceId(), complaint.getComplaintType(),
            complaint.getFullAddress() != null ? complaint.getFullAddress() : 
                (complaint.getWard() != null ? complaint.getWard() : "N/A"), 
            daysPending, complaint.getUserName(),
            complaint.getUserEmail());
    }

    private String getDepartmentEmail(DepartmentType dept) {

        if (dept == null) return "krishnamittal969145@gmail.com";

        return switch (dept) {
            case PWD -> "krishnamittal969145@gmail.com";
            case MUNICIPAL_CORPORATION -> "krishnamittal969145@gmail.com";
            case ELECTRICITY_BOARD -> "krishnamittal969145@gmail.com";
            case WATER_SUPPLY -> "krishnamittal969145@gmail.com";
            case SANITATION -> "krishnamittal969145@gmail.com";
        };

//        if (dept == null) return "mc.indore@mp.gov.in";
//        return switch (dept) {
//            case PWD -> "pwd.indore@mp.gov.in";
//            case MUNICIPAL_CORPORATION -> "mc.indore@mp.gov.in";
//            case ELECTRICITY_BOARD -> "mpcz.indore@mp.gov.in";
//            case WATER_SUPPLY -> "phed.indore@mp.gov.in";
//            case SANITATION -> "sanitation.indore@mp.gov.in";
//        };

    }
}
