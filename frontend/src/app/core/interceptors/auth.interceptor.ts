import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';

/** Routes publiques du backend : elles ne reçoivent pas le jeton (les autres routes /auth/, comme /auth/me, si). */
const PUBLIC_ROUTES = ['/auth/login', '/auth/password-setup'];

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  if (PUBLIC_ROUTES.some(route => req.url.includes(route))) {
    return next(req);
  }
  const auth = inject(AuthService);
  const router = inject(Router);
  const token = auth.getToken();
  if (token) {
    req = req.clone({
      setHeaders: { Authorization: `Bearer ${token}` },
    });
  }
  return next(req).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && error.status === 401 && auth.isLoggedIn()) {
        auth.logout();
        router.navigate(['/connexion'], { state: { sessionExpired: true } });
      } else if (error instanceof HttpErrorResponse && error.status === 403 && error.error?.code === 'ACCESS_DENIED') {
        // le rôle a pu être retiré depuis la connexion : on recale le menu et les gardes sur le serveur
        auth.refreshCurrentUser().catch(() => undefined);
      }
      return throwError(() => error);
    })
  );
};
