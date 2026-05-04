package com.samadhanai.samadhanai.Scheduler;

import com.samadhanai.samadhanai.Complaint.Model.Complaint;
import com.samadhanai.samadhanai.Complaint.Repository.ComplaintRepository;
import com.samadhanai.samadhanai.Email.Dto.EmailStatusDTO;
import com.samadhanai.samadhanai.Email.Service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class ReminderScheduler {

    private final ComplaintRepository complaintRepository;
    private final EmailService        emailService;

    // ✅ From application.properties: app.reminder.days-threshold=15
    @Value("${app.reminder.days-threshold:15}")
    private int reminderDaysThreshold;

    // ─────────────────────────────────────────────────────────────────
    // ⏰ Feature 8: Auto Reminder Email — runs every day at 9:00 AM
    //
    // What it does:
    //  1. Find all PENDING complaints older than 15 days
    //     where reminder has NOT been sent yet
    //  2. Send reminder email to department
    //  3. Mark complaint.reminderSent = true
    //
    // Cron: "0 0 9 * * *" = Every day at 9:00 AM
    // ─────────────────────────────────────────────────────────────────
    @Scheduled(cron = "0 0 9 * * *")
    public void sendPendingReminders() {

        log.info("⏰ Reminder Scheduler started — checking for {}+ day old complaints",
                reminderDaysThreshold);

        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(reminderDaysThreshold);

        // Find complaints needing reminder
        List<Complaint> complaintsNeedingReminder =
                complaintRepository.findComplaintsNeedingReminder(cutoffDate);

        if (complaintsNeedingReminder.isEmpty()) {
            log.info("✅ No reminders needed today");
            return;
        }

        log.info("Found {} complaints needing reminder", complaintsNeedingReminder.size());

        int successCount = 0;
        int failCount    = 0;

        for (Complaint complaint : complaintsNeedingReminder) {
            try {
                // Send reminder email to department
                EmailStatusDTO result = emailService.sendReminderToDepartment(complaint);

                if (result.isSent()) {
                    // Mark reminder sent so it doesn't get sent again
                    complaint.setReminderSent(true);
                    complaint.setReminderSentAt(LocalDateTime.now());
                    complaintRepository.save(complaint);
                    successCount++;

                    log.info("✅ Reminder sent for: {} → {}",
                            complaint.getReferenceId(),
                            complaint.getAssignedDepartment());
                } else {
                    failCount++;
                    log.warn("❌ Reminder failed for: {} — {}",
                            complaint.getReferenceId(), result.getFailureReason());
                }

            } catch (Exception e) {
                failCount++;
                log.error("❌ Exception sending reminder for {}: {}",
                        complaint.getReferenceId(), e.getMessage());
            }
        }

        log.info("⏰ Reminder Scheduler done — ✅ {} sent, ❌ {} failed",
                successCount, failCount);
    }
}