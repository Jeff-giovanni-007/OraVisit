import { Component, DestroyRef, HostListener, computed, effect, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../core/services/auth.service';
import { ROLE_LABELS } from '../core/models/api.models';

const DRAWER_KEY = 'oravisit-drawer-open';
const COMPACT_QUERY = '(max-width: 900px)';

interface NavItem {
  path: string;
  label: string;
  icon: 'visite' | 'visiteurs' | 'employes' | 'audit';
}

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [CommonModule, RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app-shell.html',
  styleUrl: './app-shell.css',
  host: { '[class.compact]': 'compact()' },
})
export class AppShell {
  private media = window.matchMedia(COMPACT_QUERY);
  compact = signal(this.media.matches);
  drawerOpen = signal(this.media.matches ? false : readDrawerPreference());

  constructor(public auth: AuthService, private router: Router) {
    const onMediaChange = (event: MediaQueryListEvent) => {
      this.compact.set(event.matches);
      this.drawerOpen.set(event.matches ? false : readDrawerPreference());
    };
    this.media.addEventListener('change', onMediaChange);
    inject(DestroyRef).onDestroy(() => this.media.removeEventListener('change', onMediaChange));

    effect(() => {
      if (this.auth.currentUser() && !this.auth.isAdmin() && /^\/(admin|audit)\b/.test(this.router.url)) {
        this.router.navigateByUrl('/');
      }
    });
    this.auth.refreshCurrentUser().catch(() => undefined);
  }

  roleLabel = computed(() => {
    const role = this.auth.currentUser()?.role;
    return role ? ROLE_LABELS[role] : '';
  });

  nav = computed(() => {
    const items: NavItem[] = [
      { path: '/', label: 'Visites', icon: 'visite' },
      { path: '/visiteurs', label: 'Visiteurs', icon: 'visiteurs' },
    ];
    if (this.auth.isAdmin()) {
      items.push(
        { path: '/admin', label: 'Employés', icon: 'employes' },
        { path: '/audit', label: 'Audit', icon: 'audit' });
    }
    return items;
  });

  toggleDrawer() {
    const open = !this.drawerOpen();
    this.drawerOpen.set(open);
    if (!this.compact()) {
      try { localStorage.setItem(DRAWER_KEY, String(open)); } catch { }
    }
  }

  onNavigate() {
    if (this.compact()) this.drawerOpen.set(false);
  }

  @HostListener('document:keydown.escape')
  onEscape() {
    if (this.compact() && this.drawerOpen()) this.drawerOpen.set(false);
  }

  logout() {
    this.auth.logout();
    this.router.navigateByUrl('/connexion');
  }
}

function readDrawerPreference(): boolean {
  try {
    return localStorage.getItem(DRAWER_KEY) !== 'false';
  } catch {
    return true;
  }
}
