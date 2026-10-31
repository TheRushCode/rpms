package com.kiranacademy.rpms.controller;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
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
import static com.kiranacademy.rpms.util.RequestUtils.getDouble;
import static com.kiranacademy.rpms.util.RequestUtils.getInt;
import static com.kiranacademy.rpms.util.RequestUtils.getStr;
import static com.kiranacademy.rpms.util.RequestUtils.getStrWithDefault;

@RestController
@RequestMapping("/api")
public class PlacementController {

    private final JdbcTemplate jdbcTemplate;

    public PlacementController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/offers")
    public List<Map<String, Object>> getOffers(
            @RequestParam(required = false) Integer applicationId,
            @RequestParam(required = false) Integer candidateId,
            @RequestParam(required = false) String status) {

        StringBuilder sql = new StringBuilder(
                "SELECT o.offer_id, o.application_id, o.offer_date, o.offered_salary, o.joining_date, " +
                "o.offer_status, o.offer_letter_path, o.remarks, o.created_at, o.updated_at, " +
                "a.candidate_id, a.job_id, a.application_status, " +
                "c.candidate_code, c.full_name AS candidate_name, c.email AS candidate_email, c.mobile AS candidate_mobile, " +
                "c.primary_technology, c.qualification, " +
                "j.job_title, j.work_location, " +
                "comp.company_id, comp.company_name " +
                "FROM offers o " +
                "JOIN applications a ON o.application_id = a.application_id " +
                "JOIN candidates c ON a.candidate_id = c.candidate_id " +
                "JOIN jobs j ON a.job_id = j.job_id " +
                "JOIN companies comp ON j.company_id = comp.company_id " +
                "WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (applicationId != null) {
            sql.append(" AND o.application_id = ?");
            params.add(applicationId);
        }

        if (candidateId != null) {
            sql.append(" AND a.candidate_id = ?");
            params.add(candidateId);
        }

        if (status != null && !status.isBlank()) {
            sql.append(" AND LOWER(o.offer_status) = ?");
            params.add(status.toLowerCase().trim());
        }

        sql.append(" ORDER BY o.offer_id DESC");

        return jdbcTemplate.queryForList(sql.toString(), params.toArray());
    }

    @PostMapping("/offers")
    @Transactional
    public ResponseEntity<Map<String, Object>> createOffer(@RequestBody Map<String, Object> payload) {
        Integer applicationId = getInt(payload, "applicationId", "application_id");
        if (applicationId == null) {
            throw new BadRequestException("Application ID is required.");
        }

        List<Map<String, Object>> apps = jdbcTemplate.queryForList(
                "SELECT * FROM applications WHERE application_id = ?", applicationId);
        if (apps.isEmpty()) {
            throw new BadRequestException("Application with ID " + applicationId + " does not exist.");
        }

        Double offeredSalary = getDouble(payload, "offeredSalary", "offered_salary");
        String offerDate = getStr(payload, "offerDate", "offer_date");
        String joiningDate = getStr(payload, "joiningDate", "joining_date");
        String offerStatus = getStrWithDefault(payload, "offerStatus", "offer_status", "OFFERED");
        String offerLetterPath = getStr(payload, "offerLetterPath", "offer_letter_path");
        String remarks = getStr(payload, "remarks");

        String insertSql = "INSERT INTO offers (application_id, offer_date, offered_salary, joining_date, " +
                "offer_status, offer_letter_path, remarks, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, NOW(), NOW())";

        KeyHolder keyHolder = new GeneratedKeyHolder();
        final int fAppId = applicationId;
        final Double fSalary = offeredSalary;
        final String fOfferDate = offerDate;
        final String fJoiningDate = joiningDate;
        final String fStatus = offerStatus;
        final String fPath = offerLetterPath;
        final String fRemarks = remarks;

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS);
            ps.setInt(1, fAppId);
            if (fOfferDate != null && !fOfferDate.isBlank()) ps.setDate(2, java.sql.Date.valueOf(fOfferDate));
            else ps.setDate(2, new java.sql.Date(System.currentTimeMillis()));
            if (fSalary != null) ps.setBigDecimal(3, BigDecimal.valueOf(fSalary));
            else ps.setNull(3, java.sql.Types.DECIMAL);
            if (fJoiningDate != null && !fJoiningDate.isBlank()) ps.setDate(4, java.sql.Date.valueOf(fJoiningDate));
            else ps.setNull(4, java.sql.Types.DATE);
            ps.setString(5, fStatus);
            ps.setString(6, fPath);
            ps.setString(7, fRemarks);
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

        // Update application status to OFFERED
        String currentAppStatus = (String) apps.get(0).get("application_status");
        if (currentAppStatus == null) currentAppStatus = "SELECTED";
        jdbcTemplate.update("UPDATE applications SET application_status = 'OFFERED', updated_at = NOW() WHERE application_id = ?", applicationId);
        jdbcTemplate.update("INSERT INTO application_history (application_id, previous_status, new_status, changed_by, remarks, changed_at) " +
                "VALUES (?, ?, 'OFFERED', 1, 'Job offer extended', NOW())", applicationId, currentAppStatus);

        Map<String, Object> created = getOfferById(newId);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    @PutMapping("/offers/{id}/status")
    @Transactional
    public ResponseEntity<Map<String, Object>> updateOfferStatus(
            @PathVariable("id") int id,
            @RequestBody Map<String, Object> payload) {

        List<Map<String, Object>> offers = jdbcTemplate.queryForList("SELECT * FROM offers WHERE offer_id = ?", id);
        if (offers.isEmpty()) {
            throw new ResourceNotFoundException("Offer with ID " + id + " was not found.");
        }
        Map<String, Object> currentOffer = offers.get(0);
        int applicationId = (Integer) currentOffer.get("application_id");

        String newStatus = getStr(payload, "offerStatus", "newStatus");
        if (newStatus == null || newStatus.isBlank()) newStatus = getStr(payload, "status");
        if (newStatus == null || newStatus.isBlank()) {
            throw new BadRequestException("New offer status is required.");
        }

        String remarks = getStrWithDefault(payload, "remarks", "remarks", (String) currentOffer.get("remarks"));
        String joiningDateStr = getStr(payload, "joiningDate", "joining_date");

        jdbcTemplate.update("UPDATE offers SET offer_status = ?, remarks = ?, updated_at = NOW() WHERE offer_id = ?",
                newStatus, remarks, id);

        // If offer is ACCEPTED or JOINED, create or update placement record
        if ("ACCEPTED".equalsIgnoreCase(newStatus) || "JOINED".equalsIgnoreCase(newStatus)) {
            // Retrieve application, candidate, job, company
            Map<String, Object> appData = jdbcTemplate.queryForMap(
                    "SELECT a.candidate_id, a.job_id, j.company_id, j.min_salary " +
                    "FROM applications a " +
                    "JOIN jobs j ON a.job_id = j.job_id " +
                    "WHERE a.application_id = ?", applicationId);

            int candidateId = (Integer) appData.get("candidate_id");
            int jobId = (Integer) appData.get("job_id");
            int companyId = (Integer) appData.get("company_id");

            BigDecimal salary = (BigDecimal) currentOffer.get("offered_salary");
            java.sql.Date joiningDate = (java.sql.Date) currentOffer.get("joining_date");
            if (joiningDateStr != null && !joiningDateStr.isBlank()) {
                joiningDate = java.sql.Date.valueOf(joiningDateStr);
            }

            // Check if placement already exists
            Integer placementCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM placements WHERE candidate_id = ? AND job_id = ?",
                    Integer.class, candidateId, jobId);

            String placementStatus = "JOINED".equalsIgnoreCase(newStatus) ? "JOINED" : "CONFIRMED";

            if (placementCount == null || placementCount == 0) {
                jdbcTemplate.update(
                        "INSERT INTO placements (candidate_id, company_id, job_id, offer_id, placement_date, " +
                        "joining_date, salary, placement_status, remarks, created_at) " +
                        "VALUES (?, ?, ?, ?, CURDATE(), ?, ?, ?, ?, NOW())",
                        candidateId, companyId, jobId, id, joiningDate, salary, placementStatus, remarks);
            } else {
                jdbcTemplate.update(
                        "UPDATE placements SET placement_status = ?, joining_date = ?, salary = ?, remarks = ? " +
                        "WHERE candidate_id = ? AND job_id = ?",
                        placementStatus, joiningDate, salary, remarks, candidateId, jobId);
            }

            // Update candidate placement status
            jdbcTemplate.update("UPDATE candidates SET placement_status = 'PLACED', updated_at = NOW() WHERE candidate_id = ?", candidateId);

            // Update application status
            jdbcTemplate.update("UPDATE applications SET application_status = 'PLACED', updated_at = NOW() WHERE application_id = ?", applicationId);
            jdbcTemplate.update("INSERT INTO application_history (application_id, previous_status, new_status, changed_by, remarks, changed_at) " +
                    "VALUES (?, 'OFFERED', 'PLACED', 1, 'Offer accepted and candidate placed', NOW())", applicationId);
        } else if ("REJECTED".equalsIgnoreCase(newStatus)) {
            jdbcTemplate.update("UPDATE applications SET application_status = 'OFFER_REJECTED', updated_at = NOW() WHERE application_id = ?", applicationId);
            jdbcTemplate.update("INSERT INTO application_history (application_id, previous_status, new_status, changed_by, remarks, changed_at) " +
                    "VALUES (?, 'OFFERED', 'OFFER_REJECTED', 1, 'Candidate rejected offer', NOW())", applicationId);
        }

        Map<String, Object> updated = getOfferById(id);
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/placements")
    public List<Map<String, Object>> getPlacements(
            @RequestParam(required = false) Integer candidateId,
            @RequestParam(required = false) Integer companyId,
            @RequestParam(required = false) String status) {

        StringBuilder sql = new StringBuilder(
                "SELECT p.*, " +
                "c.candidate_code, c.full_name AS candidate_name, c.email AS candidate_email, c.mobile AS candidate_mobile, " +
                "c.qualification, c.primary_technology, " +
                "comp.company_name, comp.industry, " +
                "j.job_title, j.work_location " +
                "FROM placements p " +
                "JOIN candidates c ON p.candidate_id = c.candidate_id " +
                "JOIN companies comp ON p.company_id = comp.company_id " +
                "JOIN jobs j ON p.job_id = j.job_id " +
                "WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (candidateId != null) {
            sql.append(" AND p.candidate_id = ?");
            params.add(candidateId);
        }

        if (companyId != null) {
            sql.append(" AND p.company_id = ?");
            params.add(companyId);
        }

        if (status != null && !status.isBlank()) {
            sql.append(" AND LOWER(p.placement_status) = ?");
            params.add(status.toLowerCase().trim());
        }

        sql.append(" ORDER BY p.placement_date DESC, p.placement_id DESC");

        return jdbcTemplate.queryForList(sql.toString(), params.toArray());
    }

    @PostMapping("/placements")
    @Transactional
    public ResponseEntity<Map<String, Object>> createPlacement(@RequestBody Map<String, Object> payload) {
        Integer candidateId = getInt(payload, "candidateId", "candidate_id");
        Integer companyId = getInt(payload, "companyId", "company_id");
        Integer jobId = getInt(payload, "jobId", "job_id");
        Integer offerId = getInt(payload, "offerId", "offer_id");

        if (offerId != null) {
            List<Map<String, Object>> oList = jdbcTemplate.queryForList(
                    "SELECT o.offered_salary, a.candidate_id, a.job_id, j.company_id " +
                    "FROM offers o " +
                    "JOIN applications a ON o.application_id = a.application_id " +
                    "JOIN jobs j ON a.job_id = j.job_id " +
                    "WHERE o.offer_id = ?", offerId);
            if (!oList.isEmpty()) {
                Map<String, Object> oRow = oList.get(0);
                if (candidateId == null) candidateId = (Integer) oRow.get("candidate_id");
                if (jobId == null) jobId = (Integer) oRow.get("job_id");
                if (companyId == null) companyId = (Integer) oRow.get("company_id");
            }
        }

        if (companyId == null && jobId != null) {
            List<Map<String, Object>> jList = jdbcTemplate.queryForList("SELECT company_id FROM jobs WHERE job_id = ?", jobId);
            if (!jList.isEmpty()) {
                companyId = (Integer) jList.get(0).get("company_id");
            }
        }

        if (candidateId == null || companyId == null || jobId == null) {
            throw new BadRequestException("Candidate ID, Company ID, and Job ID are all required.");
        }

        String placementDateStr = getStr(payload, "placementDate", "placement_date");
        String joiningDateStr = getStr(payload, "joiningDate", "joining_date");
        if (joiningDateStr == null || joiningDateStr.isBlank()) {
            joiningDateStr = getStr(payload, "actualJoiningDate", "actual_joining_date");
        }
        Double salary = getDouble(payload, "salary");
        if (salary == null) salary = getDouble(payload, "offeredSalary", "offered_salary");
        String placementStatus = getStrWithDefault(payload, "placementStatus", "placement_status", "CONFIRMED");
        String remarks = getStr(payload, "remarks");

        String insertSql = "INSERT INTO placements (candidate_id, company_id, job_id, offer_id, placement_date, " +
                "joining_date, salary, placement_status, remarks, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())";

        KeyHolder keyHolder = new GeneratedKeyHolder();
        final int fCandId = candidateId;
        final int fCompId = companyId;
        final int fJobId = jobId;
        final Integer fOfferId = offerId;
        final String fPlaceDate = placementDateStr;
        final String fJoinDate = joiningDateStr;
        final Double fSal = salary;
        final String fStatus = placementStatus;
        final String fRem = remarks;

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS);
            ps.setInt(1, fCandId);
            ps.setInt(2, fCompId);
            ps.setInt(3, fJobId);
            if (fOfferId != null) ps.setInt(4, fOfferId); else ps.setNull(4, java.sql.Types.INTEGER);
            if (fPlaceDate != null && !fPlaceDate.isBlank()) ps.setDate(5, java.sql.Date.valueOf(fPlaceDate));
            else ps.setDate(5, new java.sql.Date(System.currentTimeMillis()));
            if (fJoinDate != null && !fJoinDate.isBlank()) ps.setDate(6, java.sql.Date.valueOf(fJoinDate));
            else ps.setNull(6, java.sql.Types.DATE);
            if (fSal != null) ps.setBigDecimal(7, BigDecimal.valueOf(fSal)); else ps.setNull(7, java.sql.Types.DECIMAL);
            ps.setString(8, fStatus);
            ps.setString(9, fRem);
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

        // Mark candidate as PLACED
        jdbcTemplate.update("UPDATE candidates SET placement_status = 'PLACED', updated_at = NOW() WHERE candidate_id = ?", candidateId);

        // Update application status to PLACED
        jdbcTemplate.update("UPDATE applications SET application_status = 'PLACED', updated_at = NOW() WHERE candidate_id = ? AND job_id = ?",
                candidateId, jobId);

        List<Map<String, Object>> result = jdbcTemplate.queryForList("SELECT * FROM placements WHERE placement_id = ?", newId);
        return new ResponseEntity<>(result.get(0), HttpStatus.CREATED);
    }

    private Map<String, Object> getOfferById(int id) {
        String sql = "SELECT o.*, " +
                "a.candidate_id, a.job_id, a.application_status, " +
                "c.candidate_code, c.full_name AS candidate_name, c.email AS candidate_email, c.mobile AS candidate_mobile, " +
                "j.job_title, j.work_location, " +
                "comp.company_id, comp.company_name " +
                "FROM offers o " +
                "JOIN applications a ON o.application_id = a.application_id " +
                "JOIN candidates c ON a.candidate_id = c.candidate_id " +
                "JOIN jobs j ON a.job_id = j.job_id " +
                "JOIN companies comp ON j.company_id = comp.company_id " +
                "WHERE o.offer_id = ?";
        List<Map<String, Object>> list = jdbcTemplate.queryForList(sql, id);
        if (list.isEmpty()) {
            throw new ResourceNotFoundException("Offer with ID " + id + " was not found.");
        }
        return list.get(0);
    }
}
