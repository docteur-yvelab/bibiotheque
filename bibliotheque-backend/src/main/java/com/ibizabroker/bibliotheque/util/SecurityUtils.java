package com.ibizabroker.bibliotheque.util;

import com.ibizabroker.bibliotheque.dao.UsersRepository;
import com.ibizabroker.bibliotheque.entity.Users;
import com.ibizabroker.bibliotheque.exceptions.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Point d'accès unique à l'identité de l'utilisateur authentifié.
 *
 * C'est le cœur de RS-04 : l'identité ne vient JAMAIS du corps de la
 * requête (adherentId envoyé par le client), toujours du token JWT
 * validé par JwtRequestFilter et posé dans le SecurityContext.
 *
 * Le principal Spring Security (org.springframework.security.core.userdetails.User)
 * ne contient que le username — le userId métier est résolu via
 * UsersRepository.findByUsername(username).
 */
@Component
public class SecurityUtils {

    @Autowired
    private UsersRepository usersRepository;

    /**
     * Retourne l'entité Users correspondant à l'utilisateur actuellement
     * authentifié (le username vient du token, pas d'un paramètre client).
     */
    public Users getUtilisateurConnecte() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        return usersRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("Utilisateur non trouvé : " + username));
    }

    /**
     * true si l'utilisateur connecté a le rôle BIBLIOTHECAIRE.
     */
    public boolean estBibliothecaire() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role -> role.equals("ROLE_BIBLIOTHECAIRE"));
    }
}
