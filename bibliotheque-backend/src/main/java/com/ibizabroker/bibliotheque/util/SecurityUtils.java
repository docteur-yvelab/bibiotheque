package com.ibizabroker.bibliotheque.util;

import com.ibizabroker.bibliotheque.dao.UsersRepository;
import com.ibizabroker.bibliotheque.entity.Users;
import com.ibizabroker.bibliotheque.exceptions.ForbiddenException;
import com.ibizabroker.bibliotheque.exceptions.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

/**
 * Résolution de l'identité métier de l'utilisateur connecté.
 *
 * Le principal Spring Security est un org.springframework.security.core.userdetails.User
 * (username + authorities ROLE_<role>) : il ne porte PAS le userId métier.
 * Cette classe centralise la resolution username -> UsersRepository -> userId
 * afin d'éviter toute duplication dans les contrôleurs (RS-04).
 */
@Component
public class SecurityUtils {

    @Autowired
    private UsersRepository usersRepository;

    /** Username du principal Spring Security. */
    public String getUsernameCourant() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserDetails)) {
            throw new ForbiddenException("Aucun utilisateur authentifié.");
        }
        return ((UserDetails) authentication.getPrincipal()).getUsername();
    }

    /** Entité Users complète de l'utilisateur connecté. */
    public Users getUtilisateurCourant() {
        String username = getUsernameCourant();
        Users utilisateur = usersRepository.findByUsername(username).orElse(null);
        if (utilisateur == null) {
            throw new NotFoundException("Utilisateur connecté introuvable en base : " + username + ".");
        }
        return utilisateur;
    }

    /** Identifiant métier (userId) de l'utilisateur connecté. */
    public Integer getUserIdCourant() {
        return getUtilisateurCourant().getUserId();
    }

    /** Vrai si l'utilisateur connecté possède le rôle donné (ex. "ADHERENT"). */
    public boolean aLeRole(String roleName) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_" + roleName));
    }
}
