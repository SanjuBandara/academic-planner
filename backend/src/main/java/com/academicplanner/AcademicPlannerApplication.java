package com.academicplanner;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main entry point for the Academic Planner backend application.
 *
 * Architecture:
 *   React Frontend → Spring Boot REST API → Spring Security → Service Layer → JPA → PostgreSQL
 *
 * Authentication: HTTP Session-based (no JWT).
 * The browser stores a session cookie after login; subsequent requests are authenticated
 * via the session stored on the server side.
 */
@SpringBootApplication
public class AcademicPlannerApplication {

    public static void main(String[] args) {
        SpringApplication.run(AcademicPlannerApplication.class, args);
    }
}
