package sg.edu.nus.cats.dto.form;

import jakarta.validation.constraints.*;
import sg.edu.nus.cats.model.enums.*;

public class CompletionForm {
  @NotBlank
  @Size(max = 2000)
  private String experience;

  public String getExperience() {
    return experience;
  }

  public void setExperience(String experience) {
    this.experience = experience;
  }
}
