import { Component, OnDestroy, OnInit, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { PasswordSetupInfo } from '../../core/models/api.models';
import { apiErrorMessage } from '../../core/services/api-error';
import { AuthService } from '../../core/services/auth.service';

const INVALID_LINK_MESSAGE =
  'Ce lien est invalide ou a expiré. Demandez un nouveau lien à votre administrateur.';
const NOTICE_DURATION_MS = 8000;

interface Notice {
  title: string;
  items: string[];
}

@Component({
  selector: 'app-password-setup',
  standalone: true,
  imports: [DatePipe, FormsModule],
  templateUrl: './password-setup.html',
  styleUrl: './password-setup.css',
})
export class PasswordSetupPage implements OnInit, OnDestroy {
  state = signal<'loading' | 'invalid' | 'form' | 'done'>('loading');
  info = signal<PasswordSetupInfo | null>(null);
  notice = signal<Notice | null>(null);
  invalidMessage = INVALID_LINK_MESSAGE;
  password = '';
  confirmation = '';
  saving = false;
  private token = '';
  private noticeTimer?: ReturnType<typeof setTimeout>;

  constructor(private route: ActivatedRoute, private router: Router, private auth: AuthService) {}

  private missingRules() {
    const p = this.password;
    const bytes = new TextEncoder().encode(p).length;
    return [
      { label: 'Entre 8 et 72 caractères (une lettre accentuée compte double)', ok: p.length >= 8 && bytes <= 72 },
      { label: 'Une lettre minuscule', ok: /[a-z]/.test(p) },
      { label: 'Une lettre majuscule', ok: /[A-Z]/.test(p) },
      { label: 'Un chiffre', ok: /\d/.test(p) },
    ].filter(rule => !rule.ok).map(rule => rule.label);
  }

  async ngOnInit() {
    this.token = this.route.snapshot.queryParamMap.get('token') ?? '';
    if (!this.token) {
      this.state.set('invalid');
      return;
    }
    try {
      this.info.set(await this.auth.validatePasswordSetupLink(this.token));
      this.state.set('form');
    } catch (e) {
      this.invalidMessage = apiErrorMessage(e, INVALID_LINK_MESSAGE);
      this.state.set('invalid');
    }
  }

  async submit() {
    this.closeNotice();
    const missing = this.missingRules();
    if (missing.length) {
      this.notify('Mot de passe non conforme', missing);
      return;
    }
    if (this.password !== this.confirmation) {
      this.notify('Les deux mots de passe ne correspondent pas.');
      return;
    }
    this.saving = true;
    try {
      await this.auth.definePassword(this.token, this.password);
      this.password = '';
      this.confirmation = '';
      this.state.set('done');
    } catch (e: any) {
      if (e?.error?.code === 'PASSWORD_SETUP_LINK_INVALID') {
        this.invalidMessage = apiErrorMessage(e, INVALID_LINK_MESSAGE);
        this.state.set('invalid');
      } else {
        this.notify(apiErrorMessage(e, 'Enregistrement impossible, veuillez réessayer.'));
      }
    } finally {
      this.saving = false;
    }
  }

  closeNotice() {
    clearTimeout(this.noticeTimer);
    this.notice.set(null);
  }

  ngOnDestroy() {
    clearTimeout(this.noticeTimer);
  }

  private notify(title: string, items: string[] = []) {
    clearTimeout(this.noticeTimer);
    this.notice.set({ title, items });
    this.noticeTimer = setTimeout(() => this.notice.set(null), NOTICE_DURATION_MS);
  }

  goToLogin() {
    this.router.navigate(['/connexion'], { state: { matricule: this.info()?.matricule } });
  }
}
