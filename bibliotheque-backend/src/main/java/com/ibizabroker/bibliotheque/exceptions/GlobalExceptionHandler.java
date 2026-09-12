package com.ibizabroker.bibliotheque.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * Gestion centralisée des erreurs du backend.
 *
 * Chaque réponse porte un champ « message » exploitable tel quel par le
 * frontend (Séance 3) : messages de validation précis, nom de la règle
 * de gestion enfreinte, etc.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private Map<String, Object> body(String message) {
        Map<String, Object> map = new HashMap<>();
        map.put("message", message);
        return map;
    }

    /** 404 — ressource inexistante (livre, adhérent, réservation...). */
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(NotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body(exception.getMessage()));
    }

    /** 409 — règle de gestion violée (RG-01 à RG-06). */
    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(ConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body(exception.getMessage()));
    }

    /** 400 — requête invalide, avec le champ fautif nommé précisément. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body(exception.getMessage()));
    }

    /**
     * 401 — échec d'authentification (mauvais identifiants sur /authenticate).
     * Sans ce handler dédié, BadCredentialsException tombait dans le handler
     * générique et produisait un 500 — sémantiquement faux.
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleBadCredentials(BadCredentialsException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(body("Identifiants invalides."));
    }

    /** 403 — identité connue mais droits insuffisants (jamais confondre avec 401). */
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<Map<String, Object>> handleForbidden(ForbiddenException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body(exception.getMessage()));
    }

    /**
     * 403 — AccessDeniedException de Spring Security (@PreAuthorize).
     * Sans ce relais, le handler générique Exception.class l'absorbait et
     * renvoyait un 500 au lieu du 403 attendu (bug détecté par les tests).
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(body("Accès refusé : droits insuffisants pour cette opération."));
    }

    /** 500 — tout le reste, sans détail technique exposé au client. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(body("Une erreur interne est survenue."));
    }
}
