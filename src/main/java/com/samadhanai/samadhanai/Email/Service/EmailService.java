package com.samadhanai.samadhanai.Email.Service;







import com.samadhanai.samadhanai.Common.Enums.ComplaintStatus;
import com.samadhanai.samadhanai.Common.DepartmentEmailDirectory;



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







    private final BrevoEmailService brevoEmailService;



    @Value("${app.upload.dir}")

    private String uploadDir;







    private static final String FROM_NAME   = "SamadhanAI Civic Platform";



    private static final String DATE_FORMAT = "dd MMM yyyy, hh:mm a";



    private static final String SHORT_DATE  = "dd MMM yyyy";







    // ─────────────────────────────────────────────────────────────────



    // 📧 Feature 3: Send Complaint Email to Department



    // Uses Brevo's HTTPS API so sending works on Render Free.



    // ─────────────────────────────────────────────────────────────────



    public EmailStatusDTO sendComplaintToDepartment(Complaint complaint) {

        log.info("Delegating email sending to Brevo for: {}", complaint.getReferenceId());

        return brevoEmailService.sendComplaintToDepartment(complaint);

    }



    // ─────────────────────────────────────────────────────────────────



    // 📧 Feature 7: Status Update Email to Citizen



    // Sent through the same Brevo transactional-email service.



    // ─────────────────────────────────────────────────────────────────



    public void sendStatusUpdateToCitizen(Complaint complaint,

                                          ComplaintStatus oldStatus,

                                          ComplaintStatus newStatus) {



        log.info("Status update email: {} → {} for: {}",

                oldStatus, newStatus, complaint.getReferenceId());

        

        brevoEmailService.sendStatusUpdateToCitizen(complaint, oldStatus, newStatus);



    }



    // ─────────────────────────────────────────────────────────────────



    // 📧 Feature 8: Reminder Email to Department



    // Uses the same Brevo service; replies are read by EmailReplyListenerService.



    // ─────────────────────────────────────────────────────────────────



    public EmailStatusDTO sendReminderToDepartment(Complaint complaint) {



        log.info("Reminder email for: {}", complaint.getReferenceId());

        

        return brevoEmailService.sendReminderToDepartment(complaint);

    }













    





    





    // ─────────────────────────────────────────────────────────────────



    // 🔧 Department Helpers



    // ─────────────────────────────────────────────────────────────────



    private String getDepartmentEmail(DepartmentType dept) {
        return DepartmentEmailDirectory.forDepartment(dept);



    }







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
