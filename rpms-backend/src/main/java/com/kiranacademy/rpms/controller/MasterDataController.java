package com.kiranacademy.rpms.controller;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class MasterDataController {

    private final JdbcTemplate jdbcTemplate;

    public MasterDataController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/skills")
    public List<Map<String, Object>> getSkills() {
        return jdbcTemplate.queryForList("SELECT skill_id, skill_name, category, is_active FROM skills ORDER BY category, skill_name");
    }

    @GetMapping("/qualifications")
    public List<Map<String, Object>> getQualifications() {
        return jdbcTemplate.queryForList("SELECT qualification_id, qualification_name, description, is_active, created_at FROM qualifications ORDER BY qualification_id");
    }

    @GetMapping("/roles")
    public List<Map<String, Object>> getRoles() {
        return jdbcTemplate.queryForList("SELECT role_id, role_code, role_name, is_active, created_at FROM roles ORDER BY role_id");
    }
}
