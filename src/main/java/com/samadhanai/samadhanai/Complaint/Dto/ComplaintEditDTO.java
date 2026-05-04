package com.samadhanai.samadhanai.Complaint.Dto;

import com.samadhanai.samadhanai.Common.Enums.ComplaintType;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ComplaintEditDTO {
    private ComplaintType complaintType;

    // User jo description update karna chahta hai
    @Size(min = 10, max = 1000, message = "Description must be 10-1000 chars")
    private String userDescription;
}

