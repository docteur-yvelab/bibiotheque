package com.ibizabroker.bibliotheque.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Identité connue mais droits insuffisants -> HTTP 403.
 *
 * Distinction stricte avec l'authentification :
 *  401 = « je ne sais pas qui vous êtes » (token absent/invalide/expiré) ;
 *  403 = « je sais qui vous êtes mais vous n'avez pas le droit ».
 */
@ResponseStatus(value = HttpStatus.FORBIDDEN)
public class ForbiddenException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ForbiddenException(String message) {
        super(message);
    }
}
