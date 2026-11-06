package team.projectpulse.security.authorizationmanagers;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import team.projectpulse.student.StudentSecurityService;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * BR-student-lifecycle: an assigned instructor or the owning course admin, never the student herself.
 */
@ExtendWith(MockitoExtension.class)
class StudentLifecycleAuthorizationManagerTest {

    @Mock
    StudentSecurityService studentSecurityService;

    @InjectMocks
    StudentLifecycleAuthorizationManager studentLifecycleAuthorizationManager;


    private AuthorizationResult authorize(Map<String, String> variables) {
        return this.studentLifecycleAuthorizationManager.authorize(() -> null, new RequestAuthorizationContext(mock(HttpServletRequest.class), variables));
    }

    @Test
    @DisplayName("Admits an instructor assigned to the student's course section")
    void admitsAssignedInstructor() {
        given(this.studentSecurityService.isCurrentUserInstructorOfStudentSection(4)).willReturn(true);

        assertThat(authorize(Map.of("studentId", "4")).isGranted()).isTrue();
    }

    @Test
    @DisplayName("Admits the course admin of the student's course who is not assigned to the course section")
    void admitsOwningCourseAdmin() {
        given(this.studentSecurityService.isCurrentUserInstructorOfStudentSection(4)).willReturn(false);
        given(this.studentSecurityService.isCurrentUserAdminOfStudentCourse(4)).willReturn(true);

        assertThat(authorize(Map.of("studentId", "4")).isGranted()).isTrue();
    }

    @Test
    @DisplayName("Denies anyone else, the student herself included")
    void deniesEveryoneElse() {
        given(this.studentSecurityService.isCurrentUserInstructorOfStudentSection(4)).willReturn(false);
        given(this.studentSecurityService.isCurrentUserAdminOfStudentCourse(4)).willReturn(false);

        assertThat(authorize(Map.of("studentId", "4")).isGranted()).isFalse();
    }

}
