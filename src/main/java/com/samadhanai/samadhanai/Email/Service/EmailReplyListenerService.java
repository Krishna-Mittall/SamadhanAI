package com.samadhanai.samadhanai.Email.Service;

import com.samadhanai.samadhanai.Complaint.Repository.ComplaintRepository;
import com.samadhanai.samadhanai.Complaint.Service.ComplaintService;
import com.samadhanai.samadhanai.Complaint.Model.Complaint;
import jakarta.mail.*;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.search.FlagTerm;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailReplyListenerService {

    private final EmailAnalysisService emailAnalysisService;
    private final ComplaintService     complaintService;
    private final ComplaintRepository  complaintRepository;

    @Value("${app.email.imap.host}")
    private String imapHost;

    @Value("${app.email.imap.port}")
    private int imapPort;

    @Value("${app.email.imap.username}")
    private String imapUsername;

    @Value("${app.email.imap.password}")
    private String imapPassword;

    // Reference ID pattern — SAI-20241201-12345
    private static final Pattern REF_ID_PATTERN =
            Pattern.compile("SAI-\\d{8}-\\d{5}", Pattern.CASE_INSENSITIVE);

    // ─────────────────────────────────────────────────────────────────
    // 📧 MAIN: Check inbox and process department replies
    // Called by EmailReplyScheduler every 30 min
    // ─────────────────────────────────────────────────────────────────
    public void checkAndProcessReplies() {

        log.info("📬 Checking Gmail inbox for department replies...");

        Store  store  = null;
        Folder inbox  = null;

        try {
            // ── Step 1: Connect to Gmail IMAP ─────────────
            store = connectToGmail();
            if (store == null) {
                log.error("❌ Could not connect to Gmail IMAP — aborting");
                return;
            }

            // ── Step 2: Open INBOX ────────────────────────
            inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_WRITE); // READ_WRITE so we can mark as SEEN

            // ── Step 3: Fetch UNREAD emails only ──────────
            Message[] unreadMessages = inbox.search(
                    new FlagTerm(new Flags(Flags.Flag.SEEN), false)
            );

            log.info("Found {} unread email(s) in inbox", unreadMessages.length);

            if (unreadMessages.length == 0) {
                log.info("✅ No new emails — nothing to process");
                return;
            }

            // ── Step 4: Pre-load active complaint ref IDs ─
            // Optimization — avoid DB call per email
            Set<String> activeRefIds = getActiveComplaintRefIds();
            log.info("Active complaints with email sent: {}", activeRefIds.size());

            int processedCount = 0;
            int resolvedCount  = 0;

            // ── Step 5: Process each unread email ─────────
            for (Message message : unreadMessages) {
                try {
                    boolean resolved = processEmail(message, activeRefIds);
                    if (resolved) resolvedCount++;
                    processedCount++;

                    // Mark as READ after processing
                    // (whether resolved or not — don't re-process same email)
                    message.setFlag(Flags.Flag.SEEN, true);

                } catch (Exception e) {
                    log.error("Error processing email '{}': {}",
                            getSubjectSafe(message), e.getMessage());
                    // Still mark as seen to avoid infinite retry
                    try { message.setFlag(Flags.Flag.SEEN, true); } catch (Exception ignored) {}
                }
            }

            log.info("📬 Email check complete — {} processed, {} auto-resolved",
                    processedCount, resolvedCount);

        } catch (Exception e) {
            log.error("❌ Email inbox check failed: {}", e.getMessage(), e);
        } finally {
            // ── Step 6: Always close connections ──────────
            closeQuietly(inbox, store);
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // 🔍 Process single email
    // Returns true if complaint was auto-resolved
    // ─────────────────────────────────────────────────────────────────
    private boolean processEmail(Message message, Set<String> activeRefIds) throws Exception {

        String subject = getSubjectSafe(message);
        log.debug("Processing email: '{}'", subject);

        // ── Step A: Extract Reference ID ──────────────────
        // Check subject first, then body
        String refId = extractRefId(subject);

        if (refId == null) {
            // Try body
            String body = getEmailBodyText(message);
            refId = extractRefId(body);
        }

        if (refId == null) {
            log.debug("No SAI reference ID found in email '{}' — skipping", subject);
            return false;
        }

        log.info("Found reference ID: {} in email: '{}'", refId, subject);

        // ── Step B: Check if this is an active complaint ──
        String upperRefId = refId.toUpperCase();
        if (!activeRefIds.contains(upperRefId)) {
            log.info("Ref ID {} not in active complaints — already resolved/rejected or not found",
                    upperRefId);
            return false;
        }

        // ── Step C: Get email body for AI analysis ────────
        String emailBody = getEmailBodyText(message);

        // ── Step D: AI Analysis ───────────────────────────
        boolean isResolved = emailAnalysisService.isResolvedConfirmation(subject, emailBody);

        if (!isResolved) {
            log.info("Email for {} does not indicate resolution — no action", upperRefId);
            return false;
        }

        // ── Step E: Auto-resolve the complaint ────────────
        try {
            // Use first 500 chars of body as snippet for audit trail
            String snippet = emailBody != null && emailBody.length() > 500
                    ? emailBody.substring(0, 500)
                    : emailBody;

            complaintService.resolveByEmailReply(upperRefId, snippet);

            // Remove from active set so duplicate emails don't re-process
            activeRefIds.remove(upperRefId);

            log.info("✅ Complaint {} AUTO-RESOLVED via department email reply!", upperRefId);
            return true;

        } catch (Exception e) {
            log.error("Failed to auto-resolve complaint {}: {}", upperRefId, e.getMessage());
            return false;
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // 🔧 Connect to Gmail IMAP
    // ─────────────────────────────────────────────────────────────────
    private Store connectToGmail() {
        try {
            Properties props = new Properties();
            props.put("mail.imaps.host",            imapHost);
            props.put("mail.imaps.port",            String.valueOf(imapPort));
            props.put("mail.imaps.ssl.enable",      "true");
            props.put("mail.imaps.ssl.trust",       "*");
            props.put("mail.imaps.connectiontimeout", "10000"); // 10s
            props.put("mail.imaps.timeout",           "10000");

            Session session = Session.getInstance(props);
            Store store = session.getStore("imaps");
            store.connect(imapHost, imapUsername, imapPassword);

            log.info("✅ Connected to Gmail IMAP: {}", imapUsername);
            return store;

        } catch (Exception e) {
            log.error("❌ Gmail IMAP connection failed: {}", e.getMessage());
            return null;
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // 🔍 Extract SAI-XXXXXXXX reference ID from text
    // ─────────────────────────────────────────────────────────────────
    private String extractRefId(String text) {
        if (text == null || text.isBlank()) return null;

        Matcher matcher = REF_ID_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group().toUpperCase();
        }
        return null;
    }

    // ─────────────────────────────────────────────────────────────────
    // 📄 Extract plain text from email body
    // Handles plain text, HTML, and multipart emails
    // ─────────────────────────────────────────────────────────────────
    private String getEmailBodyText(Message message) {
        try {
            Object content = message.getContent();

            // Plain text email
            if (content instanceof String) {
                return (String) content;
            }

            // Multipart email (most common)
            if (content instanceof MimeMultipart multipart) {
                return extractTextFromMultipart(multipart);
            }

            return "";

        } catch (Exception e) {
            log.warn("Could not extract email body: {}", e.getMessage());
            return "";
        }
    }

    private String extractTextFromMultipart(MimeMultipart multipart) {
        StringBuilder text = new StringBuilder();
        try {
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart part = multipart.getBodyPart(i);
                String contentType = part.getContentType().toLowerCase();

                if (contentType.contains("text/plain")) {
                    // Prefer plain text
                    text.append(part.getContent().toString());
                } else if (contentType.contains("text/html") && text.isEmpty()) {
                    // HTML fallback — strip tags
                    String html = part.getContent().toString();
                    text.append(html.replaceAll("<[^>]+>", " ")
                            .replaceAll("\\s+", " ")
                            .trim());
                } else if (contentType.contains("multipart")) {
                    // Nested multipart
                    text.append(extractTextFromMultipart(
                            (MimeMultipart) part.getContent()));
                }
            }
        } catch (Exception e) {
            log.warn("Multipart extraction error: {}", e.getMessage());
        }
        return text.toString().trim();
    }

    // ─────────────────────────────────────────────────────────────────
    // 🔧 Get active complaint reference IDs
    // Only complaints where email was sent + still pending/in_progress
    // ─────────────────────────────────────────────────────────────────
    private Set<String> getActiveComplaintRefIds() {
        Set<String> refIds = new HashSet<>();
        try {
            complaintRepository.findAllActiveEmailedComplaints()
                    .forEach(c -> refIds.add(c.getReferenceId().toUpperCase()));
        } catch (Exception e) {
            log.error("Failed to load active complaint ref IDs: {}", e.getMessage());
        }
        return refIds;
    }

    // ─────────────────────────────────────────────────────────────────
    // 🔧 Safe helpers
    // ─────────────────────────────────────────────────────────────────
    private String getSubjectSafe(Message message) {
        try {
            String subject = message.getSubject();
            return subject != null ? subject : "(no subject)";
        } catch (Exception e) {
            return "(error reading subject)";
        }
    }

    private void closeQuietly(Folder folder, Store store) {
        try { if (folder != null && folder.isOpen()) folder.close(false); }
        catch (Exception ignored) {}
        try { if (store  != null && store.isConnected()) store.close(); }
        catch (Exception ignored) {}
    }
}