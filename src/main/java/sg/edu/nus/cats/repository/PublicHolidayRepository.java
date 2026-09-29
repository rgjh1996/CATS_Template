package sg.edu.nus.cats.repository;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import sg.edu.nus.cats.model.PublicHoliday;

public interface PublicHolidayRepository extends JpaRepository<PublicHoliday, Long> {
  List<PublicHoliday> findByHolidayDateBetween(LocalDate from, LocalDate to);
}
