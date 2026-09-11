package com.ibizabroker.bibliotheque.service;

import com.ibizabroker.bibliotheque.dao.UsersRepository;
import com.ibizabroker.bibliotheque.entity.Users;
import com.ibizabroker.bibliotheque.exceptions.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Service utilitaire pour extraire l'utilisateur courant depuis le SecurityContext (JWT)
 * et vérifier ses rôles (RS-04, RS-05).
 */
@Service
public class CurrentUserService {

    @Autowired
    private UsersRepository usersRepository;

    /**
     * Retourne l'entité Users de l'utilisateur actuellement authentifié.
     * Extrait le username depuis le SecurityContext (mis en place par JwtRequestFilter).
     *
     * @throws NotFoundException si l'utilisateur n'est pas trouvé en base
     */
    public Users getAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new IllegalStateException("Aucun utilisateur authentifié.");
        }
        String username = auth.getName();
        return usersRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("Utilisateur authentifié '" + username + "' non trouvé en base."));
    }

    /**
     * Retourne l'userId de l'utilisateur courant.
     */
    public Integer getAuthenticatedUserId() {
        return getAuthenticatedUser().getUserId();
    }

    /**
     * Vérifie si l'utilisateur courant a le rôle BIBLIOTHECAIRE.
     */
    public boolean isBibliothecaire() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_BIBLIOTHECAIRE"));
    }

    /**
     * Vérifie si l'utilisateur courant a le rôle ADHERENT.
     */
    public boolean isAdherent() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADHERENT"));
    }

    /**
     * Vérifie si l'utilisateur courant est admin (bibliothécaire).
     */
    public boolean isAdmin() {
        return isBibliothecaire();
    }
}
