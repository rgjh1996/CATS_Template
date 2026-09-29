package sg.edu.nus.cats.dto.view;

import java.math.BigDecimal;

public record AnnualUsage(
    int year,
    BigDecimal dayLimit,
    BigDecimal budgetLimit,
    BigDecimal committedDays,
    BigDecimal pendingDays,
    BigDecimal committedFees,
    BigDecimal pendingFees) {
  public BigDecimal remainingDays() {
    return dayLimit.subtract(committedDays).subtract(pendingDays);
  }

  public BigDecimal remainingBudget() {
    return budgetLimit.subtract(committedFees).subtract(pendingFees);
  }
}
