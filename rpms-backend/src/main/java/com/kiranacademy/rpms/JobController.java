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
import static com.kiranacademy.rpms.util.RequestUtils.getInt;
import static com.kiranacademy.rpms.util.RequestUtils.getIntWithDefault;
import static com.kiranacademy.rpms.util.RequestUtils.getStr;
import static com.kiranacademy.rpms.util.RequestUtils.getStrWithDefault;

@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final JdbcTemplate jdbcTemplate;

    public JobController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public List<Map<String, Object>> getAllJobs(
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String jobTitle,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String workLocation,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String jobStatus,
            @RequestParam(required = false) String jobType,
            @RequestParam(required = false) String employmentType,
            @RequestParam(required = false) Integer companyId,
            @RequestParam(required = false) String companyName) {

        StringBuilder sql = new StringBuilder(
                "SELECT j.*, c.company_name, c.industry, c.headquarters " +
                "FROM jobs j " +
                "LEFT JOIN companies c ON j.company_id = c.company_id " +
                "WHERE 1=1");
        List<Object> params = new ArrayList<>();

        String searchTitle = title != null && !title.isBlank() ? title : jobTitle;
        if (searchTitle != null && !searchTitle.isBlank()) {
            sql.append(" AND LOWER(j.job_title) LIKE ?");
            params.add("%" + searchTitle.toLowerCase().trim() + "%");
        }

        String searchLoc = location != null && !location.isBlank() ? location : workLocation;
        if (searchLoc != null && !searchLoc.isBlank()) {
            sql.append(" AND LOWER(j.work_location) LIKE ?");
            params.add("%" + searchLoc.toLowerCase().trim() + "%");
        }

        String searchStatus = status != null && !status.isBlank() ? status : jobStatus;
        if (searchStatus != null && !searchStatus.isBlank()) {
            sql.append(" AND LOWER(j.job_status) = ?");
            params.add(searchStatus.toLowerCase().trim());
        }

        if (jobType != null && !jobType.isBlank()) {
            sql.append(" AND LOWER(j.job_type) = ?");
            params.add(jobType.toLowerCase().trim());
        }

        if (employmentType != null && !employmentType.isBlank()) {
            sql.append(" AND LOWER(j.employment_type) = ?");
            params.add(employmentType.toLowerCase().trim());
        }

        if (companyId != null) {
            sql.append(" AND j.company_id = ?");
            params.add(companyId);
        }

        if (companyName != null && !companyName.isBlank()) {
            sql.append(" AND LOWER(c.company_name) LIKE ?");
            params.add("%" + companyName.toLowerCase().trim() + "%");
        }

        sql.append(" ORDER BY j.job_id ASC");

        return jdbcTemplate.queryForList(sql.toString(), params.toArray());
    }

    @GetMapping("/{id}")
    public Map<String, Object> getJobById(@PathVariable("id") int id) {
        String sql = "SELECT j.*, c.company_name, c.industry, c.headquarters, c.contact_person, c.contact_email " +
                     "FROM jobs j " +
                     "LEFT JOIN companies c ON j.company_id = c.company_id " +
                     "WHERE j.job_id = ?";
        List<Map<String, Object>> list = jdbcTemplate.queryForList(sql, id);
        if (list.isEmpty()) {
            throw new ResourceNotFoundException("Job with ID " + id + " was not found.");
        }
        Map<String, Object> job = new HashMap<>(list.get(0));

        // Total applications for this job
        Integer appCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM applications WHERE job_id = ?", Integer.class, id);
        job.put("total_applications", appCount != null ? appCount : 0);

        return job;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createJob(@RequestBody Map<String, Object> payload) {
        Integer companyId = getInt(payload, "companyId", "company_id");
        if (companyId == null) {
            throw new BadRequestException("Company ID is required.");
        }
        // Verify company exists
        Integer cCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM companies WHERE company_id = ?", Integer.class, companyId);
        if (cCount == null || cCount == 0) {
            throw new BadRequestException("Referenced company with ID " + companyId + " does not exist.");
        }

        String jobTitle = getStr(payload, "jobTitle", "job_title");
        if (jobTitle == null || jobTitle.isBlank()) {
            throw new BadRequestException("Job title is required.");
        }

        String jobDesc = getStr(payload, "jobDescription", "job_description");
        String jobType = getStrWithDefault(payload, "jobType", "job_type", "FULL_TIME");
        String empType = getStrWithDefault(payload, "employmentType", "employment_type", "PERMANENT");
        String workLocation = getStr(payload, "workLocation", "work_location");
        Double minSalary = getDouble(payload, "minSalary", "min_salary");
        Double maxSalary = getDouble(payload, "maxSalary", "max_salary");

        if (minSalary != null && maxSalary != null && minSalary > maxSalary) {
            throw new BadRequestException("Minimum salary cannot exceed maximum salary.");
        }

        String reqQual = getStr(payload, "requiredQualification", "required_qualification");
        Double minPercentage = getDouble(payload, "minimumPercentage", "minimum_percentage");
        Integer maxBacklogs = getIntWithDefault(payload, "maximumBacklogs", "maximum_backlogs", 0);
        String deadlineStr = getStr(payload, "applicationDeadline", "application_deadline");
        Integer openings = getIntWithDefault(payload, "openings", "openings", 1);
        if (openings != null && openings < 1) {
            throw new BadRequestException("Openings count must be at least 1.");
        }

        String jobStatus = getStrWithDefault(payload, "jobStatus", "job_status", "OPEN");

        String insertSql = "INSERT INTO jobs (company_id, job_title, job_description, job_type, employment_type, " +
                "work_location, min_salary, max_salary, required_qualification, minimum_percentage, maximum_backlogs, " +
                "application_deadline, openings, job_status, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())";

        KeyHolder keyHolder = new GeneratedKeyHolder();
        final int fCompanyId = companyId;
        final String fJobTitle = jobTitle;
        final String fJobDesc = jobDesc;
        final String fJobType = jobType;
        final String fEmpType = empType;
        final String fWorkLoc = workLocation;
        final Double fMinSal = minSalary;
        final Double fMaxSal = maxSalary;
        final String fReqQual = reqQual;
        final Double fMinPct = minPercentage;
        final Integer fMaxBl = maxBacklogs;
        final String fDeadline = deadlineStr;
        final Integer fOpenings = openings;
        final String fJobStatus = jobStatus;

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS);
            ps.setInt(1, fCompanyId);
            ps.setString(2, fJobTitle);
            ps.setString(3, fJobDesc);
            ps.setString(4, fJobType);
            ps.setString(5, fEmpType);
            ps.setString(6, fWorkLoc);
            if (fMinSal != null) ps.setBigDecimal(7, BigDecimal.valueOf(fMinSal)); else ps.setNull(7, java.sql.Types.DECIMAL);
            if (fMaxSal != null) ps.setBigDecimal(8, BigDecimal.valueOf(fMaxSal)); else ps.setNull(8, java.sql.Types.DECIMAL);
            ps.setString(9, fReqQual);
            if (fMinPct != null) ps.setBigDecimal(10, BigDecimal.valueOf(fMinPct)); else ps.setNull(10, java.sql.Types.DECIMAL);
            ps.setInt(11, fMaxBl != null ? fMaxBl : 0);
            if (fDeadline != null && !fDeadline.isBlank()) {
                ps.setDate(12, java.sql.Date.valueOf(fDeadline));
            } else {
                ps.setNull(12, java.sql.Types.DATE);
            }
            ps.setInt(13, fOpenings != null ? fOpenings : 1);
            ps.setString(14, fJobStatus);
            return ps;
        }, keyHolder);

        Number generatedKey = keyHolder.getKey();
        int newId = generatedKey != null ? generatedKey.intValue() : 0;

        Map<String, Object> created = getJobById(newId);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateJob(@PathVariable("id") int id, @RequestBody Map<String, Object> payload) {
        List<Map<String, Object>> existing = jdbcTemplate.queryForList("SELECT * FROM jobs WHERE job_id = ?", id);
        if (existing.isEmpty()) {
            throw new ResourceNotFoundException("Job with ID " + id + " was not found.");
        }
        Map<String, Object> current = existing.get(0);

        Integer companyId = getIntWithDefault(payload, "companyId", "company_id", (Integer) current.get("company_id"));
        String jobTitle = getStrWithDefault(payload, "jobTitle", "job_title", (String) current.get("job_title"));
        String jobDesc = getStrWithDefault(payload, "jobDescription", "job_description", (String) current.get("job_description"));
        String jobType = getStrWithDefault(payload, "jobType", "job_type", (String) current.get("job_type"));
        String empType = getStrWithDefault(payload, "employmentType", "employment_type", (String) current.get("employment_type"));
        String workLocation = getStrWithDefault(payload, "workLocation", "work_location", (String) current.get("work_location"));

        Double minSalary = getDouble(payload, "minSalary", "min_salary");
        if (minSalary == null && current.get("min_salary") != null) {
            minSalary = ((Number) current.get("min_salary")).doubleValue();
        }
        Double maxSalary = getDouble(payload, "maxSalary", "max_salary");
        if (maxSalary == null && current.get("max_salary") != null) {
            maxSalary = ((Number) current.get("max_salary")).doubleValue();
        }

        if (minSalary != null && maxSalary != null && minSalary > maxSalary) {
            throw new BadRequestException("Minimum salary cannot exceed maximum salary.");
        }

        String reqQual = getStrWithDefault(payload, "requiredQualification", "required_qualification", (String) current.get("required_qualification"));
        Double minPercentage = getDouble(payload, "minimumPercentage", "minimum_percentage");
        if (minPercentage == null && current.get("minimum_percentage") != null) {
            minPercentage = ((Number) current.get("minimum_percentage")).doubleValue();
        }
        Integer maxBacklogs = getIntWithDefault(payload, "maximumBacklogs", "maximum_backlogs", (Integer) current.get("maximum_backlogs"));
        String deadlineStr = getStr(payload, "applicationDeadline", "application_deadline");
        Integer openings = getIntWithDefault(payload, "openings", "openings", (Integer) current.get("openings"));
        String jobStatus = getStrWithDefault(payload, "jobStatus", "job_status", (String) current.get("job_status"));

        String updateSql = "UPDATE jobs SET company_id = ?, job_title = ?, job_description = ?, job_type = ?, " +
                "employment_type = ?, work_location = ?, min_salary = ?, max_salary = ?, required_qualification = ?, " +
                "minimum_percentage = ?, maximum_backlogs = ?, application_deadline = ?, openings = ?, job_status = ?, " +
                "updated_at = NOW() WHERE job_id = ?";

        java.sql.Date sqlDeadline = null;
        if (deadlineStr != null && !deadlineStr.isBlank()) {
            sqlDeadline = java.sql.Date.valueOf(deadlineStr);
        } else if (current.get("application_deadline") != null) {
            sqlDeadline = java.sql.Date.valueOf(current.get("application_deadline").toString());
        }

        jdbcTemplate.update(updateSql,
                companyId, jobTitle, jobDesc, jobType, empType, workLocation,
                minSalary != null ? BigDecimal.valueOf(minSalary) : null,
                maxSalary != null ? BigDecimal.valueOf(maxSalary) : null,
                reqQual,
                minPercentage != null ? BigDecimal.valueOf(minPercentage) : null,
                maxBacklogs != null ? maxBacklogs : 0,
                sqlDeadline,
                openings != null ? openings : 1,
                jobStatus,
                id);

        Map<String, Object> updated = getJobById(id);
        return ResponseEntity.ok(updated);
    }
}
