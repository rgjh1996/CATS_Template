package sg.edu.nus.cats.controller;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.time.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import sg.edu.nus.cats.config.WebConfig;
import sg.edu.nus.cats.dto.form.DecisionForm;
import sg.edu.nus.cats.exception.BusinessException;
import sg.edu.nus.cats.service.ApprovalService;

@Controller
@RequestMapping("/manager")
public class ManagerController {
  private final ApprovalService service;
  private final Clock clock;

  public ManagerController(ApprovalService s, Clock c) {
    service = s;
    clock = c;
  }

  @GetMapping("/pending")
  public String pending(HttpSession s, Model m) {
    var id = WebConfig.user(s).id();
    m.addAttribute("groups", service.getPendingBySubordinate(id));
    m.addAttribute("team", service.getSubordinates(id));
    return "manager/pending";
  }

  @GetMapping("/employees/{id}/history")
  public String history(@PathVariable Long id, HttpSession s, Model m) {
    m.addAttribute(
        "applications",
        service.getSubordinateHistory(WebConfig.user(s).id(), id, LocalDate.now(clock).getYear()));
    m.addAttribute("heading", "Subordinate course history");
    m.addAttribute("managerView", true);
    return "applications/history";
  }

  @GetMapping("/applications/{id}")
  public String detail(@PathVariable Long id, HttpSession s, Model m) {
    context(id, s, m);
    m.addAttribute("form", new DecisionForm());
    return "manager/decision";
  }

  @PostMapping("/applications/{id}/{action:approve|reject}")
  public String decide(
      @PathVariable Long id,
      @PathVariable String action,
      @Valid @ModelAttribute("form") DecisionForm f,
      BindingResult errors,
      HttpSession s,
      Model m,
      RedirectAttributes ra) {
    if (errors.hasErrors()) {
      context(id, s, m);
      return "manager/decision";
    }
    try {
      if (action.equals("approve")) service.approve(WebConfig.user(s).id(), id, f.getReason());
      else service.reject(WebConfig.user(s).id(), id, f.getReason());
      ra.addFlashAttribute("success", "Decision recorded.");
      return "redirect:/manager/applications/" + id;
    } catch (BusinessException e) {
      context(id, s, m);
      m.addAttribute("error", e.getMessage());
      return "manager/decision";
    }
  }

  private void context(Long id, HttpSession s, Model m) {
    var c = service.getApprovalContext(WebConfig.user(s).id(), id);
    m.addAttribute("context", c);
    m.addAttribute("courseApplication", c.application());
  }
}
