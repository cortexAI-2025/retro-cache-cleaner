# Publication sur le Play Store — Cache Cleaner

## 1. Clé d'import (une seule fois, à conserver précieusement)

```bash
keytool -genkey -v -keystore upload-key.jks -keyalg RSA -keysize 2048 \
  -validity 10000 -alias cache-cleaner
base64 -w0 upload-key.jks > upload-key.jks.b64     # macOS : base64 -i upload-key.jks
```

Sauvegardez `upload-key.jks` et ses mots de passe hors du dépôt (ils sont dans `.gitignore`).
Avec **Play App Signing** (activé par défaut), Google garde la clé de signature finale ;
cette clé n'est que la clé d'*import* (réinitialisable via le support Play en cas de perte).

## 2. Secrets GitHub (Settings → Secrets and variables → Actions)

| Secret | Valeur |
|---|---|
| `KEYSTORE_BASE64` | contenu de `upload-key.jks.b64` |
| `KEYSTORE_PASSWORD` | mot de passe du keystore |
| `KEY_ALIAS` | `cache-cleaner` |
| `KEY_PASSWORD` | mot de passe de la clé |

## 3. Générer l'AAB signé

1. Renseignez les mentions légales dans `app/src/main/res/values/legal.xml`
   (le build de release **échoue** tant qu'il reste des `A_COMPLETER`).
2. GitHub → **Actions → Release AAB → Run workflow**.
3. Téléchargez l'artefact `app-release-aab` (zip contenant `app-release.aab`).

En local : copiez `keystore.properties.example` en `keystore.properties`, puis `./gradlew bundleRelease`.

## 4. Play Console

- **Compte :** pour une société, un compte *organisation* (numéro D-U-N-S requis).
  Les comptes personnels récents doivent d'abord faire un test fermé (12 testeurs, 14 jours).
- **Politique de confidentialité :** publiez `docs/cache-cleaner/confidentialite.html` sur
  `https://aiworkpay.fr/cache-cleaner/confidentialite.html` (même chemin dans le dépôt du site) ; cette URL est déjà
  dans `legal.xml` (`privacy_url`) et doit aussi être saisie dans la Play Console. Vérifiez qu'elle s'ouvre avant d'envoyer l'AAB.
- **Sécurité des données :** aucune donnée collectée, aucune donnée partagée, aucune connexion réseau.
- **API cible :** l'application cible Android 16 (API 36), exigée pour toute nouvelle application depuis le 31 août 2026.
- **Catégorie :** Outils. **Public cible :** 18 ans et plus (ou tout public, sans contenu sensible).
- **Autorisations sensibles** (si la console demande une justification) :
  - `PACKAGE_USAGE_STATS` : « Fonction principale : afficher la taille réelle du cache et des données de chaque
    application installée pour permettre à l'utilisateur de libérer de l'espace. Les données restent sur
    l'appareil, ne sont ni stockées ni transmises. L'accès est accordé manuellement par l'utilisateur. »
  - `REQUEST_DELETE_PACKAGES` : « Ouvre la boîte de désinstallation système pour l'application que l'utilisateur
    choisit de désinstaller. Aucune désinstallation sans confirmation système. »
  Vous pouvez joindre une courte vidéo montrant l'écran d'autorisation et le flux d'utilisation.

## 5. Fiche du store (proposition)

**Titre (30 car. max) :** Cache Cleaner

**Description courte (80 car. max) :** Voyez le cache et les données de chaque appli et libérez de l'espace.

**Description complète :**
Cache Cleaner vous montre, pour chaque application installée, la taille de son cache et de ses données,
et vous aide à libérer de l'espace en quelques gestes.

• Cache et données affichés séparément, comme dans les paramètres Android
• Tri par cache, par données ou par nom
• Sélection multiple : traitez plusieurs applications d'affilée avec une file guidée
• Vider le cache, supprimer les données ou désinstaller, avec confirmation avant toute action irréversible
• Aucune publicité, aucune collecte de données, aucune connexion Internet

Android n'autorise pas une application à effacer seule le cache d'une autre : Cache Cleaner vous redirige vers
l'écran système de chaque application pour valider. L'accès « Données d'utilisation » est nécessaire pour lire
les tailles ; il s'active dans les paramètres Android.

**À préparer vous-même :** icône 512×512, image de présentation 1024×500, au moins 2 captures d'écran de téléphone.
