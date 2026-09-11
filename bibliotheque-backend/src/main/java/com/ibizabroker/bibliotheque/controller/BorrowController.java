package com.ibizabroker.bibliotheque.controller;

import com.ibizabroker.bibliotheque.dao.BooksRepository;
import com.ibizabroker.bibliotheque.dao.BorrowRepository;
import com.ibizabroker.bibliotheque.dao.UsersRepository;
import com.ibizabroker.bibliotheque.entity.Books;
import com.ibizabroker.bibliotheque.entity.Borrow;
import com.ibizabroker.bibliotheque.entity.Users;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

@RestController
@RequestMapping("/borrow")
public class BorrowController {

    @Autowired
    private BorrowRepository borrowRepository;

    @Autowired
    private UsersRepository usersRepository;

    @Autowired
    private BooksRepository booksRepository;

    @PostMapping
    @PreAuthorize("hasRole('BIBLIOTHECAIRE')")
    public String borrowBook(@RequestBody Borrow borrow) {
        Users user = usersRepository.findById(borrow.getUserId()).get();
        Books book = booksRepository.findById(borrow.getBookId()).get();

        if (book.getNoOfCopies() < 1) {
            return "The book \"" + book.getBookName() + "\" is out of stock!";
        }

        book.borrowBook();
        booksRepository.save(book);

        Date currentDate = new Date();
        Date overdueDate = new Date();
        Calendar c = Calendar.getInstance();
        c.setTime(overdueDate);
        c.add(Calendar.DATE, 7);
        overdueDate = c.getTime();
        borrow.setIssueDate(currentDate);
        borrow.setDueDate(overdueDate);
        borrowRepository.save(borrow);
        return user.getName() + " has borrowed one copy of \"" + book.getBookName() + "\"!";
    }

    @GetMapping
    @PreAuthorize("hasRole('BIBLIOTHECAIRE')")
    public List<Borrow> getAllBorrow() {
        return borrowRepository.findAll();
    }

    @PutMapping
    @PreAuthorize("hasRole('BIBLIOTHECAIRE')")
    public Borrow returnBook(@RequestBody Borrow borrow) {
        Borrow borrowBook = borrowRepository.findById(borrow.getBorrowId()).get();
        Books book = booksRepository.findById(borrowBook.getBookId()).get();

        book.returnBook();
        booksRepository.save(book);

        Date currentDate = new Date();
        borrowBook.setReturnDate(currentDate);
        return borrowRepository.save(borrowBook);
    }

    @GetMapping("user/{id}")
    @PreAuthorize("hasRole('BIBLIOTHECAIRE')")
    public List<Borrow> booksBorrowedByUser(@PathVariable Integer id) {
        return borrowRepository.findByUserId(id);
    }

    @GetMapping("book/{id}")
    @PreAuthorize("hasRole('BIBLIOTHECAIRE')")
    public List<Borrow> bookBorrowHistory(@PathVariable Integer id) {
        return borrowRepository.findByBookId(id);
    }
}
