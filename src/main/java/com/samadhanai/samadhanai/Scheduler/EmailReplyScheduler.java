package com.samadhanai.samadhanai.Scheduler;

import com.samadhanai.samadhanai.Email.Service.EmailReplyListenerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
@RequiredArgsConstructor
@Slf4j
public class EmailReplyScheduler {

    private final EmailReplyListenerService emailReplyListenerService;

    @Value("${app.email.reply.enabled:true}")
    private boolean replyCheckEnabled;

    // ─────────────────────────────────────────────────────────────────
    // ⏰ Feature: Auto-resolve via department email reply
    //
    // Runs every 30 minutes
    // fixedDelayString — next run starts 30 min AFTER previous finishes
    // (safer than fixedRate — no overlap if Gmail is slow)
    //
    // Can be disabled via: app.email.reply.enabled=false
    // Useful for dev/test where you don't want real IMAP calls
    // ─────────────────────────────────────────────────────────────────
    @Scheduled(fixedDelayString = "${app.email.reply.check-interval-ms:1800000}")
    public void checkDepartmentEmailReplies() {

        if (!replyCheckEnabled) {
            log.debug("Email reply check is DISABLED — skipping");
            return;
        }

        String timeStr = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss"));

        log.info("⏰ EmailReplyScheduler triggered at {}", timeStr);

        try {
            emailReplyListenerService.checkAndProcessReplies();
        } catch (Exception e) {
            // Never let scheduler crash — just log and move on
            log.error("❌ EmailReplyScheduler encountered error: {}", e.getMessage(), e);
        }

        log.info("⏰ EmailReplyScheduler cycle complete");
    }
}