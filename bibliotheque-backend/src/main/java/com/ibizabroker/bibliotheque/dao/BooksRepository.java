package com.ibizabroker.bibliotheque.dao;

import com.ibizabroker.bibliotheque.entity.Books;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BooksRepository extends JpaRepository<Books, Integer> {
    Optional<Books> findByBookName(String bookName);
}
