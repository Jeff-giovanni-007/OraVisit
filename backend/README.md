# OraVisit — Backend

API REST interne de gestion des visiteurs d'Orabank Togo. L'agent d'accueil
enregistre les visiteurs qui se présentent (avec leur pièce d'identité
numérisée), leur attribue un badge, suit les visites en cours, effectue les
check-out et consulte l'historique. L'administrateur gère en plus les comptes
du personnel.

L'API ne contient que ce qu'utilise le frontend OraVisit.

## Stack

- Java 21, Spring Boot 4.1
- Spring Web, Spring Data JPA, Spring Security (JWT HS256), Bean Validation
- PostgreSQL : 5 tables décrites dans `schema.sql`
- Apache POI pour l'export des visites, des employés et du journal d'audit, et
  l'import des employés (`.xlsx`)
- Spring Mail pour l'envoi des liens de définition du mot de passe

## Prérequis

- JDK 21 ou plus
- PostgreSQL 13 ou plus (développé et testé sur 18)
- Un serveur SMTP (en local : [Mailpit](https://mailpit.axllent.org/) ou MailHog sur le port 1025)
- Aucune installation de Maven : le wrapper `./mvnw` est fourni

## Démarrage rapide

```bash
# 1. Créer la base (les tables sont créées au démarrage)
createdb oravisit

# 2. Configurer l'environnement
cp .env.example .env   # puis renseigner la base, le secret du jeton et le SMTP

# 3. Lancer en profil dev
./mvnw spring-boot:run
```

L'API écoute sur `http://localhost:8080`. Au premier démarrage, `schema.sql`
crée les 5 tables. Si la table `employees` est vide (premier lancement), le
seul compte créé est l'administrateur `ADM001`, avec le mot de passe
`Orabank@2026` (stocké haché avec BCrypt).

## Configuration

Toutes les valeurs sensibles viennent de l'environnement. [.env.example](.env.example)
ne contient que ce qu'il faut renseigner (base, secret du jeton, SMTP) ; les
autres variables ci-dessous ont une valeur par défaut et ne se définissent
qu'en cas de besoin (production notamment) :

| Variable                            | Défaut (dev)                     | Rôle                                      |
|-------------------------------------|----------------------------------|-------------------------------------------|
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | base `oravisit` en local     | Connexion PostgreSQL                      |
| `APP_JWT_SECRET`                    | secret de dev                    | Signature HS256, **obligatoire en prod**  |
| `APP_JWT_EXPIRATION`                | `PT8H`                           | Durée de vie du jeton                     |
| `APP_CORS_ALLOWED_ORIGINS`          | `http://localhost:4200`          | Origines autorisées, séparées par virgule |
| `APP_TIMEZONE`                      | `Africa/Lome`                    | Fuseau des bornes de journée              |
| `APP_FRONTEND_URL`                  | `http://localhost:4200`          | Base des liens envoyés par e-mail         |
| `MAIL_HOST` / `MAIL_PORT`           | `localhost` / `1025`             | Serveur SMTP                              |
| `MAIL_USERNAME` / `MAIL_PASSWORD`   | vides                            | Authentification SMTP (avec `MAIL_SMTP_AUTH`, `MAIL_SMTP_STARTTLS`) |
| `APP_MAIL_FROM`                     | `OraVisit <no-reply@orabank.tg>` | Expéditeur des e-mails                    |
| `APP_MAIL_PREVIEW_HOST` / `APP_MAIL_PREVIEW_PORT` | `localhost` / `1025` en dev, vide en prod | SMTP qui reçoit une copie de chaque e-mail (Mailpit) |
| `APP_PASSWORD_SETUP_LINK_VALIDITY`  | `PT48H`                          | Validité d'un lien de mot de passe        |
| `SPRING_PROFILES_ACTIVE`            | `dev`                            | Profil actif (`prod` en production)       |
| `SERVER_PORT`                       | `8080`                           | Port HTTP de l'API                        |

Le fichier `.env` est lu dans le dossier de lancement, dans `backend/` (lancement
depuis la racine du projet, par exemple depuis l'IDE) ou dans le dossier parent.

Deux profils : `dev` (par défaut, logs DEBUG, liens de mot de passe écrits
dans les logs, copie des e-mails déposée dans Mailpit) et `prod` (aucune valeur
par défaut sensible, liens jamais journalisés, pas de copie).

En `dev`, chaque e-mail part d'abord par le SMTP configuré (`MAIL_*`, Gmail par
exemple), puis une copie est déposée sur `APP_MAIL_PREVIEW_HOST:APP_MAIL_PREVIEW_PORT`
pour en voir le rendu dans Mailpit (http://localhost:8025). La copie n'est pas
faite si ce SMTP est déjà le serveur principal, et son échec n'est qu'un
avertissement dans les logs.

```bash
SPRING_PROFILES_ACTIVE=prod ./mvnw spring-boot:run
```

## Rôles

| Rôle        | Accès                                                          |
|-------------|----------------------------------------------------------------|
| `ADMIN`     | Tout, dont la gestion des employés et le journal d'audit       |
| `RECEPTION` | **Ses** visiteurs et **ses** visites, check-out, tableau de bord |
| `DEFAULT`   | Employé visitable, aucune connexion possible                   |

## Cloisonnement des agents d'accueil

Un agent d'accueil ne voit et ne modifie que ce qu'il a enregistré ; un
administrateur voit et modifie tout. Le filtre est appliqué par le serveur, pas
par le client : la liste, l'export et les indicateurs du tableau de bord
reposent sur les mêmes critères.

| Donnée               | Visible par un agent d'accueil quand…                                           |
|----------------------|----------------------------------------------------------------------------------|
| Visite               | il l'a enregistrée (`visits.registered_by_id`)                                  |
| Visiteur             | il a créé la fiche (`visitors.created_by_id`) **ou** a enregistré une visite pour lui |
| Pièce d'identité     | le visiteur lui est visible                                                      |
| Nombre de visites d'un visiteur | toujours, mais il ne compte que les visites qu'il a enregistrées (liste, tri `visitCount`, autocomplétion) |
| Indicateurs du jour  | toujours : ils ne comptent que ses visites (les totaux généraux restent `ADMIN`) |

Deux précisions sur la deuxième règle :

- un visiteur peut se présenter au guichet d'un agent alors qu'un collègue avait
  créé sa fiche : la visite appartient à l'agent qui l'enregistre, et lui ouvre à
  son tour la fiche du visiteur. Enregistrer une visite ne demande donc pas que
  le visiteur soit déjà visible ;
- la fiche reste rattachée à son premier agent. Supprimer un employé ne supprime
  pas les fiches qu'il a créées : elles perdent leur agent créateur et ne
  restent visibles que d'un administrateur.

Une exception, voulue : **la recherche d'un visiteur à sélectionner n'est pas
cloisonnée**. `GET /visitors/lookup` cherche dans tous les visiteurs et renvoie
au plus 8 fiches (nom, téléphone, type et numéro de pièce, nombre de visites —
celles de l'agent seulement —, jamais l'e-mail), de quoi identifier la personne au guichet sans donner accès à
sa pièce numérisée. C'est ce qu'appelle
le formulaire de nouvelle visite, là où `GET /visitors`, qui alimente la liste,
reste cloisonné.

Agir sur la visite ou le visiteur d'un autre agent renvoie `403` avec le code
`VISIT_NOT_OWNED` ou `VISITOR_NOT_OWNED` — modification, suppression, check-out,
annulation, lecture et remplacement de la pièce d'identité. Supprimer un visiteur
supprimant aussi ses visites, un agent ne peut supprimer que les visiteurs reçus
par lui seul : si un collègue a enregistré une visite pour ce visiteur, la
suppression est refusée (`403 VISITOR_HAS_OTHER_AGENTS_VISITS`) et revient à un
administrateur. Les contraintes physiques restent
globales : un badge et un visiteur ne peuvent avoir qu'une visite `EN_COURS`
dans toute l'application, y compris si elle a été enregistrée par un collègue
(le message de conflit reste alors le même, sans révéler la visite).

## Journal d'audit

Chaque action qui touche aux données ou aux comptes est tracée dans
`audit_logs` : connexions (réussies et manquées), visiteurs, visites,
check-out, annulations (avec leur motif), pièces d'identité (enregistrement
**et** consultation), employés, rôles, liens de mot de passe, imports et
exports — l'export du journal compris.

Une ligne porte l'horodatage, l'action, l'issue (`SUCCESS`/`FAILURE`), l'auteur,
l'objet concerné, un résumé lisible et l'adresse IP d'origine. Le matricule, le
nom et le rôle de l'auteur sont **recopiés** dans la ligne : la trace reste
lisible après la suppression de son compte, et `actor_id` n'a donc volontairement
pas de clé étrangère vers `employees`.

Les lignes ne sont jamais modifiées ni supprimées : l'entité n'a pas de setters
et aucune route n'écrit dans le journal. Chaque écriture se fait dans une
transaction à part, afin qu'une tentative refusée (connexion manquée) soit tracée
bien que sa transaction soit annulée.

## Mots de passe des employés

L'administrateur ne saisit ni ne modifie jamais un mot de passe.

1. Lorsqu'un employé obtient un accès (création ou import avec le rôle
   `RECEPTION`/`ADMIN`, ou attribution de ce rôle), un lien est envoyé par
   e-mail à l'employé.
2. Le lien ouvre la page `/definir-mot-de-passe` du frontend, où l'employé
   choisit son mot de passe (8 à 72 caractères, une minuscule, une majuscule,
   un chiffre ; BCrypt limitant à 72 octets, une lettre accentuée compte double).
3. Il peut ensuite se connecter avec son matricule.

Le lien est un jeton signé (comme le jeton de connexion), valable 48 h par
défaut : rien n'est stocké en base. Il contient une empreinte du mot de passe
actuel et devient donc inutilisable dès qu'un mot de passe est défini. Il est
aussi refusé si le compte n'a plus d'accès.
L'administrateur peut envoyer un nouveau lien (lien expiré, mot de passe oublié).
Retirer l'accès (rôle `DEFAULT`) efface le mot de passe et déconnecte
immédiatement l'employé : c'est la façon de bloquer un compte.
L'e-mail part après la validation de la transaction, en arrière-plan : une
panne SMTP est journalisée sans bloquer l'administrateur.

## Endpoints

Toutes les routes exigent un en-tête `Authorization: Bearer <jeton>`, sauf
les trois routes publiques de `/auth`.

### Authentification — `/api/v1/auth` (public)

| Méthode | Route                      | Description                                        |
|---------|----------------------------|----------------------------------------------------|
| POST    | `/login`                   | Connexion, renvoie le jeton (8 h)                  |
| GET     | `/me`                      | Compte connecté (rôle, nom) tel qu'il est en base — **jeton requis** |
| POST    | `/password-setup/validate` | Vérifie un lien, renvoie le matricule concerné     |
| POST    | `/password-setup`          | Définit le mot de passe à partir du lien           |

### Visiteurs — `/api/v1/visitors` (ADMIN, RECEPTION)

Un agent d'accueil ne reçoit et ne modifie que ses visiteurs (voir
[Cloisonnement des agents d'accueil](#cloisonnement-des-agents-daccueil)).

| Méthode | Route                        | Description                                          |
|---------|------------------------------|------------------------------------------------------|
| GET     | `/`                          | Recherche paginée (`q` : un ou plusieurs mots parmi nom, téléphone, n° de pièce ; `identityDocumentType` + `identityCardNumber` : pièce exacte) — **cloisonnée** |
| GET     | `/lookup`                    | Mêmes critères, 8 résultats au plus, **sans cloisonnement** : sert à retrouver un visiteur déjà enregistré au moment de créer une visite |
| POST    | `/`                          | Créer une fiche (rattachée à son auteur)             |
| PUT     | `/{id}`                      | Modifier une fiche (nom, coordonnées, pièce)         |
| PUT     | `/{id}/identity-document`    | Envoyer la pièce d'identité (multipart `file` : JPEG, PNG, WEBP ou PDF, 5 Mo max) |
| GET     | `/{id}/identity-document`    | Télécharger la pièce d'identité                      |
| DELETE  | `/{id}`                      | Supprimer le visiteur, sa pièce et ses visites (un agent d'accueil : les siens) |

### Visites — `/api/v1/visits` (ADMIN, RECEPTION)

Un agent d'accueil ne reçoit, ne modifie et n'exporte que les visites qu'il a
enregistrées.

| Méthode | Route             | Description                                              |
|---------|-------------------|----------------------------------------------------------|
| POST    | `/`               | Enregistrer une visite (statut `EN_COURS`) : multipart `visit` (JSON) + `identityDocument` (pièce d'identité, facultative seulement si le visiteur en a déjà une) |
| PUT     | `/{id}`           | Modifier une visite (employé hôte, motif, badge)         |
| GET     | `/`               | Recherche paginée (`q` : un ou plusieurs mots parmi visiteur, hôte, badge, motif ; `status`, `direction`, `dateFrom`, `dateTo`) |
| GET     | `/export`         | Export `.xlsx` avec les mêmes filtres                    |
| PATCH   | `/{id}/checkout`  | Check-out : `EN_COURS` → `TERMINEE`                      |
| PATCH   | `/{id}/cancel`    | Annulation : `EN_COURS` → `ANNULEE`, corps `{ "reason": "..." }` (motif obligatoire, 255 caractères) |
| DELETE  | `/{id}`           | Supprimer une visite (un agent d'accueil : les siennes)   |

### Employés — `/api/v1/employees` (ADMIN)

| Méthode | Route                        | Description                                     |
|---------|------------------------------|-------------------------------------------------|
| GET     | `/`                          | Recherche paginée (`q` : un ou plusieurs mots parmi matricule, nom, email, poste ; `role`, `direction`) |
| GET     | `/export`                    | Export `.xlsx` des employés (mêmes filtres ; tous si aucun filtre) |
| GET     | `/lookup`                    | Autocomplétion des employés — **aussi RECEPTION** |
| POST    | `/`                          | Créer un compte (sans mot de passe)             |
| POST    | `/import`                    | Import `.xlsx` : colonnes `matricule`, `firstName`, `lastName`, `email`, `phone`, `direction`, `poste` (obligatoires) et `role` (facultatif, `DEFAULT` si vide), ou les intitulés du fichier exporté (un export peut être réimporté). Rôle et direction : code (`RECEPTION`, `DIRECTION_RISQUES`) ou libellé complet (`Agent d'accueil`, `Risques`) ; le poste est un intitulé libre (100 caractères, colonne `poste` ou `Fonction`) |
| PUT     | `/{id}`                      | Modifier un employé (matricule, nom, email, téléphone, direction, poste) |
| PATCH   | `/{id}/role`                 | Attribuer un rôle                               |
| POST    | `/{id}/password-setup-link`  | Envoyer un lien de définition du mot de passe   |
| DELETE  | `/{id}`                      | Supprimer un employé                            |

### Journal d'audit — `/api/v1/audit` (ADMIN)

| Méthode | Route      | Description                                                    |
|---------|------------|----------------------------------------------------------------|
| GET     | `/`        | Recherche paginée, triée du plus récent au plus ancien (`q` : un ou plusieurs mots parmi auteur, matricule, objet concerné, détails ; `action`, `targetType`, `actorId`, `dateFrom`, `dateTo`) |
| GET     | `/export`  | Export `.xlsx` avec les mêmes filtres                          |

Les exports (visites, employés, audit) sont écrits en flux et acceptent jusqu'à
100 000 lignes ; au-delà, la requête est refusée (`422 EXPORT_TOO_LARGE`) et il
faut affiner les filtres.

### Tableau de bord — `/api/v1/dashboard` (ADMIN, RECEPTION)

`GET /` renvoie les indicateurs du jour : visites enregistrées, visiteurs
présents, visites terminées et visites annulées. Pour un agent d'accueil, ils ne
comptent que ses visites. Pour un ADMIN, ils portent sur toutes les visites, et
la réponse ajoute le nombre total de visiteurs, de visites et d'employés.

`visitsToday` couvre les trois statuts : les visites du jour se répartissent
entre celles encore en cours, les terminées et les annulées. `activeVisits`
compte en revanche les visiteurs présents quelle que soit leur date d'arrivée.

## Règles métier

- Un agent d'accueil ne voit et ne modifie que ses visiteurs et ses visites ; un
  administrateur voit et modifie tout (voir
  [Cloisonnement des agents d'accueil](#cloisonnement-des-agents-daccueil)).
- Toute action est tracée dans le journal d'audit, que seul un administrateur
  consulte (voir [Journal d'audit](#journal-daudit)).
- Un numéro de badge ne peut équiper qu'une seule visite `EN_COURS`, dans toute
  l'application.
- Un visiteur ne peut avoir qu'une seule visite `EN_COURS`, dans toute
  l'application.
- Pas de visite sans pièce d'identité numérisée en base : elle est envoyée avec
  la visite et enregistrée dans la même transaction (une visite refusée ne
  modifie pas la fiche du visiteur). Un visiteur qui revient avec une pièce déjà
  enregistrée n'a pas à la renvoyer ; un nouveau fichier la remplace.
- Un visiteur est identifié par le couple (type, numéro) de sa pièce : s'il
  revient, sa fiche existante est mise à jour et réutilisée. À l'enregistrement
  d'une visite, l'agent recherche et sélectionne directement la fiche du visiteur
  qui revient : aucune nouvelle ligne n'est créée dans `visitors`.
- La direction d'une visite est copiée depuis l'employé hôte par le serveur ;
  elle n'est jamais saisie par le client, pas plus que le statut, les
  horodatages (y compris celui de l'annulation) ou l'agent enregistreur. Seul
  le motif de l'annulation vient du client.
- Une visite a trois statuts, et `EN_COURS` est le seul dont elle puisse sortir :
  - `EN_COURS` → `TERMINEE` par le check-out, qui inscrit l'heure de sortie ;
  - `EN_COURS` → `ANNULEE` pour une visite qui n'a pas eu lieu, avec un **motif
    d'annulation obligatoire** et l'heure de l'annulation. Une visite annulée n'a
    jamais d'heure de sortie, donc pas de durée, et elle libère aussitôt son badge
    et son visiteur (les index uniques ne portent que sur `EN_COURS`) ;
  - une visite `TERMINEE` ou `ANNULEE` ne change plus de statut : un second
    check-out ou une seconde annulation est refusé (`VISIT_NOT_ACTIVE`). Pour
    effacer une visite enregistrée par erreur, il faut la supprimer.
  Le motif d'annulation reste lisible sur la visite, dans l'export `.xlsx`
  (colonnes « Annulée le » et « Motif de l'annulation ») et dans le journal d'audit.
- Modifications (ADMIN et RECEPTION pour les visiteurs et les visites, ADMIN
  seul pour les employés) :
  - un visiteur ne peut pas prendre la pièce (type, numéro) d'un autre visiteur ;
  - d'une visite, seuls l'employé hôte, le motif et le badge changent : le
    visiteur, le statut, les heures d'arrivée et de sortie et l'annulation
    restent ceux enregistrés. Changer d'hôte recopie sa direction ; une visite
    `EN_COURS` ne peut pas prendre le badge d'une autre visite `EN_COURS` ;
  - un employé garde un matricule et un email uniques, et un poste (intitulé
    obligatoire, par exemple « Chargée de clientèle particuliers »). Son rôle se change à
    part (`PATCH /{id}/role`). Un ADMIN ne peut pas modifier son propre
    matricule (son jeton de connexion le contient). Si le matricule ou l'email
    d'un compte dont le mot de passe n'est pas encore défini change, un
    nouveau lien est envoyé.
- La suppression est définitive. Un agent d'accueil supprime les visites et les
  visiteurs auxquels il a accès, un ADMIN supprime tout ; les employés restent
  réservés à l'ADMIN :
  - une visite est supprimée seule ;
  - un visiteur est supprimé avec sa pièce d'identité et toutes ses visites,
    sauf s'il a une visite `EN_COURS` (enregistrer d'abord sa sortie) ; un agent
    d'accueil ne peut pas supprimer un visiteur qu'un collègue a aussi reçu ;
  - un employé ne peut être supprimé que s'il n'est lié à aucune visite (ni
    comme hôte, ni comme agent enregistreur) ; sinon, on lui retire l'accès
    avec le rôle Employé. Un ADMIN ne peut pas supprimer son propre compte. Les
    fiches de visiteurs qu'il avait créées restent, sans agent créateur.
- Le journal d'audit ne se supprime ni ne se modifie : aucune route n'écrit
  dedans, et la suppression d'un compte n'efface pas ses traces.
- Un ADMIN ne peut pas modifier son propre rôle : il reste donc toujours au
  moins un administrateur.
- Le format d'une pièce d'identité est déduit du contenu du fichier, jamais de
  son nom ni du type annoncé par le navigateur.

Les règles sur le badge et le visiteur unique sont doublées d'index uniques
partiels en base (`WHERE status = 'EN_COURS'`) : la vérification applicative
donne un message clair, l'index protège des accès concurrents. La cohérence de
l'annulation l'est aussi, par la contrainte `ck_visits_cancellation` : motif et
horodatage sont présents si et seulement si le statut est `ANNULEE`, et une
visite annulée n'a pas d'heure de sortie.

## Réponses

Les listes sont paginées côté serveur (`page`, `size` ≤ 100, `sort`) et
enveloppées :

```json
{ "content": [], "page": 0, "size": 20, "totalElements": 137,
  "totalPages": 7 }
```

Les erreurs suivent le format RFC 7807, enrichi d'un `code` stable destiné au
frontend et, le cas échéant, du détail par champ :

```json
{ "status": 409, "detail": "Ce numéro de badge est déjà attribué à une visite en cours.",
  "code": "BADGE_ALREADY_IN_USE" }
```

## Structure

```
src/main/java/com/orabank/backend/
├── config/       AppProperties, SecurityConfig
├── controller/   6 contrôleurs REST
├── dto/          records d'entrée/sortie, avec leurs fabriques from(entity)
├── entity/       Employee, Visitor, Visit, pièces d'identité, AuditLog, énumérations
├── exception/    ApiException, GlobalExceptionHandler
├── export/       génération des classeurs .xlsx (visites, employés, audit)
├── mail/         envoi des liens de définition du mot de passe
├── repository/   repositories Spring Data
├── security/     JWT, filtre d'authentification, utilisateur courant et sa portée
└── service/      règles métier, dont AuditService

src/main/resources/schema.sql      les 5 tables et leurs index
```

La base contient 5 tables : `employees`, `visitors`, `visits`,
`visitor_identity_documents` et `audit_logs`. `schema.sql` est exécuté à chaque
démarrage (`CREATE ... IF NOT EXISTS` : sans effet si les tables existent) ;
Hibernate est en `ddl-auto: validate` et vérifie seulement qu'elles
correspondent aux entités. Pour faire évoluer une table existante, appliquer le
`ALTER TABLE` à la main puis mettre `schema.sql` à jour — ou l'écrire en
`ADD COLUMN IF NOT EXISTS` dans `schema.sql`, comme `visitors.created_by_id`
ou `employees.poste` (les employés déjà présents reçoivent le poste « Non
renseigné », à corriger depuis la page Employés),
puisque le script est rejoué à chaque démarrage. Sur une base créée avant le
cloisonnement, cette colonne est ajoutée sans clé étrangère (`ADD CONSTRAINT`
n'étant pas idempotent) et renseignée depuis la première visite de chaque
visiteur.

## Bon à savoir

- Le projet ne contient pas de tests automatisés.
- Il n'expose ni Swagger UI ni endpoint Actuator : les seules routes sont
  celles listées ci-dessus.
