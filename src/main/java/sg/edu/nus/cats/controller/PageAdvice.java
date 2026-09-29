package sg.edu.nus.cats.controller;

import jakarta.servlet.http.HttpSession;
import java.time.*;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import sg.edu.nus.cats.config.WebConfig;

@ControllerAdvice
public class PageAdvice {
  private final Clock clock;

  public PageAdvice(Clock clock) {
    this.clock = clock;
  }

  @ModelAttribute
  public void common(HttpSession session, Model model) {
    model.addAttribute("currentUser", WebConfig.user(session));
    model.addAttribute("csrfToken", WebConfig.csrf(session));
    model.addAttribute("today", LocalDate.now(clock));
  }
}
