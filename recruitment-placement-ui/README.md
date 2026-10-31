# Recruitment & Placement Management System — UI Prototype

## Project Title
**Recruitment & Placement Management System — Technology-Neutral HTML/CSS Prototype**

---

## Project Overview
The **Recruitment & Placement Management System** is designed to manage the end-to-end recruitment and placement lifecycle of candidates within an educational training institute or placement organization. 

The system centralizes information about:
- Candidate profiles and academic backgrounds
- Employer / company master data
- Job openings and eligibility criteria
- Applications and candidate-job tracking
- Multi-round interview coordination
- Selection, offer letters, joining status, and final placement confirmation
- Executive reports, conversion metrics, and placement analytics

This HTML5 and CSS3 user interface prototype provides the complete visual and interactive model for how the web-based version of the system operates. It directly implements the specifications outlined in the **Recruitment & Placement Management System — UI Prototype Specification Document** and reflects the operational workflow described in the **Java + MySQL Project Abstract**.

---

## Technologies Used
- **HTML5:** Semantic markup, native form controls (inputs, dropdowns, radio buttons, textareas, date/time pickers), tables, badges, and layout structure.
- **CSS3:** Custom stylesheet (`css/style.css`) implementing the primary blue interface theme, responsive typography, spacing, flexbox and grid layouts, styled tables with alternating rows, status badges, and mobile-responsive breakpoints.
- **Pure Static Architecture:** Strictly zero JavaScript, zero backend frameworks, zero external CDN dependencies, and zero database connectors. Operates locally in any modern web browser.

---

## Project Folder Structure
```
recruitment-placement-ui/
│
├── login.html                  # Page 1: Entry point & user role selection
├── dashboard.html              # Page 2: High-level recruitment summary & funnel
├── candidates.html             # Page 3: Candidate search, filtering & directory
├── candidate-form.html         # Page 4: Candidate profile entry & update form
├── candidate-details.html      # Page 5: 360-degree candidate profile view
├── companies.html              # Page 6: Company master management & entry form
├── jobs.html                   # Page 7: Job openings list & filter panel
├── job-form.html               # Page 8: Job mandate & eligibility criteria setup
├── applications.html           # Page 9: Candidate-job application pipeline
├── interviews.html             # Page 10: Interview round scheduling & status
├── placement-process.html      # Page 11: Feedback, offers, joining & placement
├── reports.html                # Page 12: Management KPI insights & funnel analysis
│
├── css/
│   └── style.css               # Shared technology-neutral stylesheet
│
├── images/
│   └── logo.png                # System brand logo asset
│
└── README.md                   # Prototype documentation and execution guide
```

---

## Instructions for Opening the Website Locally
1. Extract the `recruitment-placement-ui.zip` archive if compressed, or open the `recruitment-placement-ui/` directory directly.
2. No web server (Apache, Nginx, Tomcat, Live Server) or database (MySQL) is required.
3. Open any standard desktop or laptop web browser (Google Chrome, Mozilla Firefox, Microsoft Edge, Safari, Opera).
4. Double-click **`login.html`** or right-click and choose **"Open with"** -> your browser.
5. In `login.html`, click the **"Login"** button to enter the main system **`dashboard.html`**.
6. Use the left sidebar menu or page action buttons to navigate across all 12 prototype pages.

---

## List of Implemented Pages

1. **Login Page (`login.html`)**
   - Entry point featuring Kiran Academy branding, system title, and role selector (Admin, Recruiter, Placement Officer).
   - Demonstrates sample display credentials (`recruiter@demo.com` | `Recruiter`).

2. **Dashboard (`dashboard.html`)**
   - 8 summary KPI cards: Total Candidates (2,450), Active Candidates (1,820), Companies (42), Open Jobs (28), Applications (1,200), Interviews (14 Today), Selected (210), Placed (685).
   - Recruitment Funnel visual breakdown, Upcoming Interviews, Recent Job Openings, Recent Placements, and Quick Action buttons.

3. **Candidate List (`candidates.html`)**
   - Multi-criteria filter panel (Candidate ID, Name, Mobile, Email, Technology, Passing Year, Qualification, Placement Status).
   - Comprehensive candidate results table with 10 sample records, pagination, and direct action links to View Details and Edit Profile.

4. **Candidate Add/Edit Form (`candidate-form.html`)**
   - 4 form sections: Personal Information, Academic Information, Skills, and Placement Preferences.
   - Sample display data for Rahul Patil (BE, 2025, 68%, Java, SQL, Pune).
   - Visual display of candidate form validation messages.

5. **Candidate Details (`candidate-details.html`)**
   - 360-degree candidate profile header with status badge.
   - Comprehensive personal and academic cards, skills competency, complete applications history table, interview history table, and offer/placement tracking.

6. **Companies (`companies.html`)**
   - Company search and filter panel.
   - Company master registration form with 9 required fields (Company Name, Industry, Website, Location, HR Name, HR Email, HR Mobile, Company Type, Status).
   - Registered employer table with 6 realistic enterprise partners and links to open jobs.

7. **Job Openings (`jobs.html`)**
   - Filter panel (Job Code, Company, Technology, Location, Job Status, Employment Type).
   - Job summary metrics and 8 detailed mandate records with real-time status badges and links to job editing and candidate applications.

8. **Create/Edit Job (`job-form.html`)**
   - 5 form sections: Basic Information, Compensation, Eligibility Criteria, Skills, and Important Dates.
   - Demonstration of job validation criteria (positive openings, deadline after post date, minimum package not exceeding maximum package).

9. **Applications (`applications.html`)**
   - Application filters, pipeline summary cards, and 12 applicant records across partner companies.
   - Eligibility status badges and demonstration of duplicate application and eligibility failure messages.

10. **Interviews (`interviews.html`)**
    - Daily summary metrics and interview scheduling form (Candidate, Company, Job, Round, Date, Time, Mode, Interviewer Name).
    - Demonstration of past-date interview validation rules and 8 interview schedule records.

11. **Selection, Offer & Placement Process (`placement-process.html`)**
    - Candidate and job summary banner.
    - 3 post-interview tabs: Tab 1 (Interview Feedback with 1-5 rating scales), Tab 2 (Offer Details), and Tab 3 (Joining / Placement confirmation).
    - Historical placement pipeline log with standard offer and joining statuses.

12. **Reports (`reports.html`)**
    - Filter criteria, 5 key KPI cards (Applications, Interviews, Selected, Joined, Placement %), and 6 report sections: Recruitment Funnel, Company-wise Placement, Technology-wise Placement, Recruiter Performance, Pending Interviews, and Open Job Aging.

---

## Known Limitations of the Static Frontend
- **No Database Persistence:** The prototype uses hardcoded static HTML sample records. Form submissions (such as saving candidates, scheduling interviews, or updating offers) do not persist changes to a database.
- **No Real Authentication:** The login screen is a prototype representation of role-based access; it does not validate passwords against a user directory or manage server-side user sessions.
- **Static Validation Display:** Validation messages and field constraints are demonstrated visually using HTML5 attributes and inline prototype callouts rather than a dynamic client-side JavaScript validation library.
- **Simulated Actions:** Actions such as "Check Eligibility", "Shortlist", "Reject", and "Mark No Show" are represented visually using native HTML buttons and links.
