package com.kiranacademy.rpms;

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
import static com.kiranacademy.rpms.util.RequestUtils.getStr;
import static com.kiranacademy.rpms.util.RequestUtils.getStrWithDefault;

@RestController
@RequestMapping("/api/companies")
public class CompanyController {

    private final JdbcTemplate jdbcTemplate;

    public CompanyController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public List<Map<String, Object>> getCompanies(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String companyName,
            @RequestParam(required = false) String industry,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String headquarters,
            @RequestParam(required = false) String status) {

        StringBuilder sql = new StringBuilder(
                "SELECT c.*, (SELECT COUNT(*) FROM jobs j WHERE j.company_id = c.company_id) AS total_jobs " +
                "FROM companies c WHERE 1=1");
        List<Object> params = new ArrayList<>();

        String searchName = name != null && !name.isBlank() ? name : companyName;
        if (searchName != null && !searchName.isBlank()) {
            sql.append(" AND LOWER(c.company_name) LIKE ?");
            params.add("%" + searchName.toLowerCase().trim() + "%");
        }

        if (industry != null && !industry.isBlank()) {
            sql.append(" AND (LOWER(c.industry) = ? OR LOWER(c.industry) LIKE ?)");
            params.add(industry.toLowerCase().trim());
            params.add("%" + industry.toLowerCase().trim() + "%");
        }

        String searchLoc = location != null && !location.isBlank() ? location : headquarters;
        if (searchLoc != null && !searchLoc.isBlank()) {
            sql.append(" AND LOWER(c.headquarters) LIKE ?");
            params.add("%" + searchLoc.toLowerCase().trim() + "%");
        }

        if (status != null && !status.isBlank()) {
            sql.append(" AND LOWER(c.status) = ?");
            params.add(status.toLowerCase().trim());
        }

        sql.append(" ORDER BY c.company_id ASC");

        return jdbcTemplate.queryForList(sql.toString(), params.toArray());
    }

    @GetMapping("/{id}")
    public Map<String, Object> getCompanyById(@PathVariable("id") int id) {
        List<Map<String, Object>> list = jdbcTemplate.queryForList("SELECT * FROM companies WHERE company_id = ?", id);
        if (list.isEmpty()) {
            throw new ResourceNotFoundException("Company with ID " + id + " was not found.");
        }
        Map<String, Object> company = new HashMap<>(list.get(0));

        // Get jobs associated with this company
        List<Map<String, Object>> jobs = jdbcTemplate.queryForList(
                "SELECT * FROM jobs WHERE company_id = ? ORDER BY created_at DESC", id);
        company.put("jobs", jobs);

        return company;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createCompany(@RequestBody Map<String, Object> payload) {
        String companyName = getStr(payload, "companyName", "company_name");
        if (companyName == null || companyName.isBlank()) {
            throw new BadRequestException("Company name is required.");
        }

        String industry = getStr(payload, "industry");
        String website = getStr(payload, "website");
        String contactPerson = getStr(payload, "contactPerson", "contact_person");
        String contactEmail = getStr(payload, "contactEmail", "contact_email");
        String contactPhone = getStr(payload, "contactPhone", "contact_phone");
        String headquarters = getStr(payload, "headquarters", "location");
        String companyDescription = getStr(payload, "companyDescription", "company_description");
        String status = getStrWithDefault(payload, "status", "status", "ACTIVE");

        String insertSql = "INSERT INTO companies (company_name, industry, website, contact_person, contact_email, " +
                "contact_phone, headquarters, company_description, status, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())";

        KeyHolder keyHolder = new GeneratedKeyHolder();
        final String fStatus = status;

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, companyName);
            ps.setString(2, industry);
            ps.setString(3, website);
            ps.setString(4, contactPerson);
            ps.setString(5, contactEmail);
            ps.setString(6, contactPhone);
            ps.setString(7, headquarters);
            ps.setString(8, companyDescription);
            ps.setString(9, fStatus);
            return ps;
        }, keyHolder);

        Number generatedKey = keyHolder.getKey();
        int newId = generatedKey != null ? generatedKey.intValue() : 0;

        Map<String, Object> created = getCompanyById(newId);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateCompany(@PathVariable("id") int id, @RequestBody Map<String, Object> payload) {
        List<Map<String, Object>> existing = jdbcTemplate.queryForList("SELECT * FROM companies WHERE company_id = ?", id);
        if (existing.isEmpty()) {
            throw new ResourceNotFoundException("Company with ID " + id + " was not found.");
        }
        Map<String, Object> current = existing.get(0);

        String companyName = getStrWithDefault(payload, "companyName", "company_name", (String) current.get("company_name"));
        String industry = getStrWithDefault(payload, "industry", "industry", (String) current.get("industry"));
        String website = getStrWithDefault(payload, "website", "website", (String) current.get("website"));
        String contactPerson = getStrWithDefault(payload, "contactPerson", "contact_person", (String) current.get("contact_person"));
        String contactEmail = getStrWithDefault(payload, "contactEmail", "contact_email", (String) current.get("contact_email"));
        String contactPhone = getStrWithDefault(payload, "contactPhone", "contact_phone", (String) current.get("contact_phone"));
        String headquarters = getStrWithDefault(payload, "headquarters", "location", (String) current.get("headquarters"));
        String companyDescription = getStrWithDefault(payload, "companyDescription", "company_description", (String) current.get("company_description"));
        String status = getStrWithDefault(payload, "status", "status", (String) current.get("status"));

        String updateSql = "UPDATE companies SET company_name = ?, industry = ?, website = ?, contact_person = ?, " +
                "contact_email = ?, contact_phone = ?, headquarters = ?, company_description = ?, status = ? " +
                "WHERE company_id = ?";

        jdbcTemplate.update(updateSql, companyName, industry, website, contactPerson,
                contactEmail, contactPhone, headquarters, companyDescription, status, id);

        Map<String, Object> updated = getCompanyById(id);
        return ResponseEntity.ok(updated);
    }
}
