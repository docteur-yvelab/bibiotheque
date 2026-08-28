import { Component, OnInit } from '@angular/core';
import { Books } from '../_model/books';
import { Borrow } from '../_model/borrow';
import { BooksService } from '../_service/books.service';
import { BorrowService } from '../_service/borrow.service';
import { UserAuthService } from '../_service/user-auth.service';

@Component({
  selector: 'app-borrow-book',
  templateUrl: './borrow-book.component.html',
  styleUrls: ['./borrow-book.component.css']
})
export class BorrowBookComponent implements OnInit {

  books: Books[] = [];
  successMessage: string | null = null;
  errorMessage: string | null = null;

  constructor(
    private booksService: BooksService,
    private userAuthService: UserAuthService,
    private borrowService: BorrowService,
  ) { }

  userId = this.userAuthService.getUserId();

  ngOnInit(): void {
    this.getBooks();
  }

  private getBooks() {
    this.booksService.getBooksList().subscribe(data => {
      this.books = data;
    });
  }

  borrow: Borrow = new Borrow();

  borrowBook(bookId: number) {
    this.successMessage = null;
    this.errorMessage = null;
    this.borrow.bookId = bookId;
    this.borrow.userId = this.userId;
    this.borrowService.borrowBook(this.borrow).subscribe(data => {
      this.successMessage = 'Book borrowed successfully!';
      this.getBooks();
      setTimeout(() => this.successMessage = null, 4000);
    }, error => {
      if (error.status === 409) {
        this.errorMessage = error.error?.message || 'Cannot borrow this book. It may already be borrowed or no copies are available.';
      } else if (error.status === 0) {
        this.errorMessage = 'Cannot reach the server. Please verify the backend is running.';
      } else {
        this.errorMessage = 'An unexpected error occurred (HTTP ' + error.status + ').';
      }
      setTimeout(() => this.errorMessage = null, 6000);
    });
  }
}
