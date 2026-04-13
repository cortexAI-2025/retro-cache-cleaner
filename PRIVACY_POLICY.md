# Politique de Confidentialité — Retro Cache Cleaner

**Date d'entrée en vigueur :** 2026-04-13  
**Éditeur :** [Nom de votre société]  
**Contact :** [email@votresociete.com]

---

## 1. Résumé

**Retro Cache Cleaner ne collecte, ne stocke ni ne transmet aucune donnée personnelle.**

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
| Applications installées | **Lecture locale uniquement** | La liste des applications est affichée à l'écran uniquement ; elle n'est jamais transmise. |
| Identifiants appareil | **Non** | — |
| Données d'utilisation | **Non** | — |

---

## 3. Fonctionnement technique

L'application interroge le système Android (`PackageManager`) pour lister
les applications installées par l'utilisateur. Ces informations sont :

- **uniquement affichées à l'écran**, dans la session en cours ;
- **jamais enregistrées** sur le stockage local (pas de base de données, pas de fichiers) ;
- **jamais transmises** sur Internet ou vers un serveur distant.

L'application **ne contient aucun SDK tiers** (analytics, publicité, télémétrie).

---

## 4. Permissions utilisées

| Permission | Motif |
|---|---|
| `android.intent.action.MAIN` + `LAUNCHER` (visibilité paquets) | Lister les applications installées par l'utilisateur pour les afficher dans l'interface. |

> **Note :** L'application n'utilise *pas* `QUERY_ALL_PACKAGES`. La visibilité
> des paquets est déclarée via le mécanisme `<queries>` d'Android 11+.

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

Pour toute question : [email@votresociete.com]

---

*Cette politique de confidentialité doit être hébergée sur une URL publique
et référencée dans la Play Console lors de la soumission de l'application.*
