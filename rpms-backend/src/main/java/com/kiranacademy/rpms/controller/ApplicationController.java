package com.kiranacademy.rpms.controller;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.kiranacademy.rpms.exception.BadRequestException;
import com.kiranacademy.rpms.exception.ResourceNotFoundException;

@RestController
@RequestMapping("/api/applications")
public class ApplicationController {

    private final JdbcTemplate jdbcTemplate;

    public ApplicationController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public List<Map<String, Object>> getApplications(
            @RequestParam(required = false) Integer candidateId,
            @RequestParam(required = false) Integer jobId,
            @RequestParam(required = false) Integer companyId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search) {

        StringBuilder sql = new StringBuilder(
                "SELECT a.application_id, a.candidate_id, a.job_id, a.application_date, a.application_status, " +
                "a.resume_path, a.cover_letter, a.remarks, a.created_at, a.updated_at, " +
                "c.candidate_code, c.full_name AS candidate_name, c.email AS candidate_email, c.mobile AS candidate_mobile, " +
                "c.qualification, c.passing_year, c.percentage, c.primary_technology, " +
                "j.job_title, j.work_location, j.job_type, j.employment_type, j.min_salary, j.max_salary, " +
                "comp.company_id, comp.company_name, comp.industry " +
                "FROM applications a " +
                "JOIN candidates c ON a.candidate_id = c.candidate_id " +
                "JOIN jobs j ON a.job_id = j.job_id " +
                "JOIN companies comp ON j.company_id = comp.company_id " +
                "WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (candidateId != null) {
            sql.append(" AND a.candidate_id = ?");
            params.add(candidateId);
        }

        if (jobId != null) {
            sql.append(" AND a.job_id = ?");
            params.add(jobId);
        }

        if (companyId != null) {
            sql.append(" AND comp.company_id = ?");
            params.add(companyId);
        }

        if (status != null && !status.isBlank()) {
            sql.append(" AND LOWER(a.application_status) = ?");
            params.add(status.toLowerCase().trim());
        }

        if (search != null && !search.isBlank()) {
            sql.append(" AND (LOWER(c.full_name) LIKE ? OR LOWER(j.job_title) LIKE ? OR LOWER(comp.company_name) LIKE ?)");
            String pattern = "%" + search.toLowerCase().trim() + "%";
            params.add(pattern);
            params.add(pattern);
            params.add(pattern);
        }

        sql.append(" ORDER BY a.application_id DESC");

        return jdbcTemplate.queryForList(sql.toString(), params.toArray());
    }

    @GetMapping("/{id}")
    public Map<String, Object> getApplicationById(@PathVariable("id") int id) {
        String sql = "SELECT a.*, " +
                "c.candidate_code, c.full_name AS candidate_name, c.email AS candidate_email, c.mobile AS candidate_mobile, " +
                "c.qualification, c.passing_year, c.percentage, c.cgpa, c.primary_technology, c.placement_status, " +
                "j.job_title, j.work_location, j.job_type, j.employment_type, j.min_salary, j.max_salary, " +
                "comp.company_id, comp.company_name, comp.industry, comp.contact_person, comp.contact_email " +
                "FROM applications a " +
                "JOIN candidates c ON a.candidate_id = c.candidate_id " +
                "JOIN jobs j ON a.job_id = j.job_id " +
                "JOIN companies comp ON j.company_id = comp.company_id " +
                "WHERE a.application_id = ?";

        List<Map<String, Object>> list = jdbcTemplate.queryForList(sql, id);
        if (list.isEmpty()) {
            throw new ResourceNotFoundException("Application with ID " + id + " was not found.");
        }
        Map<String, Object> app = new HashMap<>(list.get(0));

        // Get interview rounds
        List<Map<String, Object>> rounds = jdbcTemplate.queryForList(
                "SELECT ir.*, (SELECT rating FROM interview_feedback ifb WHERE ifb.round_id = ir.round_id LIMIT 1) AS rating, " +
                "(SELECT recommendation FROM interview_feedback ifb WHERE ifb.round_id = ir.round_id LIMIT 1) AS recommendation " +
                "FROM interview_rounds ir WHERE ir.application_id = ? ORDER BY ir.round_number ASC", id);
        app.put("rounds", rounds);

        // Get offer details if present
        List<Map<String, Object>> offers = jdbcTemplate.queryForList(
                "SELECT * FROM offers WHERE application_id = ? ORDER BY offer_id DESC", id);
        app.put("offers", offers);

        // Get history
        List<Map<String, Object>> history = jdbcTemplate.queryForList(
                "SELECT * FROM application_history WHERE application_id = ? ORDER BY changed_at DESC", id);
        app.put("history", history);

        return app;
    }

    @PostMapping
    @Transactional
    public ResponseEntity<Map<String, Object>> createApplication(@RequestBody Map<String, Object> payload) {
        Integer candidateId = getInt(payload, "candidateId", "candidate_id");
        Integer jobId = getInt(payload, "jobId", "job_id");

        if (candidateId == null) {
            throw new BadRequestException("Candidate ID is required.");
        }
        if (jobId == null) {
            throw new BadRequestException("Job ID is required.");
        }

        // Verify candidate exists
        Integer candCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM candidates WHERE candidate_id = ?", Integer.class, candidateId);
        if (candCount == null || candCount == 0) {
            throw new BadRequestException("Candidate with ID " + candidateId + " does not exist.");
        }

        // Verify job exists
        Integer jobCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM jobs WHERE job_id = ?", Integer.class, jobId);
        if (jobCount == null || jobCount == 0) {
            throw new BadRequestException("Job with ID " + jobId + " does not exist.");
        }

        // Prevent duplicate application
        Integer dupCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM applications WHERE candidate_id = ? AND job_id = ?",
                Integer.class, candidateId, jobId);
        if (dupCount != null && dupCount > 0) {
            throw new BadRequestException("This candidate has already applied for this job opening.");
        }

        String status = getStr(payload, "applicationStatus", "application_status");
        if (status == null || status.isBlank()) {
            status = "APPLIED";
        }
        String resumePath = getStr(payload, "resumePath", "resume_path");
        String coverLetter = getStr(payload, "coverLetter", "cover_letter");
        String remarks = getStr(payload, "remarks");
        if (remarks == null || remarks.isBlank()) {
            remarks = "Application submitted";
        }

        String insertSql = "INSERT INTO applications (candidate_id, job_id, application_date, application_status, " +
                "resume_path, cover_letter, remarks, created_at, updated_at) " +
                "VALUES (?, ?, NOW(), ?, ?, ?, ?, NOW(), NOW())";

        KeyHolder keyHolder = new GeneratedKeyHolder();
        final int fCandidateId = candidateId;
        final int fJobId = jobId;
        final String fStatus = status;
        final String fResume = resumePath;
        final String fCover = coverLetter;
        final String fRemarks = remarks;

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS);
            ps.setInt(1, fCandidateId);
            ps.setInt(2, fJobId);
            ps.setString(3, fStatus);
            ps.setString(4, fResume);
            ps.setString(5, fCover);
            ps.setString(6, fRemarks);
            return ps;
        }, keyHolder);

        Number generatedKey = keyHolder.getKey();
        int newId = generatedKey != null ? generatedKey.intValue() : 0;

        // Record history
        jdbcTemplate.update("INSERT INTO application_history (application_id, previous_status, new_status, changed_by, remarks, changed_at) " +
                "VALUES (?, NULL, ?, 1, 'Initial application submitted', NOW())", newId, fStatus);

        Map<String, Object> created = getApplicationById(newId);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    @PutMapping("/{id}/status")
    @Transactional
    public ResponseEntity<Map<String, Object>> updateApplicationStatus(
            @PathVariable("id") int id,
            @RequestBody Map<String, Object> payload) {

        List<Map<String, Object>> existing = jdbcTemplate.queryForList("SELECT * FROM applications WHERE application_id = ?", id);
        if (existing.isEmpty()) {
            throw new ResourceNotFoundException("Application with ID " + id + " was not found.");
        }
        String currentStatus = (String) existing.get(0).get("application_status");

        String newStatus = getStr(payload, "applicationStatus", "newStatus");
        if (newStatus == null || newStatus.isBlank()) {
            newStatus = getStr(payload, "status");
        }
        if (newStatus == null || newStatus.isBlank()) {
            throw new BadRequestException("New application status is required.");
        }

        String remarks = getStr(payload, "remarks");
        if (remarks == null || remarks.isBlank()) {
            remarks = "Status updated to " + newStatus;
        }

        jdbcTemplate.update("UPDATE applications SET application_status = ?, remarks = ?, updated_at = NOW() WHERE application_id = ?",
                newStatus, remarks, id);

        // Record in history
        jdbcTemplate.update("INSERT INTO application_history (application_id, previous_status, new_status, changed_by, remarks, changed_at) " +
                "VALUES (?, ?, ?, 1, ?, NOW())", id, currentStatus, newStatus, remarks);

        Map<String, Object> updated = getApplicationById(id);
        return ResponseEntity.ok(updated);
    }

    private String getStr(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString().trim() : null;
    }

    private String getStr(Map<String, Object> map, String key1, String key2) {
        String val = getStr(map, key1);
        return val != null ? val : getStr(map, key2);
    }

    private Integer getInt(Map<String, Object> map, String key1, String key2) {
        Object val = map.get(key1) != null ? map.get(key1) : map.get(key2);
        if (val == null || val.toString().isBlank()) return null;
        try {
            return Integer.parseInt(val.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
