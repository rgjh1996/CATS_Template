package sg.edu.nus.cats.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import sg.edu.nus.cats.model.enums.*;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"employee_id", "entitlement_year"}))
public class AnnualEntitlement {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  private User employee;

  @Column(name = "entitlement_year")
  private int year;

  @Column(nullable = false, precision = 8, scale = 1)
  private BigDecimal trainingDayLimit;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal budgetLimit;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public User getEmployee() {
    return employee;
  }

  public void setEmployee(User employee) {
    this.employee = employee;
  }

  public int getYear() {
    return year;
  }

  public void setYear(int year) {
    this.year = year;
  }

  public BigDecimal getTrainingDayLimit() {
    return trainingDayLimit;
  }

  public void setTrainingDayLimit(BigDecimal trainingDayLimit) {
    this.trainingDayLimit = trainingDayLimit;
  }

  public BigDecimal getBudgetLimit() {
    return budgetLimit;
  }

  public void setBudgetLimit(BigDecimal budgetLimit) {
    this.budgetLimit = budgetLimit;
  }
}
