import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './login.html',
  styleUrl: './login.css',
})
export class LoginPage {
  matricule = '';
  password = '';
  // signaux : le composant est OnPush (défaut d'Angular 22), un simple champ modifié après l'appel HTTP
  // ne serait pas réaffiché
  erreur = signal('');
  loading = signal(false);

  constructor(private auth: AuthService, private router: Router) {
    if (auth.isLoggedIn()) router.navigateByUrl('/');
    const state = router.currentNavigation()?.extras.state;
    const matricule = state?.['matricule'];
    if (typeof matricule === 'string') this.matricule = matricule;
    if (state?.['sessionExpired']) this.erreur.set('Votre session a expiré. Reconnectez-vous.');
  }

  async submit() {
    this.erreur.set('');
    if (!this.matricule.trim() || !this.password) {
      this.erreur.set('Saisissez votre matricule et votre mot de passe.');
      return;
    }
    this.loading.set(true);
    const res = await this.auth.login(this.matricule.trim(), this.password);
    this.loading.set(false);
    if (res.error) { this.erreur.set(res.error); return; }
    this.router.navigateByUrl('/');
  }
}
