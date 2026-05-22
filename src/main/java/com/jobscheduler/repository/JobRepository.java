package com.jobscheduler.repository;

import com.jobscheduler.entity.Job;
import com.jobscheduler.enums.JobStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data access layer for the jobs table.
 *
 * FIX #9: @Modifying queries require an active transaction.
 * Added @Transactional directly on the repository method so it works
 * regardless of whether the caller has an existing transaction open.
 * This is the standard Spring Data JPA pattern for bulk update queries.
 */
@Repository
public interface JobRepository extends JpaRepository<Job, UUID> {

    Page<Job> findByStatus(JobStatus status, Pageable pageable);

    long countByStatus(JobStatus status);

    List<Job> findByStatusIn(List<JobStatus> statuses);

    Optional<Job> findByQuartzJobKey(String quartzJobKey);

    /**
     * Atomic status update — avoids loading the full entity when only
     * the status field needs changing. The @Transactional here ensures
     * the update is always wrapped in a transaction even if the caller
     * doesn't have one (e.g., called from a non-@Transactional context).
     */
    @Modifying
    @Transactional
    @Query("UPDATE Job j SET j.status = :status WHERE j.id = :id")
    int updateStatus(@Param("id") UUID id, @Param("status") JobStatus status);
}
