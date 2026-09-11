package com.ibizabroker.bibliotheque.exceptions;

/**
 * 401 Unauthorized — Identifiants invalides (mauvais username ou mot de passe).
 * Levée par JwtService lors de l'authentification, convertie en HTTP 401
 * par GlobalExceptionHandler (au lieu d'un 500 générique).
 */
public class InvalidCredentialsException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidCredentialsException(String message) {
        super(message);
    }
}
