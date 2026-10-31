package com.kiranacademy.rpms.controller;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.kiranacademy.rpms.exception.BadRequestException;
import com.kiranacademy.rpms.exception.ResourceNotFoundException;

@RestController
@RequestMapping("/api/interviews")
public class InterviewController {

    private final JdbcTemplate jdbcTemplate;

    public InterviewController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public List<Map<String, Object>> getInterviews(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String roundStatus,
            @RequestParam(required = false) Integer candidateId,
            @RequestParam(required = false) Integer jobId,
            @RequestParam(required = false) Integer applicationId,
            @RequestParam(required = false) String date) {

        StringBuilder sql = new StringBuilder(
                "SELECT ir.round_id, ir.application_id, ir.round_number, ir.round_name, ir.scheduled_at, " +
                "ir.interview_mode, ir.interview_location, ir.round_status, ir.interviewer_name, ir.remarks, ir.created_at, " +
                "a.candidate_id, a.job_id, a.application_status, " +
                "c.candidate_code, c.full_name AS candidate_name, c.email AS candidate_email, c.mobile AS candidate_mobile, " +
                "c.primary_technology, " +
                "j.job_title, j.work_location, " +
                "comp.company_id, comp.company_name, " +
                "fb.feedback_id, fb.rating, fb.technical_score, fb.communication_score, fb.problem_solving_score, fb.recommendation " +
                "FROM interview_rounds ir " +
                "JOIN applications a ON ir.application_id = a.application_id " +
                "JOIN candidates c ON a.candidate_id = c.candidate_id " +
                "JOIN jobs j ON a.job_id = j.job_id " +
                "JOIN companies comp ON j.company_id = comp.company_id " +
                "LEFT JOIN interview_feedback fb ON ir.round_id = fb.round_id " +
                "WHERE 1=1");
        List<Object> params = new ArrayList<>();

        String searchStatus = status != null && !status.isBlank() ? status : roundStatus;
        if (searchStatus != null && !searchStatus.isBlank()) {
            sql.append(" AND LOWER(ir.round_status) = ?");
            params.add(searchStatus.toLowerCase().trim());
        }

        if (candidateId != null) {
            sql.append(" AND a.candidate_id = ?");
            params.add(candidateId);
        }

        if (jobId != null) {
            sql.append(" AND a.job_id = ?");
            params.add(jobId);
        }

        if (applicationId != null) {
            sql.append(" AND ir.application_id = ?");
            params.add(applicationId);
        }

        if ("today".equalsIgnoreCase(date)) {
            sql.append(" AND DATE(ir.scheduled_at) = CURDATE()");
        } else if (date != null && !date.isBlank()) {
            sql.append(" AND DATE(ir.scheduled_at) = ?");
            params.add(date.trim());
        }

        sql.append(" ORDER BY ir.scheduled_at DESC, ir.round_id DESC");

        return jdbcTemplate.queryForList(sql.toString(), params.toArray());
    }

    @GetMapping("/{roundId}")
    public Map<String, Object> getInterviewById(@PathVariable("roundId") int roundId) {
        String sql = "SELECT ir.*, a.candidate_id, a.job_id, a.application_status, " +
                "c.candidate_code, c.full_name AS candidate_name, c.email AS candidate_email, c.mobile AS candidate_mobile, " +
                "c.primary_technology, c.qualification, " +
                "j.job_title, j.work_location, " +
                "comp.company_id, comp.company_name " +
                "FROM interview_rounds ir " +
                "JOIN applications a ON ir.application_id = a.application_id " +
                "JOIN candidates c ON a.candidate_id = c.candidate_id " +
                "JOIN jobs j ON a.job_id = j.job_id " +
                "JOIN companies comp ON j.company_id = comp.company_id " +
                "WHERE ir.round_id = ?";

        List<Map<String, Object>> list = jdbcTemplate.queryForList(sql, roundId);
        if (list.isEmpty()) {
            throw new ResourceNotFoundException("Interview round with ID " + roundId + " was not found.");
        }
        Map<String, Object> round = new HashMap<>(list.get(0));

        // Get feedbacks
        List<Map<String, Object>> feedbacks = jdbcTemplate.queryForList(
                "SELECT * FROM interview_feedback WHERE round_id = ? ORDER BY created_at DESC", roundId);
        round.put("feedbacks", feedbacks);

        return round;
    }

    @PostMapping("/schedule")
    @Transactional
    public ResponseEntity<Map<String, Object>> scheduleInterview(@RequestBody Map<String, Object> payload) {
        Integer applicationId = getInt(payload, "applicationId", "application_id");
        if (applicationId == null) {
            // Check if candidateId and jobId are supplied
            Integer candId = getInt(payload, "candidateId", "candidate_id");
            Integer jobId = getInt(payload, "jobId", "job_id");
            if (candId != null && jobId != null) {
                List<Map<String, Object>> apps = jdbcTemplate.queryForList(
                        "SELECT application_id FROM applications WHERE candidate_id = ? AND job_id = ? ORDER BY application_id DESC",
                        candId, jobId);
                if (!apps.isEmpty()) {
                    applicationId = (Integer) apps.get(0).get("application_id");
                }
            }
        }

        if (applicationId == null) {
            throw new BadRequestException("Application ID is required to schedule an interview.");
        }

        // Verify application
        List<Map<String, Object>> appList = jdbcTemplate.queryForList(
                "SELECT * FROM applications WHERE application_id = ?", applicationId);
        if (appList.isEmpty()) {
            throw new BadRequestException("Application with ID " + applicationId + " does not exist.");
        }

        Integer roundNumber = getInt(payload, "roundNumber", "round_number", 1);
        String roundName = getStr(payload, "roundName", "round_name");
        if (roundName == null || roundName.isBlank()) {
            roundName = "Technical Interview Round " + roundNumber;
        }

        String scheduledAtStr = getStr(payload, "scheduledAt", "scheduled_at");
        if (scheduledAtStr == null || scheduledAtStr.isBlank()) {
            throw new BadRequestException("Scheduled date and time are required.");
        }

        String interviewMode = getStr(payload, "interviewMode", "interview_mode");
        if (interviewMode == null || interviewMode.isBlank()) interviewMode = "ONLINE";

        String interviewLocation = getStr(payload, "interviewLocation", "interview_location");
        if (interviewLocation == null || interviewLocation.isBlank()) interviewLocation = "Online Meeting";

        String roundStatus = getStr(payload, "roundStatus", "round_status");
        if (roundStatus == null || roundStatus.isBlank()) roundStatus = "SCHEDULED";

        String interviewerName = getStr(payload, "interviewerName", "interviewer_name");
        String remarks = getStr(payload, "remarks");

        String insertSql = "INSERT INTO interview_rounds (application_id, round_number, round_name, scheduled_at, " +
                "interview_mode, interview_location, round_status, interviewer_name, remarks, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())";

        KeyHolder keyHolder = new GeneratedKeyHolder();
        final int fAppId = applicationId;
        final int fRoundNum = roundNumber;
        final String fRoundName = roundName;
        final String fScheduledAt = scheduledAtStr;
        final String fMode = interviewMode;
        final String fLoc = interviewLocation;
        final String fStatus = roundStatus;
        final String fInterviewer = interviewerName;
        final String fRemarks = remarks;

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS);
            ps.setInt(1, fAppId);
            ps.setInt(2, fRoundNum);
            ps.setString(3, fRoundName);
            ps.setString(4, fScheduledAt);
            ps.setString(5, fMode);
            ps.setString(6, fLoc);
            ps.setString(7, fStatus);
            ps.setString(8, fInterviewer);
            ps.setString(9, fRemarks);
            return ps;
        }, keyHolder);

        Number generatedKey = null;
        try {
            generatedKey = keyHolder.getKey();
        } catch (Exception e) {
            if (!keyHolder.getKeyList().isEmpty() && !keyHolder.getKeyList().get(0).isEmpty()) {
                Object val = keyHolder.getKeyList().get(0).values().iterator().next();
                if (val instanceof Number) generatedKey = (Number) val;
            }
        }
        int newId = generatedKey != null ? generatedKey.intValue() : 0;
        if (newId == 0) {
            List<Map<String, Object>> last = jdbcTemplate.queryForList("SELECT LAST_INSERT_ID() AS id");
            if (!last.isEmpty()) {
                newId = ((Number) last.get(0).get("id")).intValue();
            }
        }

        // Update application status to INTERVIEW_SCHEDULED if it is APPLIED or SHORTLISTED
        String currentAppStatus = (String) appList.get(0).get("application_status");
        if ("APPLIED".equalsIgnoreCase(currentAppStatus) || "SHORTLISTED".equalsIgnoreCase(currentAppStatus)) {
            jdbcTemplate.update("UPDATE applications SET application_status = 'INTERVIEW_SCHEDULED', updated_at = NOW() WHERE application_id = ?", applicationId);
            jdbcTemplate.update("INSERT INTO application_history (application_id, previous_status, new_status, changed_by, remarks, changed_at) " +
                    "VALUES (?, ?, 'INTERVIEW_SCHEDULED', 1, 'Interview round scheduled', NOW())", applicationId, currentAppStatus);
        }

        Map<String, Object> created = getInterviewById(newId);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    @PostMapping("/{roundId}/feedback")
    @Transactional
    public ResponseEntity<Map<String, Object>> submitFeedback(
            @PathVariable("roundId") int roundId,
            @RequestBody Map<String, Object> payload) {

        List<Map<String, Object>> existing = jdbcTemplate.queryForList(
                "SELECT ir.*, a.application_id, a.candidate_id, a.application_status " +
                "FROM interview_rounds ir " +
                "JOIN applications a ON ir.application_id = a.application_id " +
                "WHERE ir.round_id = ?", roundId);
        if (existing.isEmpty()) {
            throw new ResourceNotFoundException("Interview round with ID " + roundId + " was not found.");
        }
        Map<String, Object> currentRound = existing.get(0);
        int applicationId = (Integer) currentRound.get("application_id");
        String currentAppStatus = (String) currentRound.get("application_status");

        Double rating = getDouble(payload, "rating");
        Double technicalScore = getDouble(payload, "technicalScore", "technical_score");
        Double communicationScore = getDouble(payload, "communicationScore", "communication_score");
        Double problemSolvingScore = getDouble(payload, "problemSolvingScore", "problem_solving_score");
        String feedback = getStr(payload, "feedback");
        String recommendation = getStr(payload, "recommendation");
        if (recommendation == null || recommendation.isBlank()) {
            recommendation = "PASS";
        }
        Integer interviewerId = getInt(payload, "interviewerId", "interviewer_id");

        String insertSql = "INSERT INTO interview_feedback (round_id, interviewer_id, rating, technical_score, " +
                "communication_score, problem_solving_score, feedback, recommendation, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW())";

        jdbcTemplate.update(insertSql,
                roundId,
                interviewerId,
                rating != null ? BigDecimal.valueOf(rating) : null,
                technicalScore != null ? BigDecimal.valueOf(technicalScore) : null,
                communicationScore != null ? BigDecimal.valueOf(communicationScore) : null,
                problemSolvingScore != null ? BigDecimal.valueOf(problemSolvingScore) : null,
                feedback,
                recommendation);

        // Mark round completed
        jdbcTemplate.update("UPDATE interview_rounds SET round_status = 'COMPLETED' WHERE round_id = ?", roundId);

        // Update application status according to recommendation
        String newAppStatus = "PASS".equalsIgnoreCase(recommendation) || "SELECTED".equalsIgnoreCase(recommendation)
                ? "SELECTED" : ("REJECT".equalsIgnoreCase(recommendation) || "REJECTED".equalsIgnoreCase(recommendation) ? "REJECTED" : "ON_HOLD");

        jdbcTemplate.update("UPDATE applications SET application_status = ?, updated_at = NOW() WHERE application_id = ?",
                newAppStatus, applicationId);
        jdbcTemplate.update("INSERT INTO application_history (application_id, previous_status, new_status, changed_by, remarks, changed_at) " +
                "VALUES (?, ?, ?, 1, CONCAT('Interview feedback submitted: ', ?), NOW())",
                applicationId, currentAppStatus, newAppStatus, recommendation);

        Map<String, Object> updatedRound = getInterviewById(roundId);
        return ResponseEntity.ok(updatedRound);
    }

    private String getStr(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString().trim() : null;
    }

    private String getStr(Map<String, Object> map, String key1, String key2) {
        String val = getStr(map, key1);
        return val != null ? val : getStr(map, key2);
    }

    private Double getDouble(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val == null || val.toString().isBlank()) return null;
        try {
            return Double.parseDouble(val.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Double getDouble(Map<String, Object> map, String key1, String key2) {
        Double val = getDouble(map, key1);
        return val != null ? val : getDouble(map, key2);
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

    private Integer getInt(Map<String, Object> map, String key1, String key2, Integer defaultVal) {
        Integer val = getInt(map, key1, key2);
        return val != null ? val : defaultVal;
    }
}
