# Kiran Academy — Recruitment & Placement Management System (RPMS)

An enterprise-grade Recruitment & Placement Management System designed to orchestrate the end-to-end recruitment, interview, and placement lifecycle for educational institutes and corporate training centers.

---

## 1. System Architecture

The project consists of two clean, decoupled components connecting to a MySQL 8.0 relational database:

```
Java + MySQL Project/
├── rpms-backend/                     # Spring Boot 4 / Java 21 REST API Backend
│   ├── Dockerfile                    # Multi-stage container build (JDK 21 -> JRE 21 non-root)
│   ├── .dockerignore                 # Container build exclusion rules
│   ├── pom.xml                       # Maven build descriptor & dependencies
│   ├── mvnw / mvnw.cmd               # Official Maven wrapper executables
│   ├── src/main/java/com/kiranacademy/rpms/
│   │   ├── CandidateController.java  # Candidate CRUD & profile management
│   │   ├── CompanyController.java    # Employer master management
│   │   ├── JobController.java        # Job openings & requirement requisitions
│   │   ├── TestController.java       # Diagnostic /api/test endpoint
│   │   ├── RpmsBackendApplication.java
│   │   ├── config/
│   │   │   └── WebCorsConfig.java    # Centralized CORS with Render & local origins
│   │   ├── controller/
│   │   │   ├── ApplicationController.java     # Job applications & status audits
│   │   │   ├── AuthController.java            # Session sign-in, /me, sign-out
│   │   │   ├── DashboardReportController.java # KPIs, recruitment funnels, analytics
│   │   │   ├── HealthController.java          # Production /api/health probe
│   │   │   ├── InterviewController.java       # Interview rounds & scorecards
│   │   │   ├── MasterDataController.java      # Skills, qualifications, roles
│   │   │   └── PlacementController.java       # Offers & candidate onboarding
│   │   ├── exception/                # Global exception handler & error DTOs
│   │   └── util/                     # Type-safe parameter parsing utilities
│   └── src/main/resources/
│       ├── application.properties    # Environment-variable driven properties
│       └── application-local.properties.example # Local secret template
│
├── recruitment-placement-ui/         # Vanilla HTML5, CSS3 & JavaScript Frontend
│   ├── css/style.css                 # Kiran Academy responsive design system
│   ├── images/logo.png               # Brand logo
│   ├── js/
│   │   ├── config.js                 # Dynamic runtime API endpoint configuration
│   │   └── api.js                    # Central async fetch client with XSS escaping
│   ├── login.html                    # User authentication screen
│   ├── dashboard.html                # Executive KPI metrics & recruitment funnel
│   ├── candidates.html               # Candidate directory with multi-filter search
│   ├── candidate-form.html           # Candidate registration & profile updater
│   ├── candidate-details.html        # 360-degree candidate profile view
│   ├── companies.html                # Corporate partner directory
│   ├── jobs.html                     # Job mandates & vacancy listings
│   ├── job-form.html                 # Job requisition editor
│   ├── applications.html             # Application pipeline & status tracker
│   ├── interviews.html               # Multi-round interview scheduler & feedback
│   ├── placement-process.html        # Evaluation, job offers & placement records
│   └── reports.html                  # Conversion funnels & corporate placement tables
│
├── render.yaml                       # Render Infrastructure-as-Code Blueprint
├── .gitignore                        # Git security rules ignoring secrets & targets
└── README.md                         # Complete operations runbook & deployment guide
```

---

## 2. Technology Stack

- **Backend:** Java 21, Spring Boot 4.0.x, Spring Data JPA / Hibernate, Spring JDBC (`JdbcTemplate`), Maven
- **Database:** MySQL 8.0 (`kiran_academy_rpms`, strictly preserving existing tables and sample data with `spring.jpa.hibernate.ddl-auto=none`)
- **Frontend:** Semantic HTML5, CSS3, Vanilla JavaScript (ES6+ `async`/`await` Fetch API)
- **Containerization:** Docker multi-stage build (Temurin 21 JDK -> Temurin 21 JRE, non-root user)
- **Deployment Platform:** Render (Docker Web Service for Backend + Static Site for Frontend)

---

## 3. Database Rules & Preservation

The system operates against the pre-existing `kiran_academy_rpms` database containing 17 schema tables:
`activity_logs`, `application_history`, `applications`, `candidate_skills`, `candidates`, `companies`, `interview_feedback`, `interview_rounds`, `job_skills`, `jobs`, `locations`, `offers`, `placements`, `qualifications`, `roles`, `skills`, `users`.

### Critical Database Integrity Rules:
- **Never Alter Schema:** `spring.jpa.hibernate.ddl-auto` is set to `none`. Tables, foreign keys, and indexes must not be modified or auto-generated.
- **Preserve Existing Records:** Existing candidate, company, job, and user data must remain intact.
- **Credential Hygiene:** Passwords and database credentials must never be committed to Git. `application.properties` uses environment variables with empty defaults, while local credentials reside in git-ignored `application-local.properties`.

---

## 4. Local Development Setup

### 4.1 Prerequisites
1. **JDK 21+** (`java -version`)
2. **MySQL Server 8.0+** running on `localhost:3306`
3. Modern Web Browser (Chrome, Firefox, Edge, Safari)

### 4.2 Local Database Configuration
Copy the template file to create your local overrides (this file is ignored by Git):
```bash
cd rpms-backend/src/main/resources
cp application-local.properties.example application-local.properties
```
Edit `application-local.properties` with your local MySQL password:
```properties
spring.datasource.password=YourLocalMySQLPassword
```

### 4.3 Starting the Backend
From the `rpms-backend` directory, run:
```bash
# On Windows:
.\mvnw.cmd spring-boot:run

# On Linux / macOS:
./mvnw spring-boot:run
```
The server will start on port `8080`:
```
Tomcat started on port 8080 (http) with context path '/'
Started RpmsBackendApplication in X.XXX seconds
```

### 4.4 Starting the Frontend
The frontend requires no build steps or Node.js server.
- Simply open `recruitment-placement-ui/login.html` directly in your browser, or serve it using any local static file server (e.g. VS Code Live Server or Python `python -m http.server 3000`).
- By default, `js/config.js` auto-detects `localhost`, `127.0.0.1`, and `file://` protocols and routes all API calls to `http://localhost:8080/api`.

Default login credentials:
- **Administrator:** Username: `admin` | Password: `password`
- **Placement Officer:** Username: `placement_officer` | Password: `password`
- **Interviewer:** Username: `interviewer` | Password: `password`

---

## 5. Health & Diagnostic Endpoints

The backend includes two dedicated diagnostic endpoints:

### 5.1 System Health Probe (`GET /api/health`)
Used by Render, container health checks, and uptime monitoring services.
- **Success Response (`200 OK`):**
  ```json
  {
    "status": "UP",
    "service": "rpms-backend",
    "timestamp": "2026-10-31T11:02:05.764Z",
    "database": "CONNECTED"
  }
  ```
- **Failure Response (`503 SERVICE_UNAVAILABLE`):**
  ```json
  {
    "status": "DOWN",
    "database": "DISCONNECTED",
    "error": "Database connection check failed"
  }
  ```
  *Note: Does not expose internal hostnames, ports, credentials, or sensitive exceptions.*

### 5.2 Test Endpoint (`GET /api/test`)
Returns plain text confirmation: `RPMS Backend is running successfully!`.

---

## 6. Git & GitHub Setup

The workspace is pre-configured with secure `.gitignore` files that exclude all build artifacts (`target/`), IDE configuration (`.idea/`, `.vscode/`), and local secrets (`application-local.properties`, `.env`).

### Step-by-Step GitHub Publish Commands:
Run these commands from the workspace root (`A:\kiran academy\Java + MySQL Project`):

```bash
# 1. Check git status to confirm no secrets are staged
git status

# 2. Stage all clean project files
git add .

# 3. Create initial commit
git commit -m "feat: complete RPMS backend, frontend, Docker, and Render configuration"

# 4. Set branch name to main
git branch -M main

# 5. Add your GitHub remote repository (replace with your GitHub repository URL)
git remote add origin https://github.com/YOUR_GITHUB_USERNAME/rpms-project.git

# 6. Push code to GitHub
git push -u origin main
```

---

## 7. Docker Containerization

The backend includes a production-ready, multi-stage `Dockerfile`:
- **Stage 1 (Builder):** Uses `eclipse-temurin:21-jdk-jammy`, copies the Maven wrapper and `pom.xml`, pre-caches dependencies, and compiles the fat JAR.
- **Stage 2 (Runtime):** Uses minimal `eclipse-temurin:21-jre-jammy`, creates a non-root system user (`rpmsuser` / UID `1001`), configures container memory limits (`-XX:MaxRAMPercentage=75.0`), and binds to dynamically assigned `$PORT`.

### 7.1 Building the Docker Image Locally
```bash
cd rpms-backend
docker build -t rpms-backend:latest .
```

### 7.2 Running the Container Locally
Connect the container to your MySQL database (replace values with your actual database coordinates):
```bash
docker run -d \
  --name rpms-backend-container \
  -p 8080:8080 \
  -e PORT=8080 \
  -e DB_URL="jdbc:mysql://host.docker.internal:3306/kiran_academy_rpms?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC" \
  -e DB_USERNAME="root" \
  -e DB_PASSWORD="YourDatabasePassword" \
  rpms-backend:latest
```

### 7.3 Verifying the Running Container
```bash
# Check container logs
docker logs rpms-backend-container

# Test health check endpoint
curl http://localhost:8080/api/health
```

---

## 8. Cloud Database Setup for Render

Render does not offer a native managed MySQL service (Render natively provides PostgreSQL). For production, use any managed MySQL 8.0 cloud provider:
- **Aiven for MySQL** (Free tier available, fully managed)
- **Railway** (MySQL database add-on)
- **PlanetScale / AWS RDS / DigitalOcean MySQL**

### 8.1 Exporting Local MySQL Schema & Data
Run `mysqldump` from your local terminal to create a migration dump:
```bash
mysqldump -u root -p --single-transaction --quick --routines --triggers kiran_academy_rpms > rpms_database_dump.sql
```
*(Enter your password when prompted; never embed passwords in bash history or scripts).*

### 8.2 Importing into Cloud MySQL
Once your cloud MySQL database is created, import the dump:
```bash
mysql -h <CLOUD_DB_HOST> -P <CLOUD_DB_PORT> -u <CLOUD_DB_USER> -p <CLOUD_DB_NAME> < rpms_database_dump.sql
```

---

## 9. Deployment on Render

### Option A: One-Click Blueprint Deployment (Recommended)
The repository includes a root `render.yaml` Blueprint specification.

1. Push your repository to GitHub.
2. In the [Render Dashboard](https://dashboard.render.com), click **New +** > **Blueprint**.
3. Select your repository. Render reads `render.yaml` and provisions:
   - `rpms-backend` (Docker Web Service)
   - `rpms-frontend` (Static Site)
4. When prompted by Render, fill in your cloud database credentials:
   - `DB_HOST`: Host of your cloud MySQL instance
   - `DB_PORT`: `3306` (or provider port)
   - `DB_NAME`: `kiran_academy_rpms`
   - `DB_USERNAME`: Database user
   - `DB_PASSWORD`: Database password
   - `CORS_ALLOWED_ORIGINS`: Your frontend URL (e.g., `https://rpms-frontend.onrender.com`)
5. Click **Apply**. Render will automatically build and launch both services.

---

### Option B: Manual Web Service & Static Site Deployment

#### Deploy Backend:
1. In Render Dashboard, click **New +** > **Web Service**.
2. Connect your GitHub repository.
3. Configure service settings:
   - **Name:** `rpms-backend`
   - **Region:** Any (e.g. Oregon)
   - **Runtime:** `Docker`
   - **Docker Context Directory:** `rpms-backend`
   - **Dockerfile Path:** `Dockerfile`
   - **Health Check Path:** `/api/health`
4. Under **Environment Variables**, add:
   | Key | Value |
   |---|---|
   | `PORT` | `8080` |
   | `DB_HOST` | `<your-cloud-mysql-host>` |
   | `DB_PORT` | `3306` |
   | `DB_NAME` | `kiran_academy_rpms` |
   | `DB_USERNAME` | `<your-cloud-mysql-username>` |
   | `DB_PASSWORD` | `<your-cloud-mysql-password>` |
   | `CORS_ALLOWED_ORIGINS` | `https://rpms-frontend.onrender.com` |
5. Click **Create Web Service**.

#### Deploy Frontend:
1. In Render Dashboard, click **New +** > **Static Site**.
2. Connect the same repository.
3. Configure settings:
   - **Name:** `rpms-frontend`
   - **Publish Directory:** `recruitment-placement-ui`
   - **Build Command:** *(Leave empty)*
4. Under **Redirects/Rewrites**:
   - Source: `/`
   - Destination: `/login.html`
   - Action: `Redirect`
5. Click **Create Static Site**.

---

## 10. Frontend Cloud Endpoint Configuration

The frontend dynamically resolves its API backend without requiring code changes:
- In `recruitment-placement-ui/js/config.js`, update `defaultProductionApiUrl`:
  ```javascript
  const defaultProductionApiUrl = 'https://YOUR-BACKEND-NAME.onrender.com/api';
  ```
- Or, on your deployed site, open browser DevTools Console and execute:
  ```javascript
  setRpmsApiUrl('https://YOUR-BACKEND-NAME.onrender.com/api');
  ```
  This immediately saves the target backend in `localStorage` and reloads the page.

---

## 11. Complete REST API Reference

All endpoints return standard JSON responses and status codes:

### 11.1 Diagnostics & Health
- `GET /api/health` — Container & database connection health probe (`200 OK` or `503 Unavailable`).
- `GET /api/test` — Quick runtime ping.

### 11.2 Authentication (`/api/auth`)
- `POST /api/auth/login` — Sign in with credentials, returns role and session object.
- `GET /api/auth/me` — Inspect currently authenticated session.
- `POST /api/auth/logout` — Terminate session.

### 11.3 Master Data (`/api`)
- `GET /api/skills` — List technical skills and categories.
- `GET /api/qualifications` — List educational degrees and qualifications.
- `GET /api/roles` — List user authorization roles.

### 11.4 Candidates (`/api/candidates`)
- `GET /api/candidates` — Filterable candidate directory (`name`, `technology`, `qualification`, `passingYear`, `placementStatus`).
- `GET /api/candidates/{id}` — 360-degree candidate profile with academic details and skill tags.
- `POST /api/candidates` — Register new candidate with unique candidate code generation.
- `PUT /api/candidates/{id}` — Update candidate demographics, skills, and status.
- `GET /api/candidates/{id}/applications` — View all applications by candidate.

### 11.5 Companies & Jobs (`/api/companies`, `/api/jobs`)
- `GET /api/companies` — List registered hiring partner companies.
- `GET /api/companies/{id}` — Retrieve company details and associated job listings.
- `POST /api/companies` — Register new corporate partner.
- `PUT /api/companies/{id}` — Update company contact details.
- `GET /api/jobs` — List jobs with filters for `title`, `location`, `status`, and `companyId`.
- `GET /api/jobs/{id}` — Retrieve detailed job opening specifications.
- `POST /api/jobs` — Create new job opening requisition.
- `PUT /api/jobs/{id}` — Update job description and hiring criteria.

### 11.6 Applications (`/api/applications`)
- `GET /api/applications` — List applications with candidate and job details.
- `GET /api/applications/{id}` — Retrieve application status history audit.
- `POST /api/applications` — Submit candidate application (prevents duplicate applications).
- `PUT /api/applications/{id}/status` — Transition application status (`APPLIED`, `SHORTLISTED`, `INTERVIEW_SCHEDULED`, `SELECTED`, `OFFERED`, `PLACED`, `REJECTED`).

### 11.7 Interviews (`/api/interviews`)
- `GET /api/interviews` — List scheduled/completed interviews with date and status filters.
- `GET /api/interviews/{roundId}` — Retrieve interview evaluation details.
- `POST /api/interviews/schedule` — Schedule an interview round.
- `POST /api/interviews/{roundId}/feedback` — Record scorecard ratings; auto-updates application status.

### 11.8 Offers & Placements (`/api/offers`, `/api/placements`)
- `GET /api/offers` — List job offers extended to candidates.
- `POST /api/offers` — Extended new offer with CTC and joining timeline.
- `PUT /api/offers/{id}/status` — Transition offer status (`ACCEPTED` auto-creates placement record).
- `GET /api/placements` — List verified placements and salary packages.
- `POST /api/placements` — Confirm candidate onboarding and reporting status.

### 11.9 Dashboard & Analytics (`/api/dashboard`, `/api/reports`)
- `GET /api/dashboard/stats` — Executive summary KPIs (Candidates, Open Jobs, Placements, Partners).
- `GET /api/dashboard/funnel` — End-to-end recruitment conversion funnel.
- `GET /api/reports/company-wise` — Employer hiring counts, openings, and package statistics.
- `GET /api/reports/tech-wise` — Domain-wise candidate pools and placement percentages.

---

## 12. Security & Troubleshooting Runbook

### 12.1 Common Issues & Solutions

| Issue | Root Cause | Solution |
|---|---|---|
| `SocketException: Permission denied` | Database port or host unreachable | Verify MySQL is running and accessible on host/port specified in `DB_HOST` or `DB_URL`. |
| `CORS Error in Browser` | Origin not allowed | Set `CORS_ALLOWED_ORIGINS=https://your-frontend.onrender.com` in backend environment variables. `*.onrender.com` is allowed by default. |
| `401 Unauthorized / Login Failed` | Invalid username or password | Use default credentials (`admin` / `password`). |
| `Unable to determine Dialect without JDBC metadata` | Database connection unreachable during startup | Ensure MySQL is accessible before starting the Spring Boot application. |
| `Render Web Service Port Timeout` | Port mismatch | Render provides `$PORT` dynamically; `application.properties` uses `server.port=${PORT:8080}` and binds `0.0.0.0`. |
