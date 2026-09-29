package sg.edu.nus.cats.dto.form;

import jakarta.validation.constraints.*;
import sg.edu.nus.cats.model.enums.*;

public class LoginForm {
  @NotBlank
  @Size(max = 2000)
  private String username;

  @NotBlank
  @Size(max = 2000)
  private String password;

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }
}
