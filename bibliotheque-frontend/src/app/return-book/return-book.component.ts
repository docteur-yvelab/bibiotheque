import { Component, OnInit } from '@angular/core';
import { Observable } from 'rxjs';
import { Books } from '../_model/books';
import { Borrow } from '../_model/borrow';
import { BooksService } from '../_service/books.service';
import { BorrowService } from '../_service/borrow.service';
import { UserAuthService } from '../_service/user-auth.service';

@Component({
  selector: 'app-return-book',
  templateUrl: './return-book.component.html',
  styleUrls: ['./return-book.component.css']
})
export class ReturnBookComponent implements OnInit {

  books: Books[] = [];
  borrow: Borrow[] = [];
  successMessage: string | null = null;
  errorMessage: string | null = null;

  constructor(
    private borrowService: BorrowService,
    private booksService: BooksService,
    private userAuthService: UserAuthService
  ) { }

  userId = this.userAuthService.getUserId();

  ngOnInit(): void {
    this.getBooks();
    this.getBooksByUser();
  }

  private getBooks() {
    this.booksService.getBooksList().subscribe(data => {
      this.books = data;
    });
  }

  private getBooksByUser() {
    this.borrowService.getBooksBorrowedByUser(this.userId).subscribe(data => {
      this.borrow = data;
    }, error => {
      if (error.status === 0) {
        this.errorMessage = 'Cannot reach the server. Please verify the backend is running.';
      } else if (error.status === 404) {
        this.errorMessage = 'No borrowed books found for your account.';
      } else {
        this.errorMessage = 'An error occurred while loading your borrowed books (HTTP ' + error.status + ').';
      }
    });
  }

  brw: Borrow = new Borrow();
  public returnBook(borrowId: number) {
    this.successMessage = null;
    this.errorMessage = null;
    this.brw.borrowId = borrowId;
    this.borrowService.returnBook(this.brw).subscribe(data => {
      this.successMessage = 'Book returned successfully!';
      this.getBooksByUser();
      setTimeout(() => this.successMessage = null, 4000);
    }, error => {
      if (error.status === 0) {
        this.errorMessage = 'Cannot reach the server. Please verify the backend is running.';
      } else {
        this.errorMessage = 'An error occurred while returning the book (HTTP ' + error.status + ').';
      }
      setTimeout(() => this.errorMessage = null, 6000);
    });
  }

}
