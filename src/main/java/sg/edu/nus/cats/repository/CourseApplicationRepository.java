package sg.edu.nus.cats.repository;

import java.time.LocalDate;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import sg.edu.nus.cats.model.CourseApplication;
import sg.edu.nus.cats.model.enums.ApplicationStatus;

public interface CourseApplicationRepository extends JpaRepository<CourseApplication, Long> {
  @Query("select a.employee.id from CourseApplication a where a.id=:id")
  Optional<Long> employeeId(Long id);

  @EntityGraph(attributePaths = {"employee", "employee.manager", "decidedBy"})
  @Query("select a from CourseApplication a where a.id=:id")
  Optional<CourseApplication> details(Long id);

  @EntityGraph(attributePaths = {"employee", "decidedBy"})
  @Query(
      "select a from CourseApplication a where a.employee.id=:employeeId and a.startDate<=:to and"
          + " a.endDate>=:from order by a.startDate desc, a.id desc")
  List<CourseApplication> history(Long employeeId, LocalDate from, LocalDate to);

  @EntityGraph(attributePaths = {"employee", "employee.manager"})
  @Query(
      "select a from CourseApplication a where a.employee.manager.id=:managerId and a.status in"
          + " :statuses order by a.employee.name,a.startDate")
  List<CourseApplication> pending(Long managerId, Collection<ApplicationStatus> statuses);

  @Query(
      "select count(a) from CourseApplication a where a.employee.id=:employeeId and a.status in"
          + " :statuses and a.startDate<=:to and a.endDate>=:from and (:excludeId is null or"
          + " a.id<>:excludeId)")
  long overlaps(
      Long employeeId,
      LocalDate from,
      LocalDate to,
      Collection<ApplicationStatus> statuses,
      Long excludeId);

  @EntityGraph(attributePaths = {"employee"})
  @Query(
      "select a from CourseApplication a where a.employee.manager.id=:managerId and"
          + " a.employee.id<>:employeeId and a.status=:status and a.startDate<=:to and"
          + " a.endDate>=:from order by a.startDate")
  List<CourseApplication> teamOverlaps(
      Long managerId, Long employeeId, LocalDate from, LocalDate to, ApplicationStatus status);
}
