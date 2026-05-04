package com.samadhanai.samadhanai.Complaint.Repository;

import com.samadhanai.samadhanai.Common.Enums.ComplaintStatus;
import com.samadhanai.samadhanai.Common.Enums.DepartmentType;
import com.samadhanai.samadhanai.Complaint.Model.Complaint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ComplaintRepository extends JpaRepository<Complaint, Long> {

    Optional<Complaint> findByReferenceId(String referenceId);

    List<Complaint> findAllByOrderByCreatedAtDesc();

    // ─── User-specific ────────────────────────────────────
    List<Complaint> findByUserIdOrderByCreatedAtDesc(Long userId);

    int countByUserId(Long userId);
    int countByUserIdAndStatus(Long userId, ComplaintStatus status);

    // ─── Dashboard ────────────────────────────────────────
    long countByStatus(ComplaintStatus status);

    long countByPhotoVerifiedFalse();

    List<Complaint> findByAssignedDepartment(DepartmentType dept);

    @Query("SELECT c FROM Complaint c WHERE c.status = 'PENDING' " +
            "AND c.createdAt < :cutoffDate AND c.photoVerified = true")
    List<Complaint> findIgnoredComplaints(@Param("cutoffDate") LocalDateTime cutoffDate);

    @Query("SELECT c.ward, COUNT(c) as total FROM Complaint c " +
            "WHERE c.ward IS NOT NULL GROUP BY c.ward ORDER BY total DESC")
    List<Object[]> findWardComplaintCounts();

    @Query("SELECT c.complaintType, COUNT(c) FROM Complaint c GROUP BY c.complaintType")
    List<Object[]> findComplaintTypeCounts();

    // ─── Reminder Scheduler ───────────────────────────────
    @Query("SELECT c FROM Complaint c WHERE c.status = 'PENDING' " +
            "AND c.photoVerified = true " +
            "AND c.reminderSent = false " +
            "AND c.createdAt < :cutoffDate")
    List<Complaint> findComplaintsNeedingReminder(@Param("cutoffDate") LocalDateTime cutoffDate);

    // ─── NEW: Way 3 — Email Reply Matching ───────────────
    // Sirf wo complaints jinhone email bheja tha department ko
    // In pe hi department reply karega
    @Query("SELECT c FROM Complaint c WHERE c.emailSent = true " +
            "AND c.status IN ('PENDING', 'IN_PROGRESS') " +
            "AND c.referenceId = :referenceId")
    Optional<Complaint> findActiveComplaintWithEmailSent(
            @Param("referenceId") String referenceId);

    // ─── NEW: Find all active emailed complaints ──────────
    // EmailReplyListenerService use karega — ref IDs ka set banana ke liye
    @Query("SELECT c FROM Complaint c WHERE c.emailSent = true " +
            "AND c.status IN ('PENDING', 'IN_PROGRESS')")
    List<Complaint> findAllActiveEmailedComplaints();
}