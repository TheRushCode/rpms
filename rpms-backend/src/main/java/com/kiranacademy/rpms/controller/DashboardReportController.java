package com.kiranacademy.rpms.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class DashboardReportController {

    private final JdbcTemplate jdbcTemplate;

    public DashboardReportController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/dashboard/stats")
    public Map<String, Object> getDashboardStats() {
        Map<String, Object> stats = new HashMap<>();

        // 8 Key KPI metrics
        Integer totalCandidates = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM candidates", Integer.class);
        Integer activeCandidates = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM candidates WHERE LOWER(profile_status) = 'active'", Integer.class);
        Integer totalCompanies = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM companies", Integer.class);
        Integer openJobs = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM jobs WHERE LOWER(job_status) = 'open'", Integer.class);
        Integer totalApplications = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM applications", Integer.class);
        Integer interviewsToday = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM interview_rounds WHERE DATE(scheduled_at) = CURDATE()", Integer.class);
        Integer selectedCandidates = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT candidate_id) FROM applications WHERE UPPER(application_status) IN ('SELECTED', 'OFFERED', 'PLACED')", Integer.class);
        Integer placedCandidates = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM candidates WHERE UPPER(placement_status) = 'PLACED'", Integer.class);

        stats.put("totalCandidates", totalCandidates != null ? totalCandidates : 0);
        stats.put("activeCandidates", activeCandidates != null ? activeCandidates : 0);
        stats.put("totalCompanies", totalCompanies != null ? totalCompanies : 0);
        stats.put("openJobs", openJobs != null ? openJobs : 0);
        stats.put("totalApplications", totalApplications != null ? totalApplications : 0);
        stats.put("interviewsToday", interviewsToday != null ? interviewsToday : 0);
        stats.put("selectedCandidates", selectedCandidates != null ? selectedCandidates : 0);
        stats.put("placedCandidates", placedCandidates != null ? placedCandidates : 0);

        // Recent Job Openings (Top 5)
        List<Map<String, Object>> recentJobs = jdbcTemplate.queryForList(
                "SELECT j.job_id, j.job_title, j.work_location, j.job_type, j.job_status, j.openings, c.company_name " +
                "FROM jobs j JOIN companies c ON j.company_id = c.company_id " +
                "ORDER BY j.created_at DESC LIMIT 5");
        stats.put("recentJobs", recentJobs);

        // Upcoming Interviews (Top 5)
        List<Map<String, Object>> upcomingInterviews = jdbcTemplate.queryForList(
                "SELECT ir.round_id, ir.round_name, ir.scheduled_at, ir.interview_mode, ir.round_status, " +
                "c.full_name AS candidate_name, comp.company_name " +
                "FROM interview_rounds ir " +
                "JOIN applications a ON ir.application_id = a.application_id " +
                "JOIN candidates c ON a.candidate_id = c.candidate_id " +
                "JOIN jobs j ON a.job_id = j.job_id " +
                "JOIN companies comp ON j.company_id = comp.company_id " +
                "ORDER BY ir.scheduled_at ASC LIMIT 5");
        stats.put("upcomingInterviews", upcomingInterviews);

        // Recent Placements (Top 5)
        List<Map<String, Object>> recentPlacements = jdbcTemplate.queryForList(
                "SELECT p.placement_id, p.placement_date, p.salary, p.placement_status, " +
                "c.full_name AS candidate_name, c.candidate_code, comp.company_name, j.job_title " +
                "FROM placements p " +
                "JOIN candidates c ON p.candidate_id = c.candidate_id " +
                "JOIN companies comp ON p.company_id = comp.company_id " +
                "JOIN jobs j ON p.job_id = j.job_id " +
                "ORDER BY p.created_at DESC LIMIT 5");
        stats.put("recentPlacements", recentPlacements);

        return stats;
    }

    @GetMapping("/dashboard/funnel")
    public Map<String, Object> getFunnel() {
        int totalCandidates = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM candidates", Integer.class);
        int applications = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM applications", Integer.class);
        int shortlisted = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM applications WHERE UPPER(application_status) IN ('SHORTLISTED', 'INTERVIEW_SCHEDULED', 'INTERVIEW_CLEARED', 'SELECTED', 'OFFERED', 'PLACED')",
                Integer.class);
        int interviews = jdbcTemplate.queryForObject("SELECT COUNT(DISTINCT application_id) FROM interview_rounds", Integer.class);
        int selected = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT candidate_id) FROM applications WHERE UPPER(application_status) IN ('SELECTED', 'OFFERED', 'PLACED')",
                Integer.class);
        int placed = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM candidates WHERE UPPER(placement_status) = 'PLACED'", Integer.class);

        Map<String, Object> result = new HashMap<>();
        result.put("totalCandidates", totalCandidates);
        result.put("applications", applications);
        result.put("shortlisted", shortlisted);
        result.put("interviews", interviews);
        result.put("selected", selected);
        result.put("placed", placed);

        // Calculate conversion percentages based on total candidates (avoid division by 0)
        double base = totalCandidates > 0 ? (double) totalCandidates : 1.0;
        result.put("applicationsPct", Math.round((applications / base) * 1000.0) / 10.0);
        result.put("shortlistedPct", Math.round((shortlisted / base) * 1000.0) / 10.0);
        result.put("interviewsPct", Math.round((interviews / base) * 1000.0) / 10.0);
        result.put("selectedPct", Math.round((selected / base) * 1000.0) / 10.0);
        result.put("placedPct", Math.round((placed / base) * 1000.0) / 10.0);

        return result;
    }

    @GetMapping("/reports/funnel")
    public Map<String, Object> getReportsFunnel() {
        return getFunnel();
    }

    @GetMapping("/reports/company-wise")
    public List<Map<String, Object>> getCompanyWiseReport() {
        String sql = "SELECT c.company_id, c.company_name, c.industry, " +
                "COUNT(DISTINCT j.job_id) AS total_jobs, " +
                "COUNT(DISTINCT a.application_id) AS total_applications, " +
                "COUNT(DISTINCT p.placement_id) AS total_placements, " +
                "COALESCE(AVG(p.salary), 0) AS average_package " +
                "FROM companies c " +
                "LEFT JOIN jobs j ON c.company_id = j.company_id " +
                "LEFT JOIN applications a ON j.job_id = a.job_id " +
                "LEFT JOIN placements p ON c.company_id = p.company_id " +
                "GROUP BY c.company_id, c.company_name, c.industry " +
                "ORDER BY total_placements DESC, total_jobs DESC";
        return jdbcTemplate.queryForList(sql);
    }

    @GetMapping("/reports/tech-wise")
    public List<Map<String, Object>> getTechWiseReport() {
        String sql = "SELECT COALESCE(primary_technology, 'Other') AS technology, " +
                "COUNT(*) AS total_candidates, " +
                "SUM(CASE WHEN UPPER(placement_status) = 'PLACED' THEN 1 ELSE 0 END) AS placed_candidates, " +
                "ROUND((SUM(CASE WHEN UPPER(placement_status) = 'PLACED' THEN 1 ELSE 0 END) * 100.0) / COUNT(*), 1) AS placement_percentage " +
                "FROM candidates " +
                "GROUP BY primary_technology " +
                "ORDER BY total_candidates DESC";
        return jdbcTemplate.queryForList(sql);
    }
}
