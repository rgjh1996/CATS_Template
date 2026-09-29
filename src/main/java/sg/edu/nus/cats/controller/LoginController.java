package sg.edu.nus.cats.controller;

import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import sg.edu.nus.cats.config.WebConfig;
import sg.edu.nus.cats.dto.form.LoginForm;
import sg.edu.nus.cats.exception.BusinessException;
import sg.edu.nus.cats.service.AuthenticationService;

@Controller
public class LoginController {
  private final AuthenticationService authentication;

  public LoginController(AuthenticationService a) {
    authentication = a;
  }

  @GetMapping("/")
  public String home() {
    return "redirect:/employee";
  }

  @GetMapping({"/employee/login", "/admin/login"})
  public String loginPage(HttpServletRequest request, Model model) {
    model.addAttribute("form", new LoginForm());
    return page(request, model);
  }

  @PostMapping({"/employee/login", "/admin/login"})
  public String login(
      @Valid @ModelAttribute("form") LoginForm form,
      BindingResult errors,
      HttpServletRequest request,
      Model model) {
    if (errors.hasErrors()) return page(request, model);
    try {
      boolean admin = request.getServletPath().startsWith("/admin");
      var user = authentication.authenticate(form.getUsername(), form.getPassword(), admin);
      request.getSession().invalidate();
      var session = request.getSession(true);
      session.setAttribute(
          "user",
          new WebConfig.SessionUser(
              user.getId(), user.getName(), java.util.Set.copyOf(user.getRoles())));
      WebConfig.csrf(session);
      return admin ? "redirect:/admin" : "redirect:/employee";
    } catch (BusinessException e) {
      model.addAttribute("error", e.getMessage());
      return page(request, model);
    }
  }

  private String page(HttpServletRequest r, Model model) {
    boolean admin = r.getServletPath().startsWith("/admin");
    model.addAttribute("adminLogin", admin);
    return "auth/login";
  }

  @PostMapping("/logout")
  public String logout(HttpSession session) {
    session.invalidate();
    return "redirect:/employee/login";
  }

  @GetMapping("/admin")
  public String admin() {
    return "admin/home";
  }
}
