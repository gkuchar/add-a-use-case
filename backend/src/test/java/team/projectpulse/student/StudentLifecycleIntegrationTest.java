package team.projectpulse.student;

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
import team.projectpulse.AbstractIntegrationTest;
import team.projectpulse.instructor.Instructor;
import team.projectpulse.instructor.InstructorRepository;
import team.projectpulse.section.Section;
import team.projectpulse.section.SectionRepository;
import team.projectpulse.system.StatusCode;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

}
