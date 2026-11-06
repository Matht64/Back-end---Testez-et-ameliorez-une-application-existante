package com.openclassrooms.etudiant.dto;

public record StudentResponse(
    Long id,
    String firstName,
    String lastName,
    String email,
    Integer age
) {}