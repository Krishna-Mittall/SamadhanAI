package com.samadhanai.samadhanai.Common.Enums;

// using enums to avoid typo error, its gives error on compile time...

public enum ComplaintStatus {
    PENDING,       // Just submitted, no action yet
    IN_PROGRESS,   // Department acknowledged
    RESOLVED,      // Problem fixed
    IGNORED,       // No action for 30+ days
    REJECTED       // Fake photo detected
}
