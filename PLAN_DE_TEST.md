# Plan de test — Backend `etudiant-backend`

## 1. Contexte et périmètre

Le backend est une API REST **Spring Boot 3.5 (Java 21)** avec base **MySQL** (Docker) et
sécurité **JWT**. Il gère :

- les comptes utilisateurs (agents de la bibliothèque) : `POST /api/register`, `POST /api/login` (retourne un JWT) ;
- les étudiants : `GET/POST /api/students`, `GET/PUT/DELETE /api/students/{id}` (accès protégé par JWT).

**Objectif du plan** : documenter et valider tous les cas de test du backend, organisés selon la
pyramide de test. Chaque cas précise l'entrée (données de départ) et la sortie attendue.

## 2. Pyramide de test et outils

```
           /\
          /E2E\         API réelle vue par un utilisateur réel
         /------\       (Cypress « réel » du projet frontend)
        / Intégr \      Toute la pile : HTTP → service → JPA → MySQL
       /----------\     (Spring Boot + MockMvc + Testcontainers)
      /  Unitaires \    Seules les classes de service, sans Spring ni BDD
     /--------------\   (JUnit 5 + Mockito)
```

| Niveau | Outils | Pré-requis | Commande |
|---|---|---|---|
| Unitaires | JUnit 5 + Mockito (dépendances mockées) | JDK 21, Maven | `mvn test -Dtest='*Test'` |
| Intégration | Spring Boot + MockMvc + Testcontainers (MySQL 8.4 réel) | **Docker** | `mvn test -Dtest='*IT'` |
| E2E | Suite Cypress « réelle » du frontend | pile complète démarrée | `npm run cy:real` (projet frontend) |

Exécution complète du backend : `mvn clean test`.

## 3. Priorités des cas de test

Les cas sont classés de P1 à P3, du **plus simple au plus complexe** (ordre respecté dans les tableaux) :

| Priorité | Signification | Exemple |
|---|---|---|
| **P1** | Comportement de base (cas nominal) | Créer un étudiant valide |
| **P2** | Gestion des erreurs et de la validation | E-mail en double → 409 |
| **P3** | Cas complexes et sécurité | Jeton JWT expiré / falsifié, flux complet |

---

## 4. Niveau 1 — Tests unitaires (22 cas)

Isolés : **pas de contexte Spring, pas de base de données, pas de Docker**.
Seule la classe testée est réelle ; ses dépendances (repository, encodeur de mot de passe,
service JWT) sont des faux (mocks Mockito). Exécution en quelques millisecondes.

### 4.1 `UserService` — fichier `src/test/java/.../service/UserServiceTest.java` (5 cas)

| ID | Cas | Priorité | Entrée (données de départ) | Sortie attendue |
|---|---|---|---|---|
| U-US-01 | Inscription d'un nouvel utilisateur | P1 | Utilisateur `john / password` ; aucun utilisateur existant avec le login `john` ; l'encodeur transforme le mot de passe en `ENCODED` | L'utilisateur est **enregistré** ; le mot de passe stocké est l'encodé (`ENCODED`) |
| U-US-02 | Inscription avec un login déjà pris | P2 | Le login `john` existe déjà | Exception `IllegalArgumentException` « User with login john already exists » ; **rien n'est enregistré** |
| U-US-03 | Connexion avec de bonnes identifiants | P1 | Utilisateur existant ; le mot de passe correspond | Renvoie le **JWT** généré par le service |
| U-US-04 | Connexion avec un mauvais mot de passe | P2 | Utilisateur existant ; mot de passe erroné | Exception `IllegalArgumentException` « Invalid credentials » |
| U-US-05 | Connexion avec un login inconnu | P2 | Le login n'existe pas | Exception `IllegalArgumentException` « Invalid credentials » |

### 4.2 `StudentService` — fichier `src/test/java/.../service/StudentServiceTest.java` (9 cas)

| ID | Cas | Priorité | Entrée | Sortie attendue |
|---|---|---|---|---|
| U-ST-01 | Création d'un étudiant valide | P1 | `Ada / Lovelace / ada@test.com / 36` | `StudentResponse` avec les mêmes valeurs (mapping entité ↔ DTO correct) |
| U-ST-02 | Création avec un e-mail en double | P2 | La base refuse l'insertion (e-mail unique) | L'erreur de base est traduite en `EmailAlreadyExistsException` |
| U-ST-03 | Liste de tous les étudiants | P1 | 2 étudiants dans le repository | Liste de 2 `StudentResponse` avec les bonnes valeurs |
| U-ST-04 | Récupération d'un étudiant existant | P1 | ID = 1 (existe) | `StudentResponse` « Ada Lovelace, 36 » |
| U-ST-05 | Récupération d'un étudiant inconnu | P2 | ID = 99 (n'existe pas) | Exception `ResponseStatusException` avec statut **404** |
| U-ST-06 | Modification d'un étudiant existant | P1 | ID = 1 ; nouvelles valeurs (Augusta / King / 37) | L'entité est modifiée et **enregistrée** ; la réponse contient les nouvelles valeurs |
| U-ST-07 | Modification d'un étudiant inconnu | P2 | ID = 99 | 404 ; **rien n'est enregistré** |
| U-ST-08 | Suppression d'un étudiant existant | P1 | ID = 1 (existe) | `delete()` est appelé sur le repository |
| U-ST-09 | Suppression d'un étudiant inconnu | P2 | ID = -12 | 404 ; **rien n'est supprimé** |

### 4.3 `JwtService` — fichier `src/test/java/.../service/JwtServiceTest.java` (9 cas)

Objet réel construit **sans Spring** (champs injectés par reflection) ; le service ne fait que des calculs.

| ID | Cas | Priorité | Entrée | Sortie attendue |
|---|---|---|---|---|
| U-JW-01 | Génération d'un jeton | P1 | Utilisateur `john` ; clé secrète valide (≥ 32 octets) | Jeton avec **3 parties** (header.payload.signature) |
| U-JW-02 | Extraction du login depuis un jeton | P1 | Jeton généré pour `john` | Renvoie `john` |
| U-JW-03 | Jeton valide de l'utilisateur lui-même | P1 | Jeton de `john` + utilisateur `john` | `true` |
| U-JW-04 | Jeton d'un autre utilisateur | P2 | Jeton de `john` + utilisateur `mary` | `false` |
| U-JW-05 | Jeton expiré | P3 | Jeton dont l'expiration est dans le passé | `ExpiredJwtException` |
| U-JW-06 | Jeton falsifié (autre secret) | P3 | Jeton signé avec une clé différente de la nôtre | `JwtException` (signature invalide) |
| U-JW-07 | Jeton malformé | P2 | Chaîne `this-is-not-a-jwt` | `JwtException` |
| U-JW-08 | Clé secrète manquante | P3 | `secretKey = null` | `IllegalStateException` « JWT secret key is missing… » |
| U-JW-09 | Clé secrète trop courte | P3 | Clé de 16 octets (HS256 exige 32) | `IllegalStateException` « at least 32 bytes » |

---

## 5. Niveau 2 — Tests d'intégration (15 cas)

Le contexte Spring Boot **complet** démarre (contrôleurs, sécurité, JPA) et communique avec
un **vrai MySQL** lancé par Testcontainers. Ces tests valident toute la chaîne
`HTTP → contrôleur → service → JPA → base de données`.

*Pour chaque test, un utilisateur est d'abord créé puis connecté via l'API afin d'obtenir un
JWT valide (les endpoints `/api/students` exigent l'authentification). La base est nettoyée
après chaque test (`deleteAll`).*

### 5.1 Endpoints d'authentification — fichier `src/test/java/.../controller/UserControllerIT.java` (6 cas)

| ID | Cas | Priorité | Entrée | Sortie attendue |
|---|---|---|---|---|
| I-UC-01 | Inscription d'un nouvel utilisateur | P1 | `POST /api/register` avec données complètes (John / Doe / login / password) | **201 Created** ; l'utilisateur est présent dans la base |
| I-UC-02 | Inscription avec des données manquantes | P2 | `POST /api/register` avec un DTO vide (tous les champs nuls) | **400 Bad Request** |
| I-UC-03 | Inscription avec un login déjà utilisé | P2 | Le login `login` existe déjà dans la base | **400 Bad Request** |
| I-UC-04 | Connexion avec de bonnes identifiants | P1 | Utilisateur existant + bon mot de passe | **200 OK** ; le corps de la réponse est un **JWT** (3 parties séparées par des points) |
| I-UC-05 | Connexion avec un mauvais mot de passe | P2 | Utilisateur existant + `wrong-password` | **400 Bad Request** |
| I-UC-06 | Connexion avec une requête vide | P2 | login et password nuls | **400 Bad Request** |

### 5.2 Endpoints étudiants — fichier `src/test/java/.../controller/StudentControllerIT.java` (9 cas)

| ID | Cas | Priorité | Entrée | Sortie attendue |
|---|---|---|---|---|
| I-ST-01 | Création d'un étudiant valide | P1 | `POST /api/students` avec données valides + en-tête `Bearer <token>` | **201 Created** ; l'étudiant est réellement dans la base (count = 1) |
| I-ST-02 | Liste des étudiants | P1 | 2 étudiants dans la base + token | **200 OK** ; tableau JSON de longueur 2 |
| I-ST-03 | Récupération d'un étudiant existant | P1 | `GET /api/students/{id}` avec l'id enregistré + token | **200 OK** ; champs firstName/lastName corrects |
| I-ST-04 | Modification d'un étudiant existant | P1 | `PUT /api/students/{id}` avec nouveaux prénom/âge + token | **200 OK** ; les modifications sont **persistées** dans la base |
| I-ST-05 | Suppression d'un étudiant existant | P1 | `DELETE /api/students/{id}` + token | **204 No Content** ; la ligne n'est plus dans la base |
| I-ST-06 | Accès sans jeton | P2 | `GET /api/students` **sans** en-tête Authorization | **401 Unauthorized** |
| I-ST-07 | Création avec des données invalides | P2 | Prénom vide, e-mail `not-an-email`, âge 0 | **400 Bad Request** ; rien n'est enregistré |
| I-ST-08 | Création avec un e-mail déjà utilisé | P2 | `ada@test.com` existe déjà dans la base | **409 Conflict** ; corps : `error = EMAIL_ALREADY_EXISTS` |
| I-ST-09 | Récupération d'un étudiant inconnu | P2 | `GET /api/students/9999` + token | **404 Not Found** |

---

## 6. Niveau 3 — Tests de bout en bout (E2E)

Le backend n'a **pas de suite E2E dédiée** : son API réelle est validée de bout en bout par
les **tests Cypress « réel » du frontend** (`npm run cy:real`), qui appellent le vrai backend
(`localhost:8080`) avec la vraie base MySQL. Voir le *Plan de test — Frontend*, section 6.

Flux E2E qui valident le backend :

1. **Création** d'un compte réel via `POST /api/register` ;
2. **Connexion** et obtention du JWT via `POST /api/login` ;
3. **CRUD complet** des étudiants avec le JWT (201 / 200 / 204) ;
4. **Sécurité** : 401 sans jeton, refus d'un JWT expiré, 400 / 409 sur les erreurs d'API.


---

## 7. Synthèse et critères de réussite

| Catégorie | Nb de cas | Emplacement |
|---|---|---|
| Unitaires | 22 | `src/test/java/.../service/*Test.java` |
| Intégration | 15 | `src/test/java/.../controller/*IT.java` |
| E2E (API) | 17 (portés par le frontend) | `Front-end/.../cypress/e2e/e2e/*.cy.ts` |
| **Total** | **37 (+ 17 E2E)** | |

**Critères de réussite**

- `mvn clean test` se termine **en vert** (aucune erreur, aucun échec, aucun test ignoré) ;
- les 22 tests unitaires et les 15 tests d'intégration passent ;
- les 17 tests E2E du frontend passent contre le backend démarré (Docker + MySQL + backend + frontend).