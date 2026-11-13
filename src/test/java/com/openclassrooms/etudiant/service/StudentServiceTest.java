package com.openclassrooms.etudiant.service;

import com.openclassrooms.etudiant.dto.StudentRequest;
import com.openclassrooms.etudiant.dto.StudentResponse;
import com.openclassrooms.etudiant.entities.Student;
import com.openclassrooms.etudiant.exception.EmailAlreadyExistsException;
import com.openclassrooms.etudiant.repository.StudentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UNIT TEST: only StudentService is real. The repository is a fake (mock),
 * so there is no Spring, no database and no Docker.
 */
@ExtendWith(MockitoExtension.class)
class StudentServiceTest {

    @Mock
    private StudentRepository studentRepository;   // fake database access

    @InjectMocks
    private StudentService studentService;         // the real class under test

    // ---------- createStudent ----------

    @Test
    void createStudent_validRequest_returnsSavedStudent() {
        // GIVEN: the fake repository "saves" by returning the student it receives
        StudentRequest request = new StudentRequest("Ada", "Lovelace", "ada@test.com", 36);
        when(studentRepository.saveAndFlush(any(Student.class))).then(returnsFirstArg());

        // WHEN
        StudentResponse response = studentService.createStudent(request);

        // THEN: the response contains the data of the request, so the mapping works
        assertEquals("Ada", response.firstName());
        assertEquals("Lovelace", response.lastName());
        assertEquals("ada@test.com", response.email());
        assertEquals(36, response.age());
    }

    @Test
    void createStudent_duplicateEmail_throwsEmailAlreadyExistsException() {
        // GIVEN: the database refuses the insert (email is unique)
        StudentRequest request = new StudentRequest("Ada", "Lovelace", "ada@test.com", 36);
        when(studentRepository.saveAndFlush(any(Student.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate email"));

        // WHEN + THEN: the service translates the database error into our own exception
        assertThrows(EmailAlreadyExistsException.class, () -> studentService.createStudent(request));
    }

    // ---------- getAllStudents ----------

    @Test
    void getAllStudents_returnsAllStudentsAsResponses() {
        // GIVEN
        when(studentRepository.findAll()).thenReturn(List.of(
                new Student("Ada", "Lovelace", "ada@test.com", 36),
                new Student("Alan", "Turing", "alan@test.com", 41)));

        // WHEN
        List<StudentResponse> responses = studentService.getAllStudents();

        // THEN
        assertEquals(2, responses.size());
        assertEquals("Ada", responses.get(0).firstName());
        assertEquals("Alan", responses.get(1).firstName());
    }

    // ---------- getStudentById ----------

    @Test
    void getStudentById_existingStudent_returnsResponse() {
        // GIVEN
        Student student = new Student("Ada", "Lovelace", "ada@test.com", 36);
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));

        // WHEN
        StudentResponse response = studentService.getStudentById(1L);

        // THEN
        assertEquals("Ada", response.firstName());
        assertEquals("Lovelace", response.lastName());
        assertEquals("ada@test.com", response.email());
        assertEquals(36, response.age());
    }

    @Test
    void getStudentById_unknownId_throws404() {
        // GIVEN: nothing found in the database
        when(studentRepository.findById(99L)).thenReturn(Optional.empty());

        // WHEN + THEN
        ResponseStatusException exception =
                assertThrows(ResponseStatusException.class, () -> studentService.getStudentById(99L));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    // ---------- updateStudent ----------

    @Test
    void updateStudent_existingStudent_updatesAndReturnsStudent() {
        // GIVEN: an existing student, and a request with new values
        Student existing = new Student("Ada", "Lovelace", "ada@test.com", 36);
        StudentRequest request = new StudentRequest("Augusta", "King", "augusta@test.com", 37);
        when(studentRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(studentRepository.save(any(Student.class))).then(returnsFirstArg());

        // WHEN
        StudentResponse response = studentService.updateStudent(1L, request);

        // THEN: the entity was modified, saved, and the response shows the new values
        assertEquals("Augusta", existing.getFirstName());
        assertEquals("Augusta", response.firstName());
        assertEquals("King", response.lastName());
        assertEquals("augusta@test.com", response.email());
        assertEquals(37, response.age());
        verify(studentRepository).save(existing);
    }

    @Test
    void updateStudent_unknownId_throws404AndSavesNothing() {
        // GIVEN
        StudentRequest request = new StudentRequest("Augusta", "King", "augusta@test.com", 37);
        when(studentRepository.findById(99L)).thenReturn(Optional.empty());

        // WHEN + THEN
        ResponseStatusException exception =
                assertThrows(ResponseStatusException.class, () -> studentService.updateStudent(99L, request));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(studentRepository, never()).save(any());
    }

    // ---------- deleteStudent ----------

    @Test
    void deleteStudent_existingStudent_deletesIt() {
        // GIVEN
        Student student = new Student("Ada", "Lovelace", "ada@test.com", 36);
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));

        // WHEN
        studentService.deleteStudent(1L);

        // THEN
        verify(studentRepository).delete(student);
    }

    @Test
    void deleteStudent_unknownId_throws404AndDeletesNothing() {
        // GIVEN
        when(studentRepository.findById(99L)).thenReturn(Optional.empty());

        // WHEN + THEN
        ResponseStatusException exception =
                assertThrows(ResponseStatusException.class, () -> studentService.deleteStudent(99L));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(studentRepository, never()).delete(any());
    }
}