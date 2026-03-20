# Verid — Personal Finance Manager

A full-stack personal finance management application that helps users track expenses, manage financial goals, analyse bank statements, and get AI-powered financial insights.

---

## Table of Contents

- [Overview](#overview)
- [Tech Stack](#tech-stack)
- [Features](#features)
- [Project Structure](#project-structure)
- [Prerequisites](#prerequisites)
- [Getting Started](#getting-started)
  - [Backend Setup](#backend-setup)
  - [Frontend Setup](#frontend-setup)
- [Running the Application](#running-the-application)
- [API Endpoints](#api-endpoints)
- [Environment Configuration](#environment-configuration)
- [Database Schema](#database-schema)
- [Contributing](#contributing)

---

## Overview

Verid is a monorepo containing:

- **`src/`** — Spring Boot 4 REST API (backend)
- **`finance-frontend/`** — React + Vite SPA (frontend)

The application currently provides secure user authentication with JWT. The database schema is fully prepared to support transaction management, goal tracking, bank statement uploads, and AI chat history in upcoming iterations.

---

## Tech Stack

### Backend
| Technology | Version | Purpose |
|---|---|---|
| Java | 21 | Runtime |
| Spring Boot | 4.0.3 | Application framework |
| Spring Security | — | Authentication & authorization |
| Spring Data JPA / Hibernate | — | ORM / data access |
| PostgreSQL | — | Relational database |
| Flyway | — | Database migrations |
| JJWT | 0.12.6 | JWT token generation & validation |
| BCrypt | — | Password hashing |
| Apache PDFBox | 3.0.2 | PDF bank statement parsing |
| OpenCSV | 5.9 | CSV bank statement parsing |
| Lombok | — | Boilerplate reduction |
| Maven | 3.8+ | Build tool |

### Frontend
| Technology | Version | Purpose |
|---|---|---|
| React | 18.3.1 | UI library |
| Vite | 5.4.10 | Build tool & dev server |
| React Router DOM | 6.30.3 | Client-side routing |
| Tailwind CSS | 3.4.19 | Utility-first styling |
| ESLint | 9.13.0 | Code linting |

---

## Features

### Implemented
- ✅ User registration (name, email, password, preferred currency)
- ✅ User login with JWT token issuance
- ✅ Stateless JWT-based authentication
- ✅ BCrypt password hashing
- ✅ Landing page with hero, about, and footer sections
- ✅ Sign-up page with form validation

### Planned
- 📊 Financial goal tracking (short / mid / long-term)
- 💳 Transaction management with categorisation
- 📄 Bank statement upload (PDF & CSV)
- 📈 Monthly income / expense / savings summaries
- 🤖 AI-powered financial advisor chat
- 🗂️ Custom transaction categories with icons and colours

---

## Project Structure

```
personal-finance/
├── src/                                # Spring Boot backend
│   ├── main/
│   │   ├── java/com/financeapp/
│   │   │   ├── FinanceBackendApplication.java   # Application entry point
│   │   │   ├── config/
│   │   │   │   └── SecurityConfig.java          # Spring Security configuration
│   │   │   ├── controller/
│   │   │   │   └── AuthController.java          # Auth REST endpoints
│   │   │   ├── dto/auth/                        # Request / response DTOs
│   │   │   ├── model/
│   │   │   │   └── User.java                    # User JPA entity
│   │   │   ├── repository/
│   │   │   │   └── UserRepository.java          # User data access
│   │   │   ├── security/
│   │   │   │   ├── JwtAuthenticationFilter.java
│   │   │   │   └── JwtUtil.java
│   │   │   └── service/
│   │   │       ├── AuthService.java
│   │   │       └── UserDetailsServiceImpl.java
│   │   └── resources/
│   │       ├── application.properties.example   # Configuration template
│   │       └── db/migration/
│   │           └── V1__init_schema.sql          # Flyway schema migration
│   └── test/
│       └── java/com/financeapp/
│           └── FinanceBackendApplicationTests.java
├── finance-frontend/                   # React frontend
│   ├── src/
│   │   ├── App.jsx                     # Root component with routes
│   │   ├── main.jsx                    # React entry point
│   │   ├── api/
│   │   │   └── auth.js                 # HTTP client for auth endpoints
│   │   ├── pages/
│   │   │   ├── LandingPage.jsx
│   │   │   └── SignupPage.jsx
│   │   └── components/
│   │       ├── Navbar.jsx
│   │       ├── Hero.jsx
│   │       ├── About.jsx
│   │       └── Footer.jsx
│   ├── vite.config.js
│   ├── tailwind.config.js
│   └── package.json
├── pom.xml                             # Maven project descriptor
└── README.md
```

---

## Prerequisites

Before you begin, ensure you have the following installed:

- **Java 21+**
- **Maven 3.8+**
- **Node.js 16+** and **npm**
- **PostgreSQL** (running locally or accessible remotely)

---

## Getting Started

### Backend Setup

1. **Clone the repository**
   ```bash
   git clone https://github.com/nobinDas/personal-finance.git
   cd personal-finance
   ```

2. **Create your application properties**
   ```bash
   cp src/main/resources/application.properties.example src/main/resources/application.properties
   ```

3. **Edit `src/main/resources/application.properties`** and fill in your values:
   ```properties
   spring.datasource.url=jdbc:postgresql://localhost:5432/financeapp
   spring.datasource.username=YOUR_DB_USERNAME
   spring.datasource.password=YOUR_DB_PASSWORD
   app.jwt.secret=YOUR_BASE64_ENCODED_256BIT_SECRET
   app.jwt.expiration-ms=86400000
   ```

4. **Create the PostgreSQL database**
   ```sql
   CREATE DATABASE financeapp;
   ```
   Flyway will automatically apply the migration scripts on startup.

5. **Build the project**
   ```bash
   ./mvnw clean package -DskipTests   # Linux / macOS
   mvnw.cmd clean package -DskipTests # Windows
   ```

### Frontend Setup

1. **Navigate to the frontend directory**
   ```bash
   cd finance-frontend
   ```

2. **Install dependencies**
   ```bash
   npm install
   ```

---

## Running the Application

### Backend
```bash
# From the repository root
./mvnw spring-boot:run          # Linux / macOS
mvnw.cmd spring-boot:run        # Windows
```
The API server starts on **http://localhost:8080**.

### Frontend
```bash
# From finance-frontend/
npm run dev
```
The React app starts on **http://localhost:5173**.

### Frontend — Production Build
```bash
npm run build      # outputs to finance-frontend/dist/
npm run preview    # serve the production build locally
```

### Frontend — Linting
```bash
npm run lint
```

### Backend — Tests
```bash
./mvnw test
```

---

## API Endpoints

### Authentication

| Method | Endpoint | Description | Auth Required |
|---|---|---|---|
| `POST` | `/api/auth/register` | Register a new user | No |
| `POST` | `/api/auth/login` | Login and receive a JWT token | No |
| `GET` | `/actuator/health` | Application health check | No |

#### Register — `POST /api/auth/register`
```json
// Request body
{
  "name": "Jane Doe",
  "email": "jane@example.com",
  "password": "securePassword123",
  "currency": "USD"
}
```

#### Login — `POST /api/auth/login`
```json
// Request body
{
  "email": "jane@example.com",
  "password": "securePassword123"
}

// Response
{
  "token": "<JWT_TOKEN>"
}
```

All protected endpoints (to be added) expect the JWT in the `Authorization` header:
```
Authorization: Bearer <JWT_TOKEN>
```

---

## Environment Configuration

The `application.properties.example` file documents all configurable properties:

| Property | Description | Default |
|---|---|---|
| `server.port` | Backend port | `8080` |
| `spring.datasource.url` | PostgreSQL JDBC URL | — |
| `spring.datasource.username` | Database username | — |
| `spring.datasource.password` | Database password | — |
| `spring.jpa.hibernate.ddl-auto` | Hibernate DDL strategy | `validate` |
| `spring.flyway.enabled` | Enable Flyway migrations | `true` |
| `app.jwt.secret` | Base64-encoded 256-bit JWT secret | — |
| `app.jwt.expiration-ms` | JWT token validity in milliseconds | `86400000` (24 h) |

> ⚠️ **Never commit** `application.properties` — it is listed in `.gitignore`.

---

## Database Schema

The initial Flyway migration (`V1__init_schema.sql`) creates the following tables:

| Table | Description |
|---|---|
| `users` | User accounts (email, hashed password, name, currency) |
| `goals` | Financial goals with target amounts, deadlines, and status |
| `bank_statements` | Uploaded PDF/CSV statements |
| `transactions` | Individual credit / debit transactions with categorisation |
| `categories` | User-defined categories with icon and colour |
| `monthly_summaries` | Pre-computed monthly income, expenses, and net savings |
| `chat_history` | AI financial advisor conversation history |

---

## Contributing

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/your-feature-name`
3. Commit your changes: `git commit -m "feat: add your feature"`
4. Push to your fork: `git push origin feature/your-feature-name`
5. Open a Pull Request against `main`

Please make sure the backend tests pass (`./mvnw test`) and the frontend linter is clean (`npm run lint`) before submitting.
