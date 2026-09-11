package com.ibizabroker.bibliotheque;

import com.ibizabroker.bibliotheque.dao.UsersRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootApplication
public class BibliothequeApplication {

	public static void main(String[] args) {
		SpringApplication.run(BibliothequeApplication.class, args);
	}

	@Bean
	public CommandLineRunner fixAdminPassword(UsersRepository usersRepository, PasswordEncoder passwordEncoder) {
		return args -> {
			usersRepository.findByUsername("admin").ifPresent(user -> {
				// Ne réinitialise le mot de passe que s'il n'est pas déjà encodé
				if (!user.getPassword().startsWith("$2a$")) {
					user.setPassword(passwordEncoder.encode("123456"));
					usersRepository.save(user);
					System.out.println(">>> MOT DE PASSE ADMIN REINITIALISE AVEC SUCCES POUR '123456' <<<");
				}
			});
		};
	}

}
