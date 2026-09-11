-- =============================================
-- INITIALISATION DES DONNÉES DE TEST
-- =============================================

-- 1. Création des livres (L1 à L5)
INSERT INTO books (book_id, book_name, book_author, book_genre, no_of_copies) VALUES
(1, 'L1', 'Auteur L1', 'Roman', 1),
(2, 'L2', 'Auteur L2', 'Roman', 1),
(3, 'L3', 'Auteur L3', 'Roman', 1),
(4, 'L4', 'Auteur L4', 'Roman', 1),
(5, 'L5', 'Auteur L5', 'Roman', 1)
ON CONFLICT (book_id) DO NOTHING;

-- 2. Création des rôles
--    role_id = 1 → BIBLIOTHECAIRE (gestionnaire de la bibliothèque)
--    role_id = 2 → ADHERENT (membre de la bibliothèque)
INSERT INTO role (role_id, role_name) VALUES
(1, 'BIBLIOTHECAIRE'),
(2, 'ADHERENT')
ON CONFLICT (role_id) DO UPDATE SET role_name = EXCLUDED.role_name;

-- 3. Création du bibliothécaire (admin)
INSERT INTO users (user_id, username, password, name) VALUES
(1, 'admin', '$2a$10$Q18M5eiqPXGAb4dkuujYyeaAgHu46TlVlL0zL/W8w5lwm6kQYyLK2', 'Bibliothécaire Principal')
ON CONFLICT (user_id) DO NOTHING;

-- 4. Attribution du rôle BIBLIOTHECAIRE à l'admin
INSERT INTO user_role (user_id, role_id) VALUES (1, 1)
ON CONFLICT DO NOTHING;

-- 5. Création des adhérents (A1, A2, A3)
INSERT INTO users (user_id, username, password, name) VALUES
(2, 'A1', '$2a$10$Q18M5eiqPXGAb4dkuujYyeaAgHu46TlVlL0zL/W8w5lwm6kQYyLK2', 'Adherent 1'),
(3, 'A2', '$2a$10$Q18M5eiqPXGAb4dkuujYyeaAgHu46TlVlL0zL/W8w5lwm6kQYyLK2', 'Adherent 2'),
(4, 'A3', '$2a$10$Q18M5eiqPXGAb4dkuujYyeaAgHu46TlVlL0zL/W8w5lwm6kQYyLK2', 'Adherent 3')
ON CONFLICT (user_id) DO NOTHING;

-- 6. Attribution du rôle ADHERENT aux adhérents
INSERT INTO user_role (user_id, role_id) VALUES
(2, 2),
(3, 2),
(4, 2)
ON CONFLICT DO NOTHING;

-- 7. Emprunts de L2 à L5 par A3
INSERT INTO borrow (user_id, book_id, issue_date, due_date) VALUES
(4, 2, NOW(), NOW() + INTERVAL '7 days'),
(4, 3, NOW(), NOW() + INTERVAL '7 days'),
(4, 4, NOW(), NOW() + INTERVAL '7 days'),
(4, 5, NOW(), NOW() + INTERVAL '7 days');

-- 8. Mettre à jour le stock des livres empruntés
UPDATE books SET no_of_copies = 0 WHERE book_id IN (2, 3, 4, 5);
