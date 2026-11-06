package team.projectpulse.student;

import jakarta.persistence.EntityManager;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import team.projectpulse.AbstractIntegrationTest;
import team.projectpulse.activity.ActivityRepository;
import team.projectpulse.evaluation.PeerEvaluationRepository;
import team.projectpulse.team.Team;
import team.projectpulse.team.TeamRepository;
import team.projectpulse.instructor.Instructor;
import team.projectpulse.instructor.InstructorRepository;
import team.projectpulse.section.Section;
import team.projectpulse.section.SectionRepository;
import team.projectpulse.system.StatusCode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UC-STU-deactivate-student and BR-student-lifecycle, through the API. Seed: section 2 belongs to course 1, whose
 * course admin Bingyang (instructor 1) and instructor Bill (instructor 2) are both assigned to it; Tim (instructor 3)
 * is course 3's admin. John (student 4) and Eric (student 5) are on team 1 in section 2.
 */
@DisplayName("Integration tests for the student lifecycle (UC-STU-deactivate-student)")
class StudentLifecycleIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    SectionRepository sectionRepository;

    @Autowired
    InstructorRepository instructorRepository;

    @MockitoSpyBean
    StudentRepository studentRepository;

    @Autowired
    TeamRepository teamRepository;

    @Autowired
    ActivityRepository activityRepository;

    @Autowired
    PeerEvaluationRepository peerEvaluationRepository;

    @Autowired
    EntityManager entityManager;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Value("${api.endpoint.base-url}")
    String baseUrl;

    String adminBingyangToken;

    String instructorBillToken;

    String adminTimToken;

    String studentJohnToken;

    String studentEricToken;


    @BeforeEach
    void setUp() throws Exception {
        this.adminBingyangToken = login("b.wei@abc.edu");
        this.instructorBillToken = login("b.gates@abc.edu");
        this.adminTimToken = login("t.cook@abc.edu");
        this.studentJohnToken = login("j.smith@abc.edu");
        this.studentEricToken = login("e.hudson@abc.edu");
    }

    private String login(String email) throws Exception {
        MvcResult mvcResult = this.mockMvc.perform(post(this.baseUrl + "/users/login").with(httpBasic(email, "123456"))).andReturn();
        JSONObject json = new JSONObject(mvcResult.getResponse().getContentAsString());
        return "Bearer " + json.getJSONObject("data").getString("token");
    }

    void unassignFromSection2(Integer instructorId) {
        Section section2 = this.sectionRepository.findById(2).orElseThrow();
        Instructor instructor = this.instructorRepository.findById(instructorId).orElseThrow();
        section2.removeInstructor(instructor);
        this.sectionRepository.saveAndFlush(section2);
    }

    // GET /students/{studentId}: UC-STU-view-student is step 2 of deactivation, so the owning course admin may view.

    @Test
    void testUnassignedOwningCourseAdminViewsStudent() throws Exception {
        unassignFromSection2(1);

        this.mockMvc.perform(get(this.baseUrl + "/students/4").accept(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, this.adminBingyangToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flag").value(true))
                .andExpect(jsonPath("$.data.id").value(4));
    }

    @Test
    void testAnotherCoursesAdminCannotViewStudent() throws Exception {
        this.mockMvc.perform(get(this.baseUrl + "/students/4").accept(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, this.adminTimToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.flag").value(false))
                .andExpect(jsonPath("$.code").value(StatusCode.FORBIDDEN));
    }

    // PATCH /students/{studentId}/deactivate

    private org.springframework.test.web.servlet.ResultActions deactivate(Integer studentId, String token) throws Exception {
        return this.mockMvc.perform(patch(this.baseUrl + "/students/" + studentId + "/deactivate").accept(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, token));
    }

    private Student reloadStudent(Integer studentId) {
        this.entityManager.flush();
        this.entityManager.clear();
        return this.studentRepository.findById(studentId).orElseThrow();
    }

    @Test
    void testAssignedInstructorDeactivatesStudent() throws Exception {
        long activities = this.activityRepository.count();
        long evaluations = this.peerEvaluationRepository.count();

        deactivate(4, this.instructorBillToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flag").value(true))
                .andExpect(jsonPath("$.code").value(StatusCode.SUCCESS))
                .andExpect(jsonPath("$.message").value("Deactivate student successfully"))
                .andExpect(jsonPath("$.data.id").value(4))
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.teamId").doesNotExist());

        Student john = reloadStudent(4);
        assertThat(john.isEnabled()).isFalse();
        assertThat(john.getTeam()).isNull();
        assertThat(this.teamRepository.findById(1).orElseThrow().getStudents()).extracting(Student::getId).doesNotContain(4);
        // Her weekly activity reports and peer evaluations stay.
        assertThat(this.activityRepository.count()).isEqualTo(activities);
        assertThat(this.peerEvaluationRepository.count()).isEqualTo(evaluations);
    }

    @Test
    void testUnassignedOwningCourseAdminDeactivatesStudent() throws Exception {
        unassignFromSection2(1);

        deactivate(4, this.adminBingyangToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Deactivate student successfully"))
                .andExpect(jsonPath("$.data.enabled").value(false));
        assertThat(reloadStudent(4).isEnabled()).isFalse();
    }

    @Test
    void testDeactivateStudentOnNoTeam() throws Exception {
        // Tracy (section 2) is on no team
        Integer tracyId = this.studentRepository.findAll().stream().filter(s -> s.getEmail().equals("t.nicholson@abc.edu")).findFirst().orElseThrow().getId();

        deactivate(tracyId, this.instructorBillToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Deactivate student successfully"));
        assertThat(reloadStudent(tracyId).isEnabled()).isFalse();
    }

    @Test
    void testDeactivateAlreadyDeactivatedStudent() throws Exception {
        // Extension 6a
        deactivate(4, this.instructorBillToken).andExpect(status().isOk());
        reloadStudent(4);

        deactivate(4, this.adminBingyangToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flag").value(true))
                .andExpect(jsonPath("$.code").value(StatusCode.SUCCESS))
                .andExpect(jsonPath("$.message").value("This student is already deactivated."))
                .andExpect(jsonPath("$.data.enabled").value(false));

        Student john = reloadStudent(4);
        assertThat(john.isEnabled()).isFalse();
        assertThat(john.getTeam()).isNull();
    }

    private void expectForbidden(Integer studentId, String token) throws Exception {
        deactivate(studentId, token)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.flag").value(false))
                .andExpect(jsonPath("$.code").value(StatusCode.FORBIDDEN))
                .andExpect(jsonPath("$.message").value("No permission."));
    }

    @Test
    void testStudentCannotDeactivateHerself() throws Exception {
        expectForbidden(4, this.studentJohnToken);
        assertThat(reloadStudent(4).isEnabled()).isTrue();
    }

    @Test
    void testStudentCannotDeactivateAnotherStudent() throws Exception {
        expectForbidden(4, this.studentEricToken);
        assertThat(reloadStudent(4).isEnabled()).isTrue();
    }

    @Test
    void testUnassignedInstructorCannotDeactivateStudent() throws Exception {
        unassignFromSection2(2);
        expectForbidden(4, this.instructorBillToken);
        assertThat(reloadStudent(4).isEnabled()).isTrue();
    }

    @Test
    void testAnotherCoursesAdminCannotDeactivateStudent() throws Exception {
        expectForbidden(4, this.adminTimToken);
        assertThat(reloadStudent(4).isEnabled()).isTrue();
    }

    @Test
    void testDeactivateUnknownStudentIsForbidden() throws Exception {
        expectForbidden(9999, this.adminBingyangToken);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED) // Let the service transaction really commit or roll back.
    void testDeactivationIsAtomic() throws Exception {
        willThrow(new RuntimeException("save failed")).given(this.studentRepository).save(any(Student.class));
        try {
            deactivate(4, this.instructorBillToken)
                    .andExpect(status().is5xxServerError())
                    .andExpect(jsonPath("$.flag").value(false));

            reset(this.studentRepository);
            this.transactionTemplate.executeWithoutResult(tx -> {
                Student john = this.studentRepository.findById(4).orElseThrow();
                assertThat(john.isEnabled()).isTrue();
                assertThat(john.getTeam()).isNotNull();
                assertThat(john.getTeam().getTeamId()).isEqualTo(1);
            });
        } finally {
            reset(this.studentRepository);
            // Restore the seeded state should the rollback ever fail, since this test commits outside the test transaction.
            this.transactionTemplate.executeWithoutResult(tx -> {
                Student john = this.studentRepository.findById(4).orElseThrow();
                if (!john.isEnabled() || john.getTeam() == null) {
                    john.setEnabled(true);
                    Team team1 = this.teamRepository.findById(1).orElseThrow();
                    if (john.getTeam() == null) team1.addStudent(john);
                    this.studentRepository.save(john);
                }
            });
        }
    }

}
