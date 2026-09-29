package sg.edu.nus.cats.model;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import sg.edu.nus.cats.model.enums.*;

@Entity
public class PublicHoliday {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true)
  private LocalDate holidayDate;

  private String description;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public LocalDate getHolidayDate() {
    return holidayDate;
  }

  public void setHolidayDate(LocalDate holidayDate) {
    this.holidayDate = holidayDate;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }
}
