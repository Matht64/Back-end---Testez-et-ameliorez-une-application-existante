package com.openclassrooms.etudiant.exception;

public class EmailAlreadyExistsException extends RuntimeException {

    public EmailAlreadyExistsException(String email) {
        super("A student with email " + email + " already exists");
    }
}