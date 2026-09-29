package sg.edu.nus.cats.config;

import jakarta.servlet.http.*;
import java.util.*;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.*;
import org.springframework.web.servlet.config.annotation.*;
import sg.edu.nus.cats.model.enums.Role;

@Configuration
public class WebConfig implements WebMvcConfigurer {
  public record SessionUser(Long id, String name, Set<Role> roles) {
    public boolean manager() {
      return roles.contains(Role.MANAGER);
    }

    public boolean admin() {
      return roles.contains(Role.ADMIN);
    }
  }

  public static SessionUser user(HttpSession session) {
    return (SessionUser) session.getAttribute("user");
  }

  public static String csrf(HttpSession session) {
    String token = (String) session.getAttribute("csrf");
    if (token == null) {
      token = UUID.randomUUID().toString();
      session.setAttribute("csrf", token);
    }
    return token;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry
        .addInterceptor(
            new HandlerInterceptor() {
              @Override
              public boolean preHandle(
                  HttpServletRequest req, HttpServletResponse res, Object handler)
                  throws Exception {
                String path = req.getRequestURI().substring(req.getContextPath().length());
                if (req.getMethod().equals("POST")) {
                  var s = req.getSession(false);
                  String expected = s == null ? null : (String) s.getAttribute("csrf");
                  if (expected == null || !expected.equals(req.getParameter("_csrf"))) {
                    res.sendError(403, "Your form expired. Reload the page and try again.");
                    return false;
                  }
                }
                if (path.equals("/")
                    || path.equals("/employee/login")
                    || path.equals("/admin/login")
                    || path.startsWith("/error")) return true;
                var session = req.getSession(false);
                var u = session == null ? null : user(session);
                if (u == null) {
                  res.sendRedirect(req.getContextPath() + "/employee/login");
                  return false;
                }
                if (path.startsWith("/manager") && !u.manager()
                    || path.startsWith("/admin") && !u.admin()) {
                  res.sendError(403);
                  return false;
                }
                if ((path.startsWith("/applications") || path.equals("/employee"))
                    && !(u.roles().contains(Role.EMPLOYEE) || u.manager())) {
                  res.sendError(403);
                  return false;
                }
                return true;
              }
            })
        .excludePathPatterns("/css/**", "/js/**", "/images/**", "/favicon.ico");
  }
}
