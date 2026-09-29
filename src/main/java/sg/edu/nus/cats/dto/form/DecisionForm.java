package sg.edu.nus.cats.dto.form;

import jakarta.validation.constraints.*;
import sg.edu.nus.cats.model.enums.*;

public class DecisionForm {
  @NotBlank
  @Size(max = 2000)
  private String reason;

  public String getReason() {
    return reason;
  }

  public void setReason(String reason) {
    this.reason = reason;
  }
}
