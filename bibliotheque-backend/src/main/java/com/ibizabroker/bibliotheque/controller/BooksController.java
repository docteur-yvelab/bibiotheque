package com.ibizabroker.bibliotheque.controller;

import com.ibizabroker.bibliotheque.dao.BooksRepository;
import com.ibizabroker.bibliotheque.entity.Books;
import com.ibizabroker.bibliotheque.exceptions.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/books")
public class BooksController {

    @Autowired
    private BooksRepository booksRepository;

    // GET /api/books — Accessible à tous les utilisateurs authentifiés
    @GetMapping
    public List<Books> getAllBooks() {
        return booksRepository.findAll();
    }

    // GET /api/books/{id}
    @GetMapping("/{id}")
    public ResponseEntity<Books> getBookById(@PathVariable Integer id) {
        Books book = booksRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Book with id " + id + " does not exist."));
        return ResponseEntity.ok(book);
    }

    // POST /api/books — Réservé au BIBLIOTHECAIRE
    @PreAuthorize("hasRole('BIBLIOTHECAIRE')")
    @PostMapping
    public Books createBook(@RequestBody Books book) {
        return booksRepository.save(book);
    }

    // PUT /api/books/{id} — Réservé au BIBLIOTHECAIRE
    @PreAuthorize("hasRole('BIBLIOTHECAIRE')")
    @PutMapping("/{id}")
    public ResponseEntity<Books> updateBook(@PathVariable Integer id, @RequestBody Books bookDetails) {
        Books book = booksRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Book with id " + id + " does not exist."));

        book.setBookName(bookDetails.getBookName());
        book.setBookAuthor(bookDetails.getBookAuthor());
        book.setBookGenre(bookDetails.getBookGenre());
        book.setNoOfCopies(bookDetails.getNoOfCopies());

        Books updatedBook = booksRepository.save(book);
        return ResponseEntity.ok(updatedBook);
    }

    // DELETE /api/books/{id} — Réservé au BIBLIOTHECAIRE
    @PreAuthorize("hasRole('BIBLIOTHECAIRE')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Boolean>> deleteBook(@PathVariable Integer id) {
        Books book = booksRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Book with id " + id + " does not exist."));

        booksRepository.delete(book);
        Map<String, Boolean> response = new HashMap<>();
        response.put("deleted", Boolean.TRUE);
        return ResponseEntity.ok(response);
    }
}