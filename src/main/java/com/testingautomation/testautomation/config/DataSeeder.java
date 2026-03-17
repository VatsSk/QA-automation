//package com.testingautomation.testautomation.config;
//
//
//import com.testingautomation.testautomation.model.*;
//import com.testingautomation.testautomation.model.Module;
//import com.testingautomation.testautomation.repo.ModuleRepository;
//import com.testingautomation.testautomation.repo.ProjectRepository;
//import com.testingautomation.testautomation.repo.RunRepository;
//import com.testingautomation.testautomation.repo.UserRepository;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.boot.CommandLineRunner;
//import org.springframework.context.annotation.Profile;
//import org.springframework.stereotype.Component;
//
//import java.time.Instant;
//import java.time.temporal.ChronoUnit;
//import java.util.*;
//
//@Slf4j
//@Component
//@Profile("!test")   // don't seed during tests
//@RequiredArgsConstructor
//public class DataSeeder implements CommandLineRunner {
//
//    private final UserRepository userRepository;
//    private final ProjectRepository projectRepository;
//    private final ModuleRepository moduleRepository;
//    private final RunRepository runRepository;
//
//    @Override
//    public void run(String... args) {
//        if (userRepository.count() > 0) {
//            log.info("Seed data already present — skipping.");
//            return;
//        }
//
//        log.info("Seeding database with sample data...");
//
//        // ── Users ─────────────────────────────────────────────────────
//        User alice = saveUser("alice", "ADMIN");
//        User bob   = saveUser("bob",   "USER");
//        User carol = saveUser("carol", "USER");
//
//        // ── Project 1: E-Commerce ─────────────────────────────────────
//        Project ecom = saveProject("E-Commerce Platform", "Online shopping portal QA", alice.getId(),
//                "https://shop.example.com", List.of("ecommerce", "regression"));
//
//        Module checkout = saveModule(ecom.getId(), "Checkout Flow",    "End-to-end checkout",  alice.getId());
//        Module search   = saveModule(ecom.getId(), "Search & Filters", "Product search tests", bob.getId());
//        Module auth     = saveModule(ecom.getId(), "Authentication",   "Login/signup flows",   alice.getId());
//
//        // ── Project 2: Admin Portal ───────────────────────────────────
//        Project admin = saveProject("Admin Portal", "Internal admin panel QA", bob.getId(),
//                "https://admin.example.com", List.of("admin", "smoke"));
//
//        Module userMgmt = saveModule(admin.getId(), "User Management",  "CRUD for users",         bob.getId());
//        Module reports  = saveModule(admin.getId(), "Reports & Exports","Report generation tests", carol.getId());
//        Module settings = saveModule(admin.getId(), "Settings Panel",   "App config tests",        bob.getId());
//
//        // ── Runs for checkout module (mixed statuses) ──────────────────
//        saveRun(checkout.getId(), ecom.getId(), "Checkout Smoke Run",    alice.getId(), RunStatus.PASSED,
//                "smoke", "Final assert: order confirmation visible",
//                List.of("smoke", "fast"), scenariosFor(ScenarioType.URL, ScenarioType.MODAL), -1);
//
//        saveRun(checkout.getId(), ecom.getId(), "Checkout Full Regression", alice.getId(), RunStatus.FAILED,
//                "regression", "Final assert: receipt email received",
//                List.of("regression"), scenariosFor(ScenarioType.URL_NAV, ScenarioType.MODAL_NAV), -2);
//
//        saveRun(checkout.getId(), ecom.getId(), "Guest Checkout Test", bob.getId(), RunStatus.PARTIAL,
//                "smoke", "Final assert: guest order tracked",
//                List.of("smoke", "guest"), scenariosFor(ScenarioType.URL, ScenarioType.SEARCH_NAV), -3);
//
//        saveRun(checkout.getId(), ecom.getId(), "Coupon Code Validation", carol.getId(), RunStatus.DRAFT,
//                null, "Final assert: discount applied correctly",
//                List.of("regression", "coupon"), scenariosFor(ScenarioType.MODAL), -4);
//
//        // ── Runs for search module ─────────────────────────────────────
//        saveRun(search.getId(), ecom.getId(), "Product Search Smoke", bob.getId(), RunStatus.PASSED,
//                "smoke", "Final assert: results count > 0",
//                List.of("smoke"), scenariosFor(ScenarioType.SEARCH_NAV, ScenarioType.URL), -1);
//
//        saveRun(search.getId(), ecom.getId(), "Filter Combination Test", bob.getId(), RunStatus.RUNNING,
//                "regression", "Final assert: filtered results match criteria",
//                List.of("regression", "filters"), scenariosFor(ScenarioType.URL_NAV, ScenarioType.MODAL_NAV), -5);
//
//        // ── Runs for auth module ───────────────────────────────────────
//        saveRun(auth.getId(), ecom.getId(), "Login Happy Path",   alice.getId(), RunStatus.PASSED,
//                "smoke", "Final assert: dashboard loaded",
//                List.of("smoke", "login"), scenariosFor(ScenarioType.URL, ScenarioType.MODAL), -1);
//
//        saveRun(auth.getId(), ecom.getId(), "Signup Flow Test",   carol.getId(), RunStatus.FAILED,
//                "regression", "Final assert: welcome email sent",
//                List.of("regression", "signup"), scenariosFor(ScenarioType.URL_NAV), -6);
//
//        // ── Runs for admin modules ─────────────────────────────────────
//        saveRun(userMgmt.getId(), admin.getId(), "Create User Flow", bob.getId(), RunStatus.PASSED,
//                "smoke", "Final assert: user visible in list",
//                List.of("smoke", "admin"), scenariosFor(ScenarioType.MODAL, ScenarioType.URL), -1);
//
//        saveRun(reports.getId(), admin.getId(), "Report Export Test", carol.getId(), RunStatus.PARTIAL,
//                "regression", "Final assert: CSV downloaded",
//                List.of("regression", "export"), scenariosFor(ScenarioType.URL_NAV, ScenarioType.SEARCH_NAV), -7);
//
//        log.info("Seed data loaded: 2 projects, 6 modules, 10 runs.");
//    }
//
//    // ── Helpers ───────────────────────────────────────────────────────
//
//    private User saveUser(String username, String role) {
//        return userRepository.save(User.builder()
//                .username(username)
//                .password("stub")
//                .role(role)
//                .createdAt(Instant.now())
//                .updatedAt(Instant.now())
//                .build());
//    }
//
//    private Project saveProject(String name, String desc, String createdBy,
//                                String baseUrl, List<String> tags) {
//        return projectRepository.save(Project.builder()
//                .name(name)
//                .desc(desc)
//                .createdBy(createdBy)
//                .baseUrl(baseUrl)
//                .tags(tags)
//                .createdAt(Instant.now())
//                .updatedAt(Instant.now())
//                .build());
//    }
//
//    private Module saveModule(String projectId, String name, String desc, String createdBy) {
//        return moduleRepository.save(Module.builder()
//                .projectId(projectId)
//                .name(name)
//                .desc(desc)
//                .createdBy(createdBy)
//                .createdAt(Instant.now())
//                .updatedAt(Instant.now())
//                .build());
//    }
//
//    private void saveRun(String moduleId, String projectId, String runName,
//                         String createdBy, RunStatus status, String runType,
//                         String resultStatement, List<String> tags,
//                         List<Scenario> scenarios, int daysOffset) {
//
//        Instant ts = Instant.now().plus(daysOffset, ChronoUnit.DAYS);
//
//        Run run = Run.builder()
//                .runName(runName)
//                .createdBy(createdBy)
//                .projectId(projectId)
//                .moduleId(moduleId)
//                .status(status)
//                .runType(runType)
//                .resultStatement(resultStatement)   // top-level on Run
//                .scenariosList(scenarios)
//                .scenarioCount(scenarios.size())
//                .tags(tags)
//                .metadata(Map.of("env", "staging", "browser", "chrome"))
//                .createdAt(ts)
//                .updatedAt(ts)
//                .build();
//
//        runRepository.save(run);
//    }
//
//    private List<Scenario> scenariosFor(ScenarioType... types) {
//        List<Scenario> list = new ArrayList<>();
//        for (int i = 0; i < types.length; i++) {
//            ScenarioType t = types[i];
//            list.add(Scenario.builder()
//                    .id(UUID.randomUUID().toString())
//                    .type(t)
//                    .sequenceNo(i + 1)
//                    .url(t == ScenarioType.URL || t == ScenarioType.URL_NAV || t == ScenarioType.SEARCH_NAV
//                            ? "https://shop.example.com/page" + (i + 1)
//                            : null)
//                    .cssOpener(t == ScenarioType.MODAL || t == ScenarioType.MODAL_NAV
//                            ? "#btn-modal-" + (i + 1)
//                            : null)
//                    .value("sample-value-" + (i + 1))
//                    .statement("Verify scenario " + (i + 1) + " completes successfully via " + t.name())
//                    .status(ScenarioStatus.PENDING)
//                    .manualTestCases(sampleTestCases())
//                    .createdAt(Instant.now())
//                    .updatedAt(Instant.now())
//                    .build());
//        }
//        return list;
//    }
//
//    private List<TestCase> sampleTestCases() {
//        return List.of(
//                TestCase.builder()
//                        .testcaseId(UUID.randomUUID().toString())
//                        .name("TC-001: Positive case")
//                        .inputData(Map.of("username", "testuser", "password", "pass123"))
//                        .expectedResult("Page loads and shows dashboard")
//                        .status(ScenarioStatus.PENDING)
//                        .screenshotPaths(List.of())
//                        .build(),
//                TestCase.builder()
//                        .testcaseId(UUID.randomUUID().toString())
//                        .name("TC-002: Negative case")
//                        .inputData(Map.of("username", "wrong", "password", "wrong"))
//                        .expectedResult("Error message displayed")
//                        .status(ScenarioStatus.PENDING)
//                        .screenshotPaths(List.of())
//                        .build()
//        );
//    }
//}
