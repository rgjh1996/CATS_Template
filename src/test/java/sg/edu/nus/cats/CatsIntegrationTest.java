package sg.edu.nus.cats;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import sg.edu.nus.cats.config.WebConfig;
import sg.edu.nus.cats.dto.form.ApplicationForm;
import sg.edu.nus.cats.exception.*;
import sg.edu.nus.cats.model.*;
import sg.edu.nus.cats.model.enums.*;
import sg.edu.nus.cats.repository.*;
import sg.edu.nus.cats.service.*;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:cats-test;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "cats.seed=false"
    })
@AutoConfigureMockMvc
class CatsIntegrationTest {
  @TestConfiguration
  static class FixedTime {
    @Bean
    @Primary
    Clock testClock() {
      return Clock.fixed(Instant.parse("2026-09-01T00:00:00Z"), ZoneId.of("Asia/Singapore"));
    }
  }

  @Autowired UserRepository users;
  @Autowired CourseApplicationRepository applications;
  @Autowired AnnualEntitlementRepository entitlementRepo;
  @Autowired PublicHolidayRepository holidays;
  @Autowired CourseApplicationService service;
  @Autowired ApprovalService approval;
  @Autowired EntitlementService entitlements;
  @Autowired TrainingCalendarService calendar;
  @Autowired AuthenticationService authentication;
  @Autowired PasswordEncoder encoder;
  @Autowired MockMvc mvc;
  User alice, bob, charlie, other, admin;

  @BeforeEach
  void setup() {
    applications.deleteAll();
    entitlementRepo.deleteAll();
    holidays.deleteAll();
    for (var u : users.findAll()) {
      u.setManager(null);
      users.save(u);
    }
    users.deleteAll();
    bob = user("bob", Set.of(Role.EMPLOYEE, Role.MANAGER), null);
    other = user("other", Set.of(Role.EMPLOYEE, Role.MANAGER), null);
    alice = user("alice", Set.of(Role.EMPLOYEE), bob);
    charlie = user("charlie", Set.of(Role.EMPLOYEE), bob);
    admin = user("admin", Set.of(Role.ADMIN), null);
    for (var u : List.of(alice, bob, charlie, other))
      for (int year : List.of(2026, 2027)) {
        var e = new AnnualEntitlement();
        e.setEmployee(u);
        e.setYear(year);
        e.setBudgetLimit(new BigDecimal("2000"));
        e.setTrainingDayLimit(new BigDecimal("10"));
        entitlementRepo.save(e);
      }
  }

  User user(String name, Set<Role> roles, User manager) {
    var u = new User();
    u.setUsername(name);
    u.setName(name);
    u.setRoles(new HashSet<>(roles));
    u.setManager(manager);
    u.setDesignation(Designation.PROFESSIONAL);
    u.setPasswordHash(encoder.encode("DemoPass123!"));
    return users.save(u);
  }

  ApplicationForm form(String start, String end, String fee) {
    var f = new ApplicationForm();
    f.setCourseTitle("Java training");
    f.setTrainingProvider("ISS");
    f.setCategory(CourseCategory.EXTERNAL_COURSE);
    f.setStartDate(LocalDate.parse(start));
    f.setEndDate(LocalDate.parse(end));
    f.setCourseFee(new BigDecimal(fee));
    f.setJustification("Improve application design.");
    return f;
  }

  MockHttpSession session(User u) {
    var s = new MockHttpSession();
    s.setAttribute(
        "user", new WebConfig.SessionUser(u.getId(), u.getName(), Set.copyOf(u.getRoles())));
    WebConfig.csrf(s);
    return s;
  }

  @Test
  void submitUpdateApproveCancelAndRelease() {
    var a = service.submit(alice.getId(), form("2026-09-07", "2026-09-08", "600"));
    assertThat(a.getStatus()).isEqualTo(ApplicationStatus.APPLIED);
    assertThat(entitlements.getUsage(alice.getId(), 2026).remainingBudget())
        .isEqualByComparingTo("1400");
    var f = service.toForm(service.getDetails(alice.getId(), a.getId()));
    f.setCourseFee(new BigDecimal("700"));
    service.update(alice.getId(), a.getId(), f);
    assertThat(service.getDetails(alice.getId(), a.getId()).getStatus())
        .isEqualTo(ApplicationStatus.UPDATED);
    approval.approve(bob.getId(), a.getId(), "Relevant skills.");
    assertThat(entitlements.getUsage(alice.getId(), 2026).committedFees())
        .isEqualByComparingTo("700");
    assertThatThrownBy(() -> service.delete(alice.getId(), a.getId()))
        .isInstanceOf(BusinessException.class);
    service.cancel(alice.getId(), a.getId());
    assertThat(entitlements.getUsage(alice.getId(), 2026).remainingBudget())
        .isEqualByComparingTo("2000");
  }

  @Test
  void overlapAndBudgetFailuresLeaveNoRows() {
    service.submit(alice.getId(), form("2026-09-07", "2026-09-08", "1500"));
    assertThatThrownBy(() -> service.submit(alice.getId(), form("2026-09-08", "2026-09-09", "100")))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("overlaps");
    assertThatThrownBy(() -> service.submit(alice.getId(), form("2026-09-10", "2026-09-10", "501")))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("budget");
    assertThat(applications.count()).isEqualTo(1);
    service.submit(charlie.getId(), form("2026-09-07", "2026-09-08", "1500"));
    assertThat(applications.count()).isEqualTo(2);
  }

  @Test
  void calendarCountsWorkingDaysAndHalfDays() {
    var holiday = new PublicHoliday();
    holiday.setHolidayDate(LocalDate.parse("2026-09-08"));
    holiday.setDescription("Test holiday");
    holidays.save(holiday);
    assertThat(
            calendar
                .daysByYear(
                    LocalDate.parse("2026-09-04"),
                    LocalDate.parse("2026-09-09"),
                    DaySession.AM,
                    DaySession.PM)
                .get(2026))
        .isEqualByComparingTo("3");
    var f = form("2026-09-07", "2026-09-07", "0");
    f.setCategory(CourseCategory.INTERNAL_TRAINING);
    f.setEndSession(DaySession.AM);
    assertThat(service.submit(alice.getId(), f).getTrainingDays()).isEqualByComparingTo("0.5");
    assertThatThrownBy(
            () ->
                calendar.daysByYear(f.getStartDate(), f.getEndDate(), DaySession.PM, DaySession.AM))
        .isInstanceOf(BusinessException.class);
    assertThatThrownBy(
            () ->
                calendar.daysByYear(
                    LocalDate.parse("2026-09-08"),
                    LocalDate.parse("2026-09-09"),
                    DaySession.AM,
                    DaySession.PM))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void invalidCategoryDatesAndEntitlement() {
    assertThatThrownBy(() -> service.submit(alice.getId(), form("2026-09-01", "2026-09-01", "1")))
        .hasMessageContaining("future");
    assertThatThrownBy(() -> service.submit(alice.getId(), form("2026-09-05", "2026-09-07", "1")))
        .hasMessageContaining("working");
    assertThatThrownBy(() -> service.submit(alice.getId(), form("2026-09-10", "2026-09-09", "1")))
        .hasMessageContaining("ordered");
    assertThatThrownBy(() -> service.submit(alice.getId(), form("2026-09-07", "2026-09-25", "1")))
        .hasMessageContaining("training days");
    var f = form("2026-09-07", "2026-09-07", "10");
    f.setEndSession(DaySession.AM);
    assertThatThrownBy(() -> service.submit(alice.getId(), f)).hasMessageContaining("half days");
    f.setCategory(CourseCategory.INTERNAL_TRAINING);
    assertThatThrownBy(() -> service.submit(alice.getId(), f))
        .hasMessageContaining("zero course fee");
  }

  @Test
  void reasonsAndOwnershipEnforced() {
    var a = service.submit(alice.getId(), form("2026-09-07", "2026-09-07", "100"));
    assertThatThrownBy(() -> approval.approve(bob.getId(), a.getId(), " "))
        .isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> approval.reject(other.getId(), a.getId(), "No"))
        .isInstanceOf(NotFoundException.class);
    assertThatThrownBy(() -> service.getDetails(charlie.getId(), a.getId()))
        .isInstanceOf(NotFoundException.class);
    approval.reject(bob.getId(), a.getId(), "Not relevant.");
    assertThat(service.getDetails(alice.getId(), a.getId()).getStatus())
        .isEqualTo(ApplicationStatus.REJECTED);
    assertThat(entitlements.getUsage(alice.getId(), 2026).remainingBudget())
        .isEqualByComparingTo("2000");
    assertThatThrownBy(() -> approval.approve(bob.getId(), a.getId(), "Changed mind"))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void completionRequiresEndedApprovedCourseAndComment() {
    var a = service.submit(alice.getId(), form("2026-09-07", "2026-09-07", "100"));
    approval.approve(bob.getId(), a.getId(), "Yes");
    Long futureId = a.getId();
    assertThatThrownBy(() -> service.complete(alice.getId(), futureId, "Useful"))
        .hasMessageContaining("ended");
    a = applications.findById(a.getId()).orElseThrow();
    a.setStartDate(LocalDate.parse("2026-08-31"));
    a.setEndDate(a.getStartDate());
    applications.save(a);
    Long id = a.getId();
    assertThatThrownBy(() -> service.complete(alice.getId(), id, ""))
        .isInstanceOf(BusinessException.class);
    service.complete(alice.getId(), id, "Learned service design.");
    assertThat(service.getDetails(alice.getId(), id).getStatus())
        .isEqualTo(ApplicationStatus.COMPLETED);
  }

  @Test
  void crossYearDaysAndStartYearFee() {
    service.submit(alice.getId(), form("2026-12-31", "2027-01-04", "300"));
    assertThat(entitlements.getUsage(alice.getId(), 2026).pendingDays()).isEqualByComparingTo("1");
    assertThat(entitlements.getUsage(alice.getId(), 2027).pendingDays()).isEqualByComparingTo("2");
    assertThat(entitlements.getUsage(alice.getId(), 2027).pendingFees()).isEqualByComparingTo("0");
  }

  @Test
  void staleEditsAndTerminalHistory() {
    var a = service.submit(alice.getId(), form("2026-09-07", "2026-09-07", "100"));
    var f = service.toForm(a);
    service.update(alice.getId(), a.getId(), f);
    assertThatThrownBy(() -> service.update(alice.getId(), a.getId(), f))
        .hasMessageContaining("changed");
    service.delete(alice.getId(), a.getId());
    assertThat(service.getPersonalHistory(alice.getId(), 2026)).hasSize(1);
    assertThat(service.getPersonalHistory(alice.getId(), 2025)).isEmpty();
  }

  @Test
  void concurrentSubmissionsCannotOverspend() throws Exception {
    var pool = Executors.newFixedThreadPool(2);
    var gate = new CountDownLatch(1);
    var sequence = new java.util.concurrent.atomic.AtomicInteger();
    try {
      Callable<Boolean> task =
          () -> {
            gate.await();
            try {
              String date = sequence.getAndIncrement() == 0 ? "2026-09-07" : "2026-09-08";
              service.submit(alice.getId(), form(date, date, "1500"));
              return true;
            } catch (BusinessException e) {
              return false;
            }
          };
      var a = pool.submit(task);
      var b = pool.submit(task);
      gate.countDown();
      assertThat(List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(true, false);
      assertThat(applications.count()).isEqualTo(1);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void failedUpdateRollsBackAndManagerContextExcludesApplicant() {
    var original = service.submit(alice.getId(), form("2026-09-07", "2026-09-07", "2000"));
    var update = service.toForm(original);
    update.setCourseTitle("Must not persist");
    update.setCourseFee(new BigDecimal("2001"));
    assertThatThrownBy(() -> service.update(alice.getId(), original.getId(), update))
        .isInstanceOf(BusinessException.class);
    assertThat(service.getDetails(alice.getId(), original.getId()).getCourseTitle())
        .isEqualTo("Java training");
    var teammate = service.submit(charlie.getId(), form("2026-09-07", "2026-09-07", "100"));
    approval.approve(bob.getId(), teammate.getId(), "Relevant");
    var context = approval.getApprovalContext(bob.getId(), original.getId());
    assertThat(context.otherTeamApprovedCourses())
        .extracting(CourseApplication::getId)
        .containsExactly(teammate.getId());
    assertThat(context.annualUsage().get(2026).remainingBudget()).isEqualByComparingTo("0");
  }

  @Test
  void pagesRenderAndWorkflowThroughHttp() throws Exception {
    var s = session(alice);
    String token = WebConfig.csrf(s);
    mvc.perform(get("/employee").session(s))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Hello, alice")));
    mvc.perform(get("/applications/new").session(s)).andExpect(status().isOk());
    mvc.perform(
            post("/applications")
                .session(s)
                .param("_csrf", token)
                .param("courseTitle", "Web course")
                .param("trainingProvider", "ISS")
                .param("category", "EXTERNAL_COURSE")
                .param("startDate", "2026-09-07")
                .param("endDate", "2026-09-07")
                .param("startSession", "AM")
                .param("endSession", "PM")
                .param("courseFee", "100")
                .param("justification", "Learn MVC"))
        .andExpect(status().is3xxRedirection());
    Long id = applications.findAll().getFirst().getId();
    for (String path :
        List.of(
            "/applications",
            "/applications/" + id,
            "/applications/" + id + "/edit",
            "/applications/" + id + "/complete"))
      mvc.perform(get(path).session(s)).andExpect(status().isOk());
    var manager = session(bob);
    for (String path :
        List.of(
            "/manager/pending",
            "/manager/applications/" + id,
            "/manager/employees/" + alice.getId() + "/history"))
      mvc.perform(get(path).session(manager)).andExpect(status().isOk());
    mvc.perform(
            post("/manager/applications/" + id + "/approve")
                .session(manager)
                .param("_csrf", WebConfig.csrf(manager))
                .param("reason", "Approved for development"))
        .andExpect(status().is3xxRedirection());
    mvc.perform(get("/applications/" + id).session(s))
        .andExpect(
            content().string(org.hamcrest.Matchers.containsString("Approved for development")));
  }

  @Test
  void loginSecurityAndValidation() throws Exception {
    for (String path : List.of("/employee/login", "/admin/login"))
      mvc.perform(get(path)).andExpect(status().isOk());
    mvc.perform(get("/employee")).andExpect(redirectedUrl("/employee/login"));
    var s = new MockHttpSession();
    String token = WebConfig.csrf(s);
    mvc.perform(
            post("/employee/login")
                .session(s)
                .param("_csrf", token)
                .param("username", "alice")
                .param("password", "wrong"))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Invalid")));
    mvc.perform(
            post("/employee/login")
                .session(s)
                .param("_csrf", token)
                .param("username", "alice")
                .param("password", "DemoPass123!"))
        .andExpect(redirectedUrl("/employee"));
    mvc.perform(post("/applications").session(session(alice))).andExpect(status().isForbidden());
    mvc.perform(get("/manager/pending").session(session(alice))).andExpect(status().isForbidden());
    mvc.perform(get("/admin").session(session(admin))).andExpect(status().isOk());
    assertThatThrownBy(() -> authentication.authenticate("alice", "DemoPass123!", true))
        .isInstanceOf(BusinessException.class);
  }
}
