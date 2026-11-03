import { Routes } from '@angular/router';
import { LoginPage } from './features/auth/login';
import { AppShell } from './layout/app-shell';
import { DashboardPage } from './features/dashboard/dashboard';
import { VisiteursPage } from './features/visiteurs/visiteurs';
import { AdminPage } from './features/admin/admin';
import { AuditPage } from './features/audit/audit';
import { PasswordSetupPage } from './features/password-setup/password-setup';
import { authGuard, adminGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  { path: 'connexion', component: LoginPage },
  { path: 'definir-mot-de-passe', component: PasswordSetupPage },
  {
    path: '',
    component: AppShell,
    canActivate: [authGuard],
    children: [
      { path: '', component: DashboardPage },
      { path: 'visiteurs', component: VisiteursPage },
      { path: 'admin', component: AdminPage, canActivate: [adminGuard] },
      { path: 'audit', component: AuditPage, canActivate: [adminGuard] },
    ],
  },
  { path: '**', redirectTo: '' },
];
