import { Component, OnInit } from '@angular/core';
import { NgForm } from '@angular/forms';
import { Router } from '@angular/router';
import { UserAuthService } from '../_service/user-auth.service';
import { UsersService } from '../_service/users.service';

@Component({
  selector: 'app-login',
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.css']
})
export class LoginComponent implements OnInit {

  errorMessage: string | null = null;

  constructor(private userService: UsersService,
    private userAuthSerivce: UserAuthService,
    private router: Router
  ) { }

  ngOnInit() {
  }

  login(loginForm: NgForm) {
    this.errorMessage = null;
    this.userService.login(loginForm.value).subscribe(
      (response: any) => {
        this.userAuthSerivce.setRoles(response.user.role);
        this.userAuthSerivce.setToken(response.jwtToken);
        this.userAuthSerivce.setUserId(response.user.userId);
        this.userAuthSerivce.setName(response.user.name);

        const role = response.user.role[0].roleName;
        if (role === 'Admin') {
          this.router.navigate(['/books']);
        } else {
          this.router.navigate(['/borrow-book']);
        }
      },
      (error) => {
        if (error.status === 401) {
          this.errorMessage = 'Nom d\'utilisateur ou mot de passe incorrect.';
        } else if (error.status === 0) {
          this.errorMessage = 'Impossible de joindre le serveur. Vérifiez que le backend est en cours d\'exécution.';
        } else {
          this.errorMessage = 'Une erreur est survenue lors de la connexion (HTTP ' + error.status + ').';
        }
      }
    );
  }

}
