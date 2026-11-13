package com.openclassrooms.etudiant.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openclassrooms.etudiant.dto.LoginRequestDTO;
import com.openclassrooms.etudiant.dto.StudentRequest;
import com.openclassrooms.etudiant.entities.Student;
import com.openclassrooms.etudiant.entities.User;
import com.openclassrooms.etudiant.repository.StudentRepository;
import com.openclassrooms.etudiant.repository.UserRepository;
import com.openclassrooms.etudiant.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * INTEGRATION TEST of /api/students: real application + real MySQL (Testcontainers).
 * Before each test we create a user and log in through the API to get a JWT,
 * because every /api/students endpoint requires authentication.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
public class StudentControllerIT {

    private static final String URL = "/api/students";

    @Container
    static MySQLContainer mySQLContainer = new MySQLContainer("mysql:8.4");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserService userService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private StudentRepository studentRepository;

    private String token;

    @DynamicPropertySource
    static void configureTestProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> mySQLContainer.getJdbcUrl());
        registry.add("spring.datasource.username", () -> mySQLContainer.getUsername());
        registry.add("spring.datasource.password", () -> mySQLContainer.getPassword());
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
    }

    @BeforeEach
    void createUserAndLogin() throws Exception {
        // Create a user directly in the database
        User user = new User();
        user.setFirstName("Teacher");
        user.setLastName("Test");
        user.setLogin("teacher");
        user.setPassword("password");
        userService.register(user);

        // Log in through the API: the response body is the JWT
        LoginRequestDTO loginRequest = new LoginRequestDTO();
        loginRequest.setLogin("teacher");
        loginRequest.setPassword("password");

        token = mockMvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @AfterEach
    void cleanDatabase() {
        studentRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ---------- security ----------

    @Test
    void getStudents_withoutToken_returns401() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isUnauthorized());
    }

    // ---------- POST /api/students ----------

    @Test
    void createStudent_withValidData_returns201() throws Exception {
        StudentRequest request = new StudentRequest("Ada", "Lovelace", "ada@test.com", 36);

        mockMvc.perform(post(URL)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.firstName").value("Ada"))
                .andExpect(jsonPath("$.email").value("ada@test.com"));

        // The student really is in the database
        assertEquals(1, studentRepository.count());
    }

    @Test
    void createStudent_withInvalidData_returns400() throws Exception {
        // blank first name, malformed email, age 0: all three break the validation rules
        StudentRequest request = new StudentRequest("", "Lovelace", "not-an-email", 0);

        mockMvc.perform(post(URL)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        assertEquals(0, studentRepository.count());
    }

    @Test
    void createStudent_withExistingEmail_returns409() throws Exception {
        studentRepository.save(new Student("Ada", "Lovelace", "ada@test.com", 36));
        StudentRequest request = new StudentRequest("Alan", "Turing", "ada@test.com", 41);

        mockMvc.perform(post(URL)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("EMAIL_ALREADY_EXISTS"));

        assertEquals(1, studentRepository.count());
    }

    // ---------- GET /api/students ----------

    @Test
    void getAllStudents_returnsTheList() throws Exception {
        studentRepository.save(new Student("Ada", "Lovelace", "ada@test.com", 36));
        studentRepository.save(new Student("Alan", "Turing", "alan@test.com", 41));

        mockMvc.perform(get(URL)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    // ---------- GET /api/students/{id} ----------

    @Test
    void getStudentById_existingStudent_returns200() throws Exception {
        Student saved = studentRepository.save(new Student("Ada", "Lovelace", "ada@test.com", 36));

        mockMvc.perform(get(URL + "/" + saved.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Ada"))
                .andExpect(jsonPath("$.lastName").value("Lovelace"));
    }

    @Test
    void getStudentById_unknownId_returns404() throws Exception {
        mockMvc.perform(get(URL + "/9999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    // ---------- PUT /api/students/{id} ----------

    @Test
    void updateStudent_existingStudent_returns200() throws Exception {
        Student saved = studentRepository.save(new Student("Ada", "Lovelace", "ada@test.com", 36));
        StudentRequest update = new StudentRequest("Augusta", "Lovelace", "ada@test.com", 37);

        mockMvc.perform(put(URL + "/" + saved.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Augusta"));

        // The change was saved in the database
        Student inDatabase = studentRepository.findById(saved.getId()).orElseThrow();
        assertEquals("Augusta", inDatabase.getFirstName());
        assertEquals(37, inDatabase.getAge());
    }

    // ---------- DELETE /api/students/{id} ----------

    @Test
    void deleteStudent_existingStudent_returns204() throws Exception {
        Student saved = studentRepository.save(new Student("Ada", "Lovelace", "ada@test.com", 36));

        mockMvc.perform(delete(URL + "/" + saved.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        assertFalse(studentRepository.existsById(saved.getId()));
    }
}