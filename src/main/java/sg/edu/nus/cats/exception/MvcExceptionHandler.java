package sg.edu.nus.cats.exception;

import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@ControllerAdvice
public class MvcExceptionHandler {
  @ExceptionHandler(BusinessException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public String business(BusinessException e, Model m) {
    m.addAttribute("message", e.getMessage());
    return "error/problem";
  }

  @ExceptionHandler(NotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public String missing(NotFoundException e, Model m) {
    m.addAttribute("message", e.getMessage());
    return "error/problem";
  }

  @ExceptionHandler({
    ObjectOptimisticLockingFailureException.class,
    PessimisticLockingFailureException.class
  })
  @ResponseStatus(HttpStatus.CONFLICT)
  public String conflict(Exception e, Model m) {
    m.addAttribute("message", "Another request changed this record. Reload and try again.");
    return "error/problem";
  }
}
