import { Injectable, signal, computed } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthUser, LoginResponse, PasswordSetupInfo } from '../models/api.models';
import { apiErrorMessage } from './api-error';

const TOKEN_KEY = 'oravisit-token';
const USER_KEY = 'oravisit-user';

@Injectable({ providedIn: 'root' })
export class AuthService {
  currentUser = signal<AuthUser | null>(null);
  token = signal<string | null>(null);

  isLoggedIn = computed(() => !!this.currentUser() && !!this.token());
  isAdmin = computed(() => this.currentUser()?.role === 'ADMIN');

  constructor(private http: HttpClient) {
    const token = localStorage.getItem(TOKEN_KEY);
    const raw = localStorage.getItem(USER_KEY);
    if (token && raw) {
      try {
        this.token.set(token);
        this.currentUser.set(JSON.parse(raw));
      } catch {
        this.clear();
      }
    } else {
      this.clear();
    }
  }

  async login(matricule: string, password: string): Promise<{ error?: string }> {
    try {
      const { accessToken, user } = await firstValueFrom(
        this.http.post<LoginResponse>(`${environment.apiUrl}/auth/login`, {
          matricule: matricule.trim(),
          password,
        })
      );
      localStorage.setItem(TOKEN_KEY, accessToken);
      localStorage.setItem(USER_KEY, JSON.stringify(user));
      this.token.set(accessToken);
      this.currentUser.set(user);
      return {};
    } catch (e: any) {
      if (e?.status === 0) {
        return { error: 'Serveur injoignable. Vérifiez votre connexion et réessayez.' };
      }
      return { error: apiErrorMessage(e, 'Matricule ou mot de passe incorrect.') };
    }
  }

  validatePasswordSetupLink(token: string) {
    return firstValueFrom(
      this.http.post<PasswordSetupInfo>(`${environment.apiUrl}/auth/password-setup/validate`, { token })
    );
  }

  definePassword(token: string, password: string) {
    return firstValueFrom(
      this.http.post<void>(`${environment.apiUrl}/auth/password-setup`, { token, password })
    );
  }

  /** Recale le rôle et le nom sur le serveur : ils ont pu changer depuis la connexion. */
  async refreshCurrentUser(): Promise<void> {
    const user = await firstValueFrom(this.http.get<AuthUser>(`${environment.apiUrl}/auth/me`));
    if (!this.token()) return;
    localStorage.setItem(USER_KEY, JSON.stringify(user));
    this.currentUser.set(user);
  }

  updateCurrentUser(changes: Partial<AuthUser>) {
    const user = this.currentUser();
    if (!user) return;
    const updated = { ...user, ...changes };
    localStorage.setItem(USER_KEY, JSON.stringify(updated));
    this.currentUser.set(updated);
  }

  getToken(): string | null {
    return this.token() || localStorage.getItem(TOKEN_KEY);
  }

  logout() {
    this.clear();
  }

  private clear() {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    this.token.set(null);
    this.currentUser.set(null);
  }
}
