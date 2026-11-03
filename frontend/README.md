# OraVisit Frontend (Angular)

API backend : `http://localhost:8080/api/v1`

## Lancer

```bash
npm install
ng serve
```

Ouvrir http://localhost:4200

## Comptes

Au premier lancement de la base, un seul compte existe : l'administrateur
`ADM001`, mot de passe `Orabank@2026`. Les autres agents d'accueil et
administrateurs définissent leur mot de passe grâce au lien reçu par e-mail.

## Navigation

Quatre pages : **Visites** (`/`), **Visiteurs** (`/visiteurs`), puis **Employés**
(`/admin`) et **Audit** (`/audit`), réservées à l'administrateur. La page Visites
est la page d'accueil : elle porte les indicateurs du jour et la liste complète
des visites.

Le menu latéral (`src/app/layout/app-shell`) n'affiche que des icônes, dans un
panneau de 60 px replié par le bouton de l'en-tête :

| Page       | Icône                      | Lecture                                   |
|------------|----------------------------|-------------------------------------------|
| Visites    | calendrier avec coche      | des événements datés : arrivée, sortie, durée |
| Visiteurs  | carte d'identité avec photo| des fiches identifiées par une pièce      |
| Employés   | deux personnes             | un annuaire du personnel                  |
| Audit      | document avec loupe        | un journal que l'on consulte et examine   |

Ce sont des SVG écrits en ligne dans le gabarit, sans bibliothèque d'icônes :
ils héritent de `currentColor`, donc de la couleur du lien au repos, au survol et
à l'état actif. Chaque entrée de `nav()` porte une clé `icon` typée, et le
gabarit choisit le tracé par `@switch` — ajouter une page sans son icône est une
erreur de compilation.

Un menu d'icônes seules étant vite indéchiffrable, chaque lien porte le libellé
en `title` (infobulle) **et** en `aria-label` pour les lecteurs d'écran, le SVG
étant marqué `aria-hidden`. La zone cliquable fait 44 × 44 px, taille
recommandée pour une cible tactile.

## Fonctions

- Connexion JWT (matricule)
- Visites (page d'accueil) : indicateurs du jour, puis la liste des visites avec
  recherche, filtres statut, direction et dates, bouton Réinitialiser, export
  Excel des lignes filtrées, colonne Durée, détail d'une visite et check-out
- Nouvelle visite (création visiteur + pièce d'identité numérisée obligatoire +
  visite) ; la recherche d'un visiteur déjà enregistré y porte sur **tous** les
  visiteurs, y compris ceux d'un autre agent (voir « Ce que voit chaque rôle »)
- Annulation d'une visite en cours avec motif obligatoire, depuis le détail de
  la visite : composant `src/app/shared/visit-cancel`
- Visiteurs : fiches classées du plus grand nombre de visites au plus petit,
  recherche, pièces d'identité numérisées
- Employés (ADMIN) : poste, rôles, import et export Excel, envoi du lien de mot
  de passe ; le poste de l'hôte est aussi affiché dans les suggestions d'hôte et
  le détail d'une visite
- Audit (ADMIN) : qui a fait quoi et à quel moment, filtres action, objet et
  dates, détail d'une action et export Excel
- Modification d'un visiteur (page Visiteurs), d'une visite (ligne de la page
  Visites ou détail de la visite) et d'un employé (page Employés, ADMIN) dans une
  fenêtre modale : composants `src/app/shared/visitor-edit`, `visit-edit` et
  `employee-edit`
- Suppression, après confirmation, d'une visite ou d'un visiteur : un agent
  d'accueil supprime les siens, un administrateur supprime tout ; celle d'un
  employé reste réservée à l'administrateur
- Les confirmations (suppressions, changement de rôle) s'affichent dans une
  fenêtre modale et non dans une alerte du navigateur : service
  `ConfirmService` et composant `src/app/shared/confirm-dialog`, placé une
  seule fois dans `app.ts`. `await confirm.ask({ title, message, confirmLabel })`
  renvoie `true` si l'utilisateur confirme ; Échap, la croix, « Annuler » ou un
  clic hors de la fenêtre renvoient `false`
- Page publique `/definir-mot-de-passe?token=...` ouverte depuis l'e-mail reçu

Tous les tableaux utilisent le composant `app-pagination`
(`src/app/shared/pagination`) : la page et les filtres sont envoyés au
serveur, qui ne renvoie que la page demandée.

## Statuts d'une visite

`En cours`, `Terminée` et `Annulée`. Ils sont décrits une seule fois, dans
`src/app/core/models/api.models.ts` : `STATUS_LABEL` donne le libellé,
`STATUS_CLASS` la classe de la pastille (sa teinte est dans `src/styles.css`) et
`VISIT_STATUSES` alimente les listes déroulantes de filtre. Ajouter un statut se fait donc à un seul
endroit côté TypeScript, plus sa couleur dans `styles.css`.

Le bouton « Annuler la visite » n'apparaît que sur une visite `En cours`, dans le
détail de la visite. Le motif saisi n'apparaît pas dans la liste : il se consulte
dans le détail de la visite et figure dans l'export Excel.

## Ce que voit chaque rôle

Un agent d'accueil ne voit que les visites qu'il a enregistrées et les visiteurs
qu'il a créés **ou reçus au moins une fois** ; sans cette seconde condition, un
visiteur disparaîtrait de sa vue juste après qu'il lui a enregistré une visite.
Ses indicateurs du jour ne comptent que ses visites, et les trois cartes de
totaux généraux ne lui sont pas envoyées. De même, le nombre de visites d'un
visiteur (colonne « Vos visites » de la page Visiteurs, son tri, et la
recherche de la nouvelle visite) ne compte que les visites qu'il a enregistrées.
Un administrateur voit tout.

Le filtrage est fait par le serveur : les pages affichent ce qu'il renvoie, et
les sous-titres rappellent à l'agent que la liste est la sienne. Il ne peut
modifier, supprimer, ni consulter la pièce d'identité que des visites et des
visiteurs auxquels il a accès — une tentative par identifiant renvoie 403
(`VISIT_NOT_OWNED`, `VISITOR_NOT_OWNED`).

**L'exception, voulue :** pour enregistrer une visite, un agent doit pouvoir
retrouver un visiteur déjà connu de l'établissement, même créé par quelqu'un
d'autre. Le formulaire de nouvelle visite n'interroge donc pas `GET /visitors`
(cloisonné) mais `GET /visitors/lookup`, qui cherche dans **tous** les visiteurs
et renvoie au plus 8 fiches : nom, téléphone, type et numéro de pièce, de quoi
identifier la personne au guichet. La pièce d'identité numérisée, elle, reste
inaccessible tant que l'agent n'a pas reçu ce visiteur. Le visiteur
sélectionné n'apparaît dans sa page Visiteurs qu'une fois la visite enregistrée.

Les pages **Employés** et **Audit** restent réservées à l'administrateur, côté
menu et côté route via `adminGuard` (`src/app/core/guards/auth.guard.ts`), et
côté serveur via `@PreAuthorize`.

## Mots de passe

L'administrateur ne saisit aucun mot de passe. Un employé qui obtient le rôle
Agent d'accueil ou Administrateur reçoit un e-mail avec un lien sécurisé ; il
définit son mot de passe sur `/definir-mot-de-passe`, puis se connecte.

## Auth

Le token JWT est stocké dans `localStorage` clé `oravisit-token`
et envoyé en `Authorization: Bearer ...` sur chaque requête (sauf les routes
publiques `/auth/login` et `/auth/password-setup`).

Le rôle et le nom gardés à la connexion sont recalés sur le serveur
(`GET /auth/me`) à chaque ouverture de l'application et après un refus
`ACCESS_DENIED` : un administrateur à qui l'on retire ce rôle perd aussitôt les
pages Employés et Audit, sans attendre de se reconnecter.

Les dates et heures sont affichées dans le fuseau du backend (`environment.timezone`,
`+0000` pour Africa/Lome, via `DATE_PIPE_DEFAULT_OPTIONS`) : elles coïncident avec
les exports Excel, les e-mails et les bornes « aujourd'hui » du tableau de bord,
quel que soit le fuseau du poste.

`zone.js` est déclaré dans `polyfills` (`angular.json`) : c'est ce qui permet
au build de convertir `async/await` pour que la vue se mette à jour après
chaque appel à l'API.
