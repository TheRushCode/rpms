package com.kiranacademy.rpms;

import java.math.BigDecimal;
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
import static com.kiranacademy.rpms.util.RequestUtils.getIntWithDefault;
import static com.kiranacademy.rpms.util.RequestUtils.getStr;
import static com.kiranacademy.rpms.util.RequestUtils.getStrWithDefault;

@RestController
@RequestMapping("/api/candidates")
public class CandidateController {

    private final JdbcTemplate jdbcTemplate;

    public CandidateController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public List<Map<String, Object>> getCandidates(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String candidateName,
            @RequestParam(required = false) String candidateId,
            @RequestParam(required = false) String candidateCode,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String mobile,
            @RequestParam(required = false) String technology,
            @RequestParam(required = false) String qualification,
            @RequestParam(required = false) Integer passingYear,
            @RequestParam(required = false) String placementStatus,
            @RequestParam(required = false) String profileStatus) {

        StringBuilder sql = new StringBuilder("SELECT * FROM candidates WHERE 1=1");
        List<Object> params = new ArrayList<>();

        String searchName = name != null && !name.isBlank() ? name : candidateName;
        if (searchName != null && !searchName.isBlank()) {
            sql.append(" AND (LOWER(full_name) LIKE ? OR LOWER(candidate_code) LIKE ?)");
            params.add("%" + searchName.toLowerCase().trim() + "%");
            params.add("%" + searchName.toLowerCase().trim() + "%");
        }

        String searchCode = candidateId != null && !candidateId.isBlank() ? candidateId : candidateCode;
        if (searchCode != null && !searchCode.isBlank()) {
            sql.append(" AND LOWER(candidate_code) LIKE ?");
            params.add("%" + searchCode.toLowerCase().trim() + "%");
        }

        if (email != null && !email.isBlank()) {
            sql.append(" AND LOWER(email) LIKE ?");
            params.add("%" + email.toLowerCase().trim() + "%");
        }

        if (mobile != null && !mobile.isBlank()) {
            sql.append(" AND mobile LIKE ?");
            params.add("%" + mobile.trim() + "%");
        }

        if (technology != null && !technology.isBlank()) {
            sql.append(" AND LOWER(primary_technology) = ?");
            params.add(technology.toLowerCase().trim());
        }

        if (qualification != null && !qualification.isBlank()) {
            sql.append(" AND (LOWER(qualification) = ? OR LOWER(qualification) LIKE ?)");
            params.add(qualification.toLowerCase().trim());
            params.add("%" + qualification.toLowerCase().trim() + "%");
        }

        if (passingYear != null) {
            sql.append(" AND passing_year = ?");
            params.add(passingYear);
        }

        if (placementStatus != null && !placementStatus.isBlank()) {
            sql.append(" AND LOWER(placement_status) = ?");
            params.add(placementStatus.toLowerCase().trim());
        }

        if (profileStatus != null && !profileStatus.isBlank()) {
            sql.append(" AND LOWER(profile_status) = ?");
            params.add(profileStatus.toLowerCase().trim());
        }

        sql.append(" ORDER BY candidate_id ASC");

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> map = new HashMap<>(r);
            enhanceCandidateMap(map);
            result.add(map);
        }
        return result;
    }

    @GetMapping("/{id}")
    public Map<String, Object> getCandidateById(@PathVariable("id") int id) {
        List<Map<String, Object>> list = jdbcTemplate.queryForList("SELECT * FROM candidates WHERE candidate_id = ?", id);
        if (list.isEmpty()) {
            throw new ResourceNotFoundException("Candidate with ID " + id + " was not found.");
        }
        Map<String, Object> candidate = new HashMap<>(list.get(0));
        enhanceCandidateMap(candidate);

        // Fetch skills
        List<Map<String, Object>> skills = jdbcTemplate.queryForList(
                "SELECT cs.candidate_skill_id, cs.candidate_id, cs.skill_id, cs.proficiency_level, " +
                "cs.years_experience, cs.is_primary, s.skill_name, s.category " +
                "FROM candidate_skills cs " +
                "JOIN skills s ON cs.skill_id = s.skill_id " +
                "WHERE cs.candidate_id = ?", id);
        candidate.put("skills", skills);

        return candidate;
    }

    private void enhanceCandidateMap(Map<String, Object> map) {
        Object fnObj = map.get("full_name");
        String fullName = fnObj != null ? fnObj.toString().trim() : null;
        if (fullName != null && !fullName.isEmpty()) {
            map.putIfAbsent("candidate_name", fullName);
            String[] parts = fullName.split("\\s+", 2);
            map.putIfAbsent("first_name", parts[0]);
            map.putIfAbsent("last_name", parts.length > 1 ? parts[1] : "");
        } else {
            Object fName = map.get("first_name");
            Object lName = map.get("last_name");
            String comp = ((fName != null ? fName.toString() : "") + " " + (lName != null ? lName.toString() : "")).trim();
            map.putIfAbsent("full_name", comp);
            map.putIfAbsent("candidate_name", comp);
        }
    }

    @GetMapping("/{id}/applications")
    public List<Map<String, Object>> getCandidateApplications(@PathVariable("id") int id) {
        // Verify candidate exists
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM candidates WHERE candidate_id = ?", Integer.class, id);
        if (count == null || count == 0) {
            throw new ResourceNotFoundException("Candidate with ID " + id + " was not found.");
        }

        String sql = "SELECT a.application_id, a.candidate_id, a.job_id, a.application_date, a.application_status, " +
                     "a.resume_path, a.cover_letter, a.remarks, a.created_at, a.updated_at, " +
                     "j.job_title, j.work_location, j.job_type, j.employment_type, j.min_salary, j.max_salary, " +
                     "c.company_name, c.company_id, c.industry " +
                     "FROM applications a " +
                     "JOIN jobs j ON a.job_id = j.job_id " +
                     "JOIN companies c ON j.company_id = c.company_id " +
                     "WHERE a.candidate_id = ? " +
                     "ORDER BY a.application_date DESC";

        return jdbcTemplate.queryForList(sql, id);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createCandidate(@RequestBody Map<String, Object> payload) {
        String fullName = payload.get("fullName") != null ? payload.get("fullName").toString().trim() :
                (payload.get("full_name") != null ? payload.get("full_name").toString().trim() : null);
        String email = payload.get("email") != null ? payload.get("email").toString().trim() : null;
        String mobile = payload.get("mobile") != null ? payload.get("mobile").toString().trim() : null;
        String qualification = payload.get("qualification") != null ? payload.get("qualification").toString().trim() : null;
        Object passingYearObj = payload.get("passingYear") != null ? payload.get("passingYear") : payload.get("passing_year");

        if (fullName == null || fullName.isBlank()) {
            throw new BadRequestException("Full name is required.");
        }
        if (email == null || email.isBlank()) {
            throw new BadRequestException("Email is required.");
        }
        if (mobile == null || mobile.isBlank()) {
            throw new BadRequestException("Mobile number is required.");
        }
        if (qualification == null || qualification.isBlank()) {
            throw new BadRequestException("Qualification is required.");
        }
        if (passingYearObj == null) {
            throw new BadRequestException("Passing year is required.");
        }

        int passingYear;
        try {
            passingYear = Integer.parseInt(passingYearObj.toString().trim());
        } catch (NumberFormatException e) {
            throw new BadRequestException("Passing year must be a valid number.");
        }

        // Generate candidate code if not supplied
        String candidateCode = payload.get("candidateCode") != null ? payload.get("candidateCode").toString().trim() :
                (payload.get("candidate_code") != null ? payload.get("candidate_code").toString().trim() : null);
        if (candidateCode == null || candidateCode.isBlank()) {
            Integer maxId = jdbcTemplate.queryForObject("SELECT COALESCE(MAX(candidate_id), 0) FROM candidates", Integer.class);
            int nextSeq = (maxId != null ? maxId : 0) + 1;
            candidateCode = String.format("CAN%04d", nextSeq);
        }

        String college = getStr(payload, "college");
        String university = getStr(payload, "university");
        Double percentage = getDouble(payload, "percentage");
        Double cgpa = getDouble(payload, "cgpa");
        Integer backlogCount = getIntWithDefault(payload, "backlogCount", "backlog_count", 0);
        String primaryTechnology = getStr(payload, "primaryTechnology", "primary_technology");
        String currentLocation = getStr(payload, "currentLocation", "current_location");
        String preferredLocation = getStr(payload, "preferredLocation", "preferred_location");
        String profileStatus = getStrWithDefault(payload, "profileStatus", "profile_status", "ACTIVE");
        String placementStatus = getStrWithDefault(payload, "placementStatus", "placement_status", "NOT_PLACED");
        String gender = getStr(payload, "gender");
        String dob = getStr(payload, "dateOfBirth", "date_of_birth");

        String insertSql = "INSERT INTO candidates (candidate_code, full_name, email, mobile, date_of_birth, gender, " +
                "qualification, college, university, passing_year, percentage, cgpa, backlog_count, primary_technology, " +
                "current_location, preferred_location, profile_status, placement_status, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())";

        KeyHolder keyHolder = new GeneratedKeyHolder();
        final String finalCandidateCode = candidateCode;
        final String finalProfileStatus = profileStatus;
        final String finalPlacementStatus = placementStatus;
        final String finalGender = gender;
        final String finalCollege = college;
        final String finalUniversity = university;
        final Double finalPercentage = percentage;
        final Double finalCgpa = cgpa;
        final Integer finalBacklogCount = backlogCount;
        final String finalPrimaryTechnology = primaryTechnology;
        final String finalCurrentLocation = currentLocation;
        final String finalPreferredLocation = preferredLocation;

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, finalCandidateCode);
            ps.setString(2, fullName);
            ps.setString(3, email);
            ps.setString(4, mobile);
            if (dob != null && !dob.isBlank()) {
                ps.setDate(5, java.sql.Date.valueOf(dob));
            } else {
                ps.setNull(5, java.sql.Types.DATE);
            }
            ps.setString(6, finalGender);
            ps.setString(7, qualification);
            ps.setString(8, finalCollege);
            ps.setString(9, finalUniversity);
            ps.setInt(10, passingYear);
            if (finalPercentage != null) {
                ps.setBigDecimal(11, BigDecimal.valueOf(finalPercentage));
            } else {
                ps.setNull(11, java.sql.Types.DECIMAL);
            }
            if (finalCgpa != null) {
                ps.setBigDecimal(12, BigDecimal.valueOf(finalCgpa));
            } else {
                ps.setNull(12, java.sql.Types.DECIMAL);
            }
            ps.setInt(13, finalBacklogCount != null ? finalBacklogCount : 0);
            ps.setString(14, finalPrimaryTechnology);
            ps.setString(15, finalCurrentLocation);
            ps.setString(16, finalPreferredLocation);
            ps.setString(17, finalProfileStatus);
            ps.setString(18, finalPlacementStatus);
            return ps;
        }, keyHolder);

        Number generatedKey = keyHolder.getKey();
        int newId = generatedKey != null ? generatedKey.intValue() : 0;

        // Optionally insert skills if passed
        if (payload.get("skills") instanceof List<?> skillsList) {
            for (Object skObj : skillsList) {
                if (skObj instanceof Map<?, ?> skMap) {
                    Object skillIdVal = skMap.get("skillId") != null ? skMap.get("skillId") : skMap.get("skill_id");
                    if (skillIdVal != null) {
                        int skillId = Integer.parseInt(skillIdVal.toString());
                        String prof = skMap.get("proficiencyLevel") != null ? skMap.get("proficiencyLevel").toString() :
                                (skMap.get("proficiency_level") != null ? skMap.get("proficiency_level").toString() : "INTERMEDIATE");
                        jdbcTemplate.update("INSERT INTO candidate_skills (candidate_id, skill_id, proficiency_level, years_experience, is_primary) VALUES (?, ?, ?, ?, ?)",
                                newId, skillId, prof, 1.0, true);
                    }
                }
            }
        }

        Map<String, Object> created = getCandidateById(newId);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateCandidate(@PathVariable("id") int id, @RequestBody Map<String, Object> payload) {
        List<Map<String, Object>> existing = jdbcTemplate.queryForList("SELECT * FROM candidates WHERE candidate_id = ?", id);
        if (existing.isEmpty()) {
            throw new ResourceNotFoundException("Candidate with ID " + id + " was not found.");
        }
        Map<String, Object> current = existing.get(0);

        String fullName = getStrWithDefault(payload, "fullName", "full_name", (String) current.get("full_name"));
        String email = getStrWithDefault(payload, "email", "email", (String) current.get("email"));
        String mobile = getStrWithDefault(payload, "mobile", "mobile", (String) current.get("mobile"));
        String qualification = getStrWithDefault(payload, "qualification", "qualification", (String) current.get("qualification"));

        Integer passingYear = getIntWithDefault(payload, "passingYear", "passing_year", (Integer) current.get("passing_year"));
        String college = getStrWithDefault(payload, "college", "college", (String) current.get("college"));
        String university = getStrWithDefault(payload, "university", "university", (String) current.get("university"));
        Double percentage = getDouble(payload, "percentage");
        if (percentage == null && current.get("percentage") != null) {
            percentage = ((Number) current.get("percentage")).doubleValue();
        }
        Double cgpa = getDouble(payload, "cgpa");
        if (cgpa == null && current.get("cgpa") != null) {
            cgpa = ((Number) current.get("cgpa")).doubleValue();
        }
        Integer backlogCount = getIntWithDefault(payload, "backlogCount", "backlog_count", (Integer) current.get("backlog_count"));
        String primaryTechnology = getStrWithDefault(payload, "primaryTechnology", "primary_technology", (String) current.get("primary_technology"));
        String currentLocation = getStrWithDefault(payload, "currentLocation", "current_location", (String) current.get("current_location"));
        String preferredLocation = getStrWithDefault(payload, "preferredLocation", "preferred_location", (String) current.get("preferred_location"));
        String profileStatus = getStrWithDefault(payload, "profileStatus", "profile_status", (String) current.get("profile_status"));
        String placementStatus = getStrWithDefault(payload, "placementStatus", "placement_status", (String) current.get("placement_status"));
        String gender = getStrWithDefault(payload, "gender", "gender", (String) current.get("gender"));

        String updateSql = "UPDATE candidates SET full_name = ?, email = ?, mobile = ?, gender = ?, qualification = ?, " +
                "college = ?, university = ?, passing_year = ?, percentage = ?, cgpa = ?, backlog_count = ?, " +
                "primary_technology = ?, current_location = ?, preferred_location = ?, profile_status = ?, " +
                "placement_status = ?, updated_at = NOW() WHERE candidate_id = ?";

        jdbcTemplate.update(updateSql,
                fullName, email, mobile, gender, qualification,
                college, university, passingYear,
                percentage != null ? BigDecimal.valueOf(percentage) : null,
                cgpa != null ? BigDecimal.valueOf(cgpa) : null,
                backlogCount != null ? backlogCount : 0,
                primaryTechnology, currentLocation, preferredLocation,
                profileStatus, placementStatus, id);

        // If skills array is passed in payload, refresh candidate skills
        if (payload.containsKey("skills") && payload.get("skills") instanceof List<?> skillsList) {
            jdbcTemplate.update("DELETE FROM candidate_skills WHERE candidate_id = ?", id);
            for (Object skObj : skillsList) {
                if (skObj instanceof Map<?, ?> skMap) {
                    Object skillIdVal = skMap.get("skillId") != null ? skMap.get("skillId") : skMap.get("skill_id");
                    if (skillIdVal != null) {
                        int skillId = Integer.parseInt(skillIdVal.toString());
                        String prof = skMap.get("proficiencyLevel") != null ? skMap.get("proficiencyLevel").toString() :
                                (skMap.get("proficiency_level") != null ? skMap.get("proficiency_level").toString() : "INTERMEDIATE");
                        jdbcTemplate.update("INSERT INTO candidate_skills (candidate_id, skill_id, proficiency_level, years_experience, is_primary) VALUES (?, ?, ?, ?, ?)",
                                id, skillId, prof, 1.0, true);
                    }
                }
            }
        }

        Map<String, Object> updated = getCandidateById(id);
        return ResponseEntity.ok(updated);
    }
}
