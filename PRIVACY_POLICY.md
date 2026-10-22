# Politique de Confidentialité — Cache Cleaner

**Date d'entrée en vigueur :** 22 octobre 2026  
**Éditeur :** AIWorkPay, SAS au capital de 100 €, 60 rue François 1er, 75008 Paris — SIREN 941 861 775, RCS Paris (président : Ahmed Akafou)  
**Contact :** contact@aiworkpay.fr

---

## 1. Résumé

**Cache Cleaner ne collecte, ne stocke ni ne transmet aucune donnée personnelle.**

---

## 2. Données collectées

| Catégorie | Collectée ? | Détails |
|---|---|---|
| Données d'identification | **Non** | — |
| Données de localisation | **Non** | — |
| Données financières | **Non** | — |
| Données de santé | **Non** | — |
| Messages / e-mails | **Non** | — |
| Photos / vidéos | **Non** | — |
| Fichiers de l'appareil | **Non** | — |
| Applications installées et tailles cache/données | **Lecture locale uniquement** | Affichées à l'écran uniquement ; jamais transmises. |
| Identifiants appareil | **Non** | — |
| Données d'utilisation | **Non** | — |

---

## 3. Fonctionnement technique

L'application interroge le système Android pour lister les applications installées
et lire, pour chacune, la taille de son cache et de ses données (`StorageStatsManager`).
Ces informations sont :

- **uniquement affichées à l'écran**, dans la session en cours ;
- **jamais enregistrées** sur l'appareil (pas de base de données, pas de fichiers) ;
- **jamais transmises** sur Internet ou vers un serveur distant.

L'application **ne contient aucun SDK tiers** (analytics, publicité, télémétrie).
Elle n'efface rien elle-même : elle ouvre les écrans système (infos de l'application,
désinstallation) où l'utilisateur confirme chaque action.

---

## 4. Permissions utilisées

| Permission | Motif |
|---|---|
| `PACKAGE_USAGE_STATS` (accès aux données d'utilisation, accordé manuellement par l'utilisateur) | Lire la taille réelle du cache et des données de chaque application. |
| `REQUEST_DELETE_PACKAGES` | Ouvrir la boîte de désinstallation système pour l'application choisie par l'utilisateur. |
| `<queries>` (`MAIN` + `LAUNCHER`) | Voir les applications lançables pour les afficher. |

> **Note :** L'application n'utilise *pas* `QUERY_ALL_PACKAGES`.

---

## 5. Accès Internet

L'application **ne nécessite pas la permission `INTERNET`** et n'effectue
**aucune requête réseau**.

---

## 6. Droits des utilisateurs (RGPD)

Aucune donnée personnelle n'étant collectée, les droits d'accès, de
rectification et de suppression (Articles 15-17 RGPD) ne s'appliquent pas.

---

## 7. Modifications

Cette politique peut être mise à jour. La date d'entrée en vigueur figurant
en en-tête sera modifiée en conséquence.

---

## 8. Contact

Pour toute question : contact@aiworkpay.fr

---

*Version publiée : https://aiworkpay.fr/cache-cleaner/confidentialite.html*
