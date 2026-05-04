package com.samadhanai.samadhanai.Config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI samadhanAiOpenAPI() {
        return new OpenAPI()
                .info(buildApiInfo())
                .servers(buildServers())
                .tags(buildTags())
                // ✅ JWT Auth support in Swagger UI
                .addSecurityItem(new SecurityRequirement().addList("Bearer Auth"))
                .components(new Components()
                        .addSecuritySchemes("Bearer Auth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Login karke token lo, yahan paste karo")));
    }

    // ─── API Info ─────────────────────────────────────────
    private Info buildApiInfo() {
        return new Info()
                .title("SamadhanAI — Civic Complaint API")
                .version("1.0.0")
                .description("""
                        ## SamadhanAI — AI-Powered Civic Complaint System
                        
                        A civic complaint management platform for Indian citizens
                        that uses AI to detect problems, verify photos, and send
                        complaints directly to government departments.
                        
                        ### All 8 Features:
                        - **Feature 1** → Smart Complaint Submission + AI Photo Analysis + Preview Modal
                        - **Feature 2** → Fake/AI Photo Detection (4-layer verification)
                        - **Feature 3** → Auto Email to Government Department (with photo attached)
                        - **Feature 4** → Public Transparency Dashboard
                        - **Feature 5** → User Auth System (JWT) + Profile + Admin Panel
                        - **Feature 6** → Track Page — Edit + Extra Photos + Send + WhatsApp Share
                        - **Feature 7** → Status Change Email to Citizen
                        - **Feature 8** → Auto Reminder Email to Govt (15 din baad)
                        
                        ### How to Test:
                        1. Register via `POST /api/auth/register`
                        2. Login via `POST /api/auth/login` → copy token
                        3. Click **Authorize** button → paste token
                        4. Submit complaint via `POST /api/complaints`
                        5. Track via `GET /api/complaints/{referenceId}`
                        6. View dashboard via `GET /api/dashboard/full`
                        """)
                .contact(new Contact()
                        .name("SamadhanAI Team")
                        .email("support@samadhanai.in"))
                .license(new License()
                        .name("MIT License"));
    }

    // ─── Servers ──────────────────────────────────────────
    private List<Server> buildServers() {
        return List.of(
                new Server()
                        .url("http://localhost:8080")
                        .description("Local Development Server")
        );
    }

    // ─── Tags ─────────────────────────────────────────────
    private List<Tag> buildTags() {
        return List.of(
                new Tag()
                        .name("Auth")
                        .description("Register, Login, JWT token — Feature 5"),

                new Tag()
                        .name("Complaint")
                        .description("""
                                Submit, Track, Edit, Extra Photos, Send to Dept
                                POST /api/complaints              — Submit with photo
                                GET  /api/complaints/{id}         — Track by reference ID
                                PUT  /api/complaints/{id}/edit    — Edit type + description
                                POST /api/complaints/{id}/photos  — Upload extra photos
                                POST /api/complaints/{id}/send    — Send to department
                                PUT  /api/complaints/{id}/status  — Update status (Admin)
                                """),

                new Tag()
                        .name("Dashboard")
                        .description("""
                                Public transparency dashboard — Feature 4
                                GET /api/dashboard/stats       — Overall stats
                                GET /api/dashboard/ward-stats  — Ward-wise breakdown
                                GET /api/dashboard/ignored     — 30+ days ignored
                                GET /api/dashboard/dept-scores — Department scores
                                GET /api/dashboard/full        — All data in one call
                                """),

                new Tag()
                        .name("User")
                        .description("Profile, My complaints, Update account — Feature 5"),

                new Tag()
                        .name("Admin")
                        .description("All users, All complaints, Platform stats — Feature 5")
        );
    }
}