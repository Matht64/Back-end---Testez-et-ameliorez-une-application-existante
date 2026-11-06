package com.openclassrooms.etudiant.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.openclassrooms.etudiant.dto.StudentRequest;
import com.openclassrooms.etudiant.dto.StudentResponse;
import com.openclassrooms.etudiant.entities.Student;
import com.openclassrooms.etudiant.exception.EmailAlreadyExistsException;
import com.openclassrooms.etudiant.repository.StudentRepository;

import java.util.List;

@Service
public class StudentService {

    private final StudentRepository studentRepository;

    public StudentService(StudentRepository studentRepository) {
        this.studentRepository = studentRepository;
    }

    // CREATE
    public StudentResponse createStudent(StudentRequest request) {
        Student student = new Student(
            request.firstName(),
            request.lastName(),
            request.email(),
            request.age()
        );

        try {
            Student savedStudent = studentRepository.saveAndFlush(student);
            return toResponse(savedStudent);

        } catch (DataIntegrityViolationException ex) {
            if (true /*add a real condition isDuplicateEmail(ex)*/) {
                throw new EmailAlreadyExistsException(request.email());
            }

            throw ex;
        }
    }

    // READ ALL
    public List<StudentResponse> getAllStudents() {
        return studentRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // READ ONE
    public StudentResponse getStudentById(Long id) {
        Student student = findStudent(id);
        return toResponse(student);
    }

    // UPDATE
    public StudentResponse updateStudent(
            Long id, StudentRequest request) {

        Student student = findStudent(id);

        student.setFirstName(request.firstName());
        student.setLastName(request.lastName());
        student.setEmail(request.email());
        student.setAge(request.age());

        Student updatedStudent = studentRepository.save(student);

        return toResponse(updatedStudent);
    }

    // DELETE
    public void deleteStudent(Long id) {
        Student student = findStudent(id);
        studentRepository.delete(student);
    }

    // Helper: find student or throw 404
    private Student findStudent(Long id) {
        return studentRepository.findById(id)
                .orElseThrow(() ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Student not found with id: " + id
                    )
                );
    }

    // Entity -> Response DTO
    private StudentResponse toResponse(Student student) {
        return new StudentResponse(
            student.getId(),
            student.getFirstName(),
            student.getLastName(),
            student.getEmail(),
            student.getAge()
        );
    }
}