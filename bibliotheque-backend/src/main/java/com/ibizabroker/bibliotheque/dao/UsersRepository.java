package com.ibizabroker.bibliotheque.dao;

import com.ibizabroker.bibliotheque.entity.Users;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UsersRepository extends JpaRepository<Users, Integer> {

    // Jointure explicite FETCH pour charger les rôles en une seule requête SQL
    @Query("SELECT DISTINCT u FROM Users u LEFT JOIN FETCH u.role WHERE u.username = :username")
    Optional<Users> findByUsername(@Param("username") String username);
}