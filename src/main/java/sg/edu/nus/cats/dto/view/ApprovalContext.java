package sg.edu.nus.cats.dto.view;

import java.util.*;
import sg.edu.nus.cats.model.CourseApplication;

public record ApprovalContext(
    CourseApplication application,
    Map<Integer, AnnualUsage> annualUsage,
    List<CourseApplication> otherTeamApprovedCourses) {}
