# OraVisit — Gestion des visites (Orabank Togo)

Application de gestion des visiteurs : enregistrement des visites et des
visiteurs (avec pièce d'identité numérisée), suivi des entrées et sorties,
annulation motivée d'une visite, tableau de bord, export Excel, gestion des
employés et journal d'audit.

Chaque agent d'accueil ne voit que les visites qu'il a enregistrées et les
visiteurs qu'il a créés ou reçus. L'administrateur voit l'ensemble et dispose
d'une section **Audit** qui retrace, action par action, qui a fait quoi et à
quel moment.

| Dossier                  | Contenu                                       | Documentation |
|--------------------------|-----------------------------------------------|---------------|
| [`backend/`](backend)    | Spring Boot 4.1, Java 21, PostgreSQL          | [README](backend/README.md) |
| [`frontend/`](frontend)  | Angular 22                                    | [README](frontend/README.md) |

## Aperçu

Les pages ci-dessous sont décrites telles que les voit le compte administrateur.
Un agent d'accueil retrouve les pages Visites et Visiteurs, limitées à ses
propres données, sans les pages Employés et Audit ni les totaux généraux.

### Connexion

On se connecte avec son **matricule** et son mot de passe ; la session dure 8 h.
Seuls les administrateurs et les agents d'accueil ont un accès : un employé au
rôle Employé peut être reçu en visite, mais ne se connecte pas.

### Visites (page d'accueil)

- **Indicateurs** : visites du jour, visiteurs encore présents, visites
  terminées et annulées dans la journée. L'administrateur voit en plus le total
  des visiteurs, des visites et des employés.
- **Recherche et filtres** : visiteur, hôte, badge ou motif, puis statut,
  direction et période ; *Réinitialiser* efface le tout.
- **Liste** : arrivée, visiteur, badge, employé hôte et sa direction, motif,
  statut (*En cours*, *Terminée*, *Annulée*) et durée de la visite.
- **Actions** : *Détail* ouvre la fiche de la visite, d'où l'on marque la sortie
  ou annule une visite en cours (motif obligatoire) ; *Modifier* change l'hôte,
  le motif ou le badge ; *Supprimer* demande confirmation.
- **Enregistrer une visite** : retrouver un visiteur déjà venu ou en créer un,
  joindre sa pièce d'identité numérisée, puis choisir l'hôte et le badge.
  **Télécharger en Excel** exporte les visites filtrées.

### Visiteurs

Les fiches sont classées du visiteur le plus fréquent au moins fréquent et se
recherchent par nom, téléphone ou numéro de pièce. *Voir* affiche la pièce
d'identité numérisée (*Ajouter* quand la fiche n'en a pas encore) ; *Modifier*
et *Supprimer* agissent sur la fiche. Pour un agent d'accueil, la colonne
devient *Vos visites* et ne compte que les visites qu'il a enregistrées.

### Employés (administrateur)

- **Import Excel** : un fichier `.xlsx` ajoute des employés en masse.
- **Ajouter un employé** : matricule, identité, contact, rôle, direction et
  poste. L'administrateur ne saisit jamais de mot de passe.
- **Liste des employés** : recherche, filtres par rôle et par direction, export
  Excel. Le rôle se change directement dans la liste ; la colonne *Mot de passe*
  indique, pour les comptes qui ont un accès, s'il est *Défini* ou *En attente*,
  avec un bouton pour renvoyer le lien.

### Audit (administrateur)

Le journal retrace chaque action : date et heure, auteur (nom, matricule et
rôle), action effectuée et objet concerné. Il se filtre par texte, type
d'action, type d'objet et période, s'exporte en Excel, et *Détail* affiche la
trace complète. Il ne peut être ni modifié ni supprimé.

### Accès d'un nouvel agent

Quand un employé reçoit le rôle Agent d'accueil ou Administrateur, il reçoit un
e-mail avec son matricule et un lien personnel, à usage unique et valable 48 h.

Le lien ouvre la page publique `/definir-mot-de-passe`, où il choisit son mot de
passe (8 caractères au moins, avec une minuscule, une majuscule et un chiffre)
avant de se connecter.

## Prérequis

- JDK 21, Node.js 22.22+ ou 24, PostgreSQL 13+ (développé sur 18)
- [Mailpit](https://mailpit.axllent.org/) pour voir le rendu des e-mails en développement

## Démarrage rapide

```bash
# Backend (http://localhost:8080)
cd backend
cp .env.example .env      # renseigner la base, le secret du jeton et le SMTP
./mvnw spring-boot:run

# Frontend (http://localhost:4200)
cd frontend
npm install

# Mailpit (SMTP sur le port 1025, interface sur http://localhost:8025)
mailpit
```

Sous Windows : `.\mvnw.cmd spring-boot:run` (avec `JAVA_HOME` pointant vers le
JDK 21) et `npm.cmd start` si PowerShell bloque l'exécution de `npm.ps1`.

## E-mails

Les liens de définition du mot de passe partent par le serveur SMTP configuré
dans `backend/.env` (`MAIL_*`, par exemple Gmail). En profil `dev`, une copie de
chaque e-mail est aussi déposée dans Mailpit (`localhost:1025`) : le rendu se
consulte sur http://localhost:8025, que l'envoi réel ait réussi ou non (voir
[Accès d'un nouvel agent](#accès-dun-nouvel-agent)). Sans
Mailpit, la copie est simplement ignorée, et le lien reste écrit dans les logs
du backend.

