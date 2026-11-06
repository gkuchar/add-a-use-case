package team.projectpulse.student;

import team.projectpulse.system.UserUtils;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

/**
 * Answers the questions a route asks about one named student: is that student the caller herself
 * ({@code isStudentSelf}), is the caller an instructor of that student's course section
 * ({@code isCurrentUserInstructorOfStudentSection}), and may the caller manage that student's lifecycle as an
 * assigned instructor or the course admin of her course ({@code isCurrentUserInstructorOrAdminOfStudent}).
 *
 * <p>Every check fails closed: an id that names no row, or a link not yet established, is denied rather than
 * throwing. These methods run inside an {@code AuthorizationManager}, upstream of the {@code DispatcherServlet},
 * so an exception thrown here escapes the filter chain as a 500 that also tells the caller which ids exist.
 */
@Service
@Transactional
public class StudentSecurityService {

    private final StudentRepository studentRepository;
    private final UserUtils userUtils;


    public StudentSecurityService(StudentRepository studentRepository, UserUtils userUtils) {
        this.studentRepository = studentRepository;
        this.userUtils = userUtils;
    }

    public boolean isStudentSelf(Integer studentId) {
        Integer studentIdFromJwt = this.userUtils.getUserId();
        return studentId.equals(studentIdFromJwt);
    }

    /**
     * Check if the user is the instructor of the section that the student is in
     *
     * @param studentId the id of the student we need to access, this is NOT the current user's Id
     * @return
     */
    public boolean isCurrentUserInstructorOfStudentSection(Integer studentId) {
        Integer userIdFromJwt = this.userUtils.getUserId();
        return this.studentRepository.findById(studentId)
                .map(student -> isInstructorOfSection(student, userIdFromJwt))
                .orElse(false);
    }

    /**
     * Check if the user may manage the student's lifecycle (BR-student-lifecycle): an instructor assigned to her
     * course section, or the course admin of her course whether or not she is assigned to that course section. No
     * role check: being named course admin is the right. Loads the student once for both questions.
     *
     * @param studentId the id of the student we need to access, this is NOT the current user's Id
     * @return false for an unknown student
     */
    public boolean isCurrentUserInstructorOrAdminOfStudent(Integer studentId) {
        Integer userIdFromJwt = this.userUtils.getUserId();
        return this.studentRepository.findById(studentId)
                .map(student -> isInstructorOfSection(student, userIdFromJwt) || isAdminOfCourse(student, userIdFromJwt))
                .orElse(false);
    }

    // Student.section, Section.course, and Course.courseAdmin are all mandatory (@ManyToOne(optional = false)).
    private static boolean isInstructorOfSection(Student student, Integer userId) {
        return student.getSection().getInstructors().stream()
                .anyMatch(instructor -> instructor.getId().equals(userId));
    }

    private static boolean isAdminOfCourse(Student student, Integer userId) {
        return student.getSection().getCourse().getCourseAdmin().getId().equals(userId);
    }

}
