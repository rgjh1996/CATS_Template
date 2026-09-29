package sg.edu.nus.cats.service;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import sg.edu.nus.cats.exception.BusinessException;
import sg.edu.nus.cats.model.enums.DaySession;
import sg.edu.nus.cats.repository.PublicHolidayRepository;

@Service
public class TrainingCalendarService {
  private final PublicHolidayRepository holidays;

  public TrainingCalendarService(PublicHolidayRepository holidays) {
    this.holidays = holidays;
  }

  public Map<Integer, BigDecimal> daysByYear(
      LocalDate from, LocalDate to, DaySession start, DaySession end) {
    if (from == null || to == null || start == null || end == null || from.isAfter(to))
      throw new BusinessException("Choose an ordered course period and both sessions.");
    Set<LocalDate> excluded =
        holidays.findByHolidayDateBetween(from, to).stream()
            .map(h -> h.getHolidayDate())
            .collect(Collectors.toSet());
    if (!working(from, excluded) || !working(to, excluded))
      throw new BusinessException("Start and end dates must be working days.");
    Map<Integer, BigDecimal> result = new TreeMap<>();
    for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
      if (!working(d, excluded)) continue;
      BigDecimal days = BigDecimal.ONE;
      if (d.equals(from) && start == DaySession.PM) days = days.subtract(new BigDecimal("0.5"));
      if (d.equals(to) && end == DaySession.AM) days = days.subtract(new BigDecimal("0.5"));
      result.merge(d.getYear(), days, BigDecimal::add);
    }
    if (result.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add).signum() <= 0)
      throw new BusinessException("The course must include at least half a training day.");
    return result;
  }

  private boolean working(LocalDate d, Set<LocalDate> excluded) {
    return d.getDayOfWeek() != DayOfWeek.SATURDAY
        && d.getDayOfWeek() != DayOfWeek.SUNDAY
        && !excluded.contains(d);
  }
}
