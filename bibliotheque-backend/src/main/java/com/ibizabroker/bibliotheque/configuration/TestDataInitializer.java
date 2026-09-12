package com.ibizabroker.bibliotheque.configuration;

import com.ibizabroker.bibliotheque.dao.BooksRepository;
import com.ibizabroker.bibliotheque.dao.ReservationRepository;
import com.ibizabroker.bibliotheque.dao.RoleRepository;
import com.ibizabroker.bibliotheque.dao.UsersRepository;
import com.ibizabroker.bibliotheque.entity.Books;
import com.ibizabroker.bibliotheque.entity.Reservation;
import com.ibizabroker.bibliotheque.entity.ReservationStatus;
import com.ibizabroker.bibliotheque.entity.Role;
import com.ibizabroker.bibliotheque.entity.Users;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

/**
 * Données de test de soutenance (Séance 4).
 *
 * Idempotent : chaque entité est cherchée avant d'être créée — redémarrer
 * l'application ne duplique rien et ne modifie pas les mots de passe
 * existants. Comptes documentés dans TESTING.md.
 */
@Component
public class TestDataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(TestDataInitializer.class);

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UsersRepository usersRepository;

    @Autowired
    private BooksRepository booksRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        creerRoleSiAbsent("ADHERENT");
        creerRoleSiAbsent("BIBLIOTHECAIRE");
        creerRoleSiAbsent("Admin");

        Users adherent1 = creerUtilisateurSiAbsent("adherent1", "Adhérent Un", "adherent1", "ADHERENT");
        Users adherent2 = creerUtilisateurSiAbsent("adherent2", "Adhérent Deux", "adherent2", "ADHERENT");
        creerUtilisateurSiAbsent("biblio1", "Bibliothécaire", "biblio1", "BIBLIOTHECAIRE");

        Books cleanCode = creerLivreSiAbsent("Clean Code", "Robert C. Martin", "Informatique", 0);
        Books effectiveJava = creerLivreSiAbsent("Effective Java", "Joshua Bloch", "Informatique", 0);

        creerReservationSiAbsent(adherent1, cleanCode);
        creerReservationSiAbsent(adherent1, effectiveJava);
        creerReservationSiAbsent(adherent2, cleanCode);
    }

    private void creerRoleSiAbsent(String roleName) {
        if (roleRepository.findByRoleName(roleName).isEmpty()) {
            Role role = new Role();
            role.setRoleName(roleName);
            roleRepository.save(role);
            log.info("Rôle créé : {}", roleName);
        }
    }

    private Users creerUtilisateurSiAbsent(String username, String name, String motDePasse, String roleName) {
        Users existant = usersRepository.findByUsername(username).orElse(null);
        if (existant != null) {
            return existant;
        }
        Role role = roleRepository.findByRoleName(roleName)
                .orElseThrow(() -> new IllegalStateException("Rôle absent : " + roleName));

        Users utilisateur = new Users();
        utilisateur.setUsername(username);
        utilisateur.setName(name);
        utilisateur.setPassword(passwordEncoder.encode(motDePasse));
        Set<Role> roles = new HashSet<>();
        roles.add(role);
        utilisateur.setRole(roles);
        Users enregistre = usersRepository.save(utilisateur);
        log.info("Utilisateur créé : {} ({})", username, roleName);
        return enregistre;
    }

    private Books creerLivreSiAbsent(String nom, String auteur, String genre, int copies) {
        Books existant = booksRepository.findByBookName(nom).orElse(null);
        if (existant != null) {
            return existant;
        }
        Books livre = new Books();
        livre.setBookName(nom);
        livre.setBookAuthor(auteur);
        livre.setBookGenre(genre);
        livre.setNoOfCopies(copies);
        Books enregistre = booksRepository.save(livre);
        log.info("Livre créé : {}", nom);
        return enregistre;
    }

    private void creerReservationSiAbsent(Users adherent, Books livre) {
        boolean existe = reservationRepository
                .findByAdherentIdAndStatutIn(adherent.getUserId(),
                        java.util.Arrays.asList(ReservationStatus.EN_ATTENTE, ReservationStatus.DISPONIBLE))
                .stream()
                .anyMatch(r -> r.getLivreId().equals(livre.getBookId()));
        if (existe) {
            return;
        }
        Date maintenant = new Date();
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(maintenant);
        calendar.add(Calendar.DAY_OF_MONTH, 7);

        Reservation reservation = new Reservation();
        reservation.setLivreId(livre.getBookId());
        reservation.setAdherentId(adherent.getUserId());
        reservation.setDateReservation(maintenant);
        reservation.setDateExpiration(calendar.getTime());
        reservation.setStatut(ReservationStatus.EN_ATTENTE);
        reservationRepository.save(reservation);
        log.info("Réservation seed créée : {} sur {}", adherent.getUsername(), livre.getBookName());
    }
}
