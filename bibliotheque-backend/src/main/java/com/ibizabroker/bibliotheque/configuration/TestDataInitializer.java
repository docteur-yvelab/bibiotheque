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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Données de test pour la soutenance (Séance 4) — IDEMPOTENT :
 * ne recrée rien si les comptes existent déjà, donc ne pollue pas
 * la base au redémarrage. Identifiants documentés dans TESTING.md.
 *
 * run() est @Transactional : tout le seed partage un seul contexte de
 * persistance, ce qui évite que le cascade=ALL de Users.role re-persiste
 * un Role détaché, et rend la création atomique.
 */
@Component
public class TestDataInitializer implements CommandLineRunner {

    @Autowired
    private UsersRepository usersRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private BooksRepository booksRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void run(String... args) {
        // La base contient des lignes créées par l'ancienne app (Hibernate 5,
        // séquence globale hibernate_sequence). Hibernate 6 crée des séquences
        // <table>_seq qui repartent de 1 -> collisions de clés primaires au
        // premier INSERT. On réaligne donc les séquences sur le MAX(id) réel.
        alignerSequence("books", "book_id");
        alignerSequence("users", "user_id");
        alignerSequence("role", "role_id");
        alignerSequence("borrow", "borrow_id");
        alignerSequence("reservation", "reservation_id");

        Role adherentRole = findOrCreateRole("ADHERENT");
        Role bibliothecaireRole = findOrCreateRole("BIBLIOTHECAIRE");
        findOrCreateRole("Admin");

        Users adherent1 = findOrCreateUser("adherent1", "Adhérent Un", "adherent1", adherentRole);
        Users adherent2 = findOrCreateUser("adherent2", "Adhérent Deux", "adherent2", adherentRole);
        findOrCreateUser("biblio1", "Bibliothécaire", "biblio1", bibliothecaireRole);

        // Livre indisponible (noOfCopies == 0) : réservable (RG-01)
        Books livreIndisponible = booksRepository.findByBookName("Clean Code").orElse(null);
        if (livreIndisponible == null) {
            livreIndisponible = new Books();
            livreIndisponible.setBookName("Clean Code");
            livreIndisponible.setBookAuthor("Robert C. Martin");
            livreIndisponible.setBookGenre("Informatique");
            livreIndisponible.setNoOfCopies(0);
            booksRepository.save(livreIndisponible);
        }
        // Livre disponible : NON réservable (RG-01)
        if (booksRepository.findByBookName("Effective Java").isEmpty()) {
            Books livreDisponible = new Books();
            livreDisponible.setBookName("Effective Java");
            livreDisponible.setBookAuthor("Joshua Bloch");
            livreDisponible.setBookGenre("Informatique");
            livreDisponible.setNoOfCopies(3);
            booksRepository.save(livreDisponible);
        }

        // Une réservation existante au nom d'adherent1 (pour RS-03 en soutenance)
        if (reservationRepository.findByAdherentId(adherent1.getUserId()).isEmpty()) {
            Reservation reservation = new Reservation();
            reservation.setLivreId(livreIndisponible.getBookId());
            reservation.setAdherentId(adherent1.getUserId());
            reservation.setStatut(ReservationStatus.EN_ATTENTE);

            Date now = new Date();
            reservation.setDateReservation(now);
            Calendar cal = Calendar.getInstance();
            cal.setTime(now);
            cal.add(Calendar.DATE, 7);
            reservation.setDateExpiration(cal.getTime());
            reservationRepository.save(reservation);
        }
    }

    /**
     * Repositionne la séquence <table>_seq (si elle existe) juste après le
     * MAX(id) actuel de la table. Idempotent et sans effet sur une base neuve.
     */
    private void alignerSequence(String table, String colonne) {
        try {
            List<String> sequences = jdbcTemplate.queryForList(
                    "SELECT sequence_name FROM information_schema.sequences WHERE sequence_name ILIKE ?",
                    String.class, table + "_seq");
            if (sequences.isEmpty()) {
                return;
            }
            String sequence = sequences.get(0);
            jdbcTemplate.execute("SELECT setval('" + sequence + "', "
                    + "(SELECT COALESCE(MAX(" + colonne + "), 0) + 1 FROM " + table + "), false)");
        } catch (Exception ignored) {
            // table ou séquence absente : rien à aligner
        }
    }

    private Role findOrCreateRole(String roleName) {
        Role existing = roleRepository.findByRoleName(roleName);
        if (existing != null) {
            return existing;
        }
        Role role = new Role();
        role.setRoleName(roleName);
        return roleRepository.save(role);
    }

    private Users findOrCreateUser(String username, String name, String rawPassword, Role role) {
        Optional<Users> existing = usersRepository.findByUsername(username);
        if (existing.isPresent()) {
            return existing.get();
        }
        Users user = new Users();
        user.setUsername(username);
        user.setName(name);
        user.setPassword(passwordEncoder.encode(rawPassword));
        Set<Role> roles = new HashSet<>();
        roles.add(role);
        user.setRole(roles);
        return usersRepository.save(user);
    }
}
