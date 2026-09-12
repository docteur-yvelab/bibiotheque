package com.ibizabroker.bibliotheque;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// Profil test : H2 en mémoire — le contexte ne dépend jamais d'une
// PostgreSQL de dev disponible
@SpringBootTest
@ActiveProfiles("test")
class BibliothequeApplicationTests {

	@Test
	void contextLoads() {
	}

}
