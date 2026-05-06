package com.samadhanai.samadhanai.Email.Service;


import com.samadhanai.samadhanai.Common.Enums.ComplaintStatus;

import com.samadhanai.samadhanai.Common.Enums.DepartmentType;

import com.samadhanai.samadhanai.Complaint.Model.Complaint;

import com.samadhanai.samadhanai.Email.Dto.EmailStatusDTO;

import lombok.RequiredArgsConstructor;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;

import org.springframework.stereotype.Service;


import java.time.LocalDateTime;

import java.time.format.DateTimeFormatter;


@Service
@RequiredArgsConstructor
@Slf4j

public class EmailService {
    private final SendGridEmailService sendGridEmailService;

    @Value("${app.upload.dir}")
    private String uploadDir;

    private static final String FROM_NAME = "SamadhanAI Civic Platform";
    private static final String DATE_FORMAT = "dd MMM yyyy, hh:mm a";
    private static final String SHORT_DATE = "dd MMM yyyy";


    public EmailStatusDTO sendComplaintToDepartment(Complaint complaint) {
        log.info("🚀 Delegating email sending to SendGrid for: {}", complaint.getReferenceId());
        return sendGridEmailService.sendComplaintToDepartment(complaint);
    }

    public void sendStatusUpdateToCitizen(Complaint complaint,
                                          ComplaintStatus oldStatus,
                                          ComplaintStatus newStatus) {
        log.info("Status update email: {} → {} for: {}",
                oldStatus, newStatus, complaint.getReferenceId());
        sendGridEmailService.sendStatusUpdateToCitizen(complaint, oldStatus, newStatus);
    }

    public EmailStatusDTO sendReminderToDepartment(Complaint complaint) {
        log.info("Reminder email for: {}", complaint.getReferenceId());
        return sendGridEmailService.sendReminderToDepartment(complaint);
    }

    private String getDepartmentEmail(DepartmentType dept) {

//        if (dept == null) return "mc.indore@mp.gov.in";
//        return switch (dept) {
//            case PWD -> "pwd.indore@mp.gov.in";
//            case MUNICIPAL_CORPORATION -> "mc.indore@mp.gov.in";
//            case ELECTRICITY_BOARD -> "mpcz.indore@mp.gov.in";
//            case WATER_SUPPLY -> "phed.indore@mp.gov.in";
//            case SANITATION -> "sanitation.indore@mp.gov.in";
//        };

        if (dept == null) return "krishnamittal969145@gmail.com";
        return switch (dept) {
            case PWD -> "krishnamittal969145@gmail.com";
            case MUNICIPAL_CORPORATION -> "krishnamittal969145@gmail.com";
            case ELECTRICITY_BOARD -> "krishnamittal969145@gmail.com";
            case WATER_SUPPLY -> "krishnamittal969145@gmail.com";
            case SANITATION -> "krishnamittal969145@gmail.com";
        };

    }

    private String getDepartmentDisplayName(DepartmentType dept) {

        if (dept == null) return "Municipal Corporation";
        return switch (dept) {
            case PWD -> "Public Works Department";
            case MUNICIPAL_CORPORATION -> "Municipal Corporation";
            case ELECTRICITY_BOARD -> "Electricity Board";
            case WATER_SUPPLY -> "Water Supply Department";
            case SANITATION -> "Sanitation Department";
        };

    }

}