# Séquenceur PCSI — étape 6, partie 1 : l'APK

Ce dossier contient tout le projet. Le build compile l'application, vérifie tes fichiers de données
avec le vrai importeur (139 tests) et fabrique l'APK signé.

## 1. Compiler (GitHub)

1. Mets le contenu de ce dossier dans ton dépôt GitHub **privé** (la clé de signature est dans le dépôt,
   garde-le privé) :
   `git add . && git commit -m "etape 6.1" && git push`
2. Onglet **Actions** → le dernier run doit avoir une coche verte (compte 2 à 5 minutes).
   S'il est rouge : ouvre-le, copie les dernières lignes du log et envoie-les moi.

## 2. Télécharger l'APK et les données

Dans le run vert, tout en bas, section **Artifacts** :

| Artefact | Contenu |
|---|---|
| `sequenceur-apk` | `Sequenceur.apk` |
| `donnees` | `data.json` (toi) et `data_cameo.json` (Cameo) |

Chaque artefact se télécharge en `.zip`.

- **Depuis le téléphone** : ouvre github.com dans le navigateur, connecte-toi, ouvre le run, touche
  l'artefact. Le `.zip` arrive dans Téléchargements ; ouvre-le avec l'application **Fichiers**
  (« Extraire »).
- **Depuis un PC** : télécharge, dézippe, branche le téléphone en USB et copie les fichiers dans
  Téléchargements.
- Les artefacts sont supprimés au bout de 90 jours. Pour en refaire : Actions → CI → **Run workflow**.

## 3. Installer

Sur le **POCO** (HyperOS) comme sur le **Galaxy** :

1. Touche `Sequenceur.apk` dans Fichiers.
2. Android demande d'autoriser l'installation d'applications depuis cette source (Fichiers ou Chrome) :
   autorise, reviens en arrière, puis **Installer**.
3. Si un écran de sécurité (Play Protect ou analyse du système) avertit que l'application est inconnue :
   « Installer quand même ».

Les écrans exacts varient selon les versions. Pour une mise à jour, refais les mêmes gestes : la nouvelle
version s'installe par-dessus et **garde ton journal** (même clé de signature). Ne désinstalle pas
l'application : ça efface le journal.

## 4. Premier lancement

1. Autorise les **notifications**.
2. **Importer data.json** (Cameo : `data_cameo.json`) et choisis le fichier dans Téléchargements.
3. Avertissements **attendus** après l'import :
   - toi (3) : la colle d'anglais du 11/11 tombe un jour férié ; la colle d'anglais du 05/01, 17h–18h,
     tombe pendant la Physique et l'EPS du mardi (2 avertissements) ;
   - Cameo (3) : la colle d'anglais du 11/11 (férié) ; la colle d'anglais du 15/09, 17h–18h, tombe pendant
     la Physique et l'EPS du mardi (2 avertissements).
   Une erreur ou un autre avertissement : envoie-moi une capture d'écran.
4. **Saisies** : saisis les chapitres déjà vus (sans eux, le moteur n'a rien à proposer en deepwork), un
   rang si tu en as, tes DM.
5. Ajoute le **widget** « Séquenceur » : appui long sur l'écran d'accueil → Widgets.
6. Touche **Que faire ?** pour voir ce que le moteur propose maintenant.

## 5. Réglages de batterie (à faire une fois par téléphone)

Sans eux, le système peut arrêter l'application et les alarmes ne sonnent pas. Les noms de menus ci-dessous
sont indicatifs : si tu ne trouves pas un réglage, cherche son nom dans la barre de recherche des Paramètres.

### POCO (HyperOS)

1. Paramètres → Applications → Gérer les applications → **Séquenceur** → Économiseur de batterie →
   **Aucune restriction**.
2. Même écran → **Démarrage automatique** : activé.
3. Même écran → **Notifications** : autorisées, **notifications flottantes** (bannières) activées,
   écran de verrouillage activé. Garde « Propositions » et « Fin de timer » en importance élevée.
4. Ouvre les applications récentes, appuie longuement sur la carte **Séquenceur**, touche le **cadenas**
   (verrouiller). Sinon un « tout fermer » arrête l'application.
5. Paramètres → Batterie : s'il existe un mode d'économie d'énergie, ajoute Séquenceur aux exceptions.

### Samsung Galaxy

1. Paramètres → Applications → **Séquenceur** → Batterie → **Non restreinte**.
2. Paramètres → Batterie → Limites d'utilisation en arrière-plan → **Applications jamais en veille** :
   ajoute Séquenceur. Retire-la de « Applications en veille » et « en veille profonde ». Désactive
   « Mettre les applications inutilisées en veille » pour cette application.
3. Paramètres → Applications → Séquenceur → **Notifications** : autorisées avec bannières.
4. Applications récentes → touche l'icône de Séquenceur → **Garder ouverte**.
5. Si le réglage existe : Paramètres → Applications → Accès spécial → **Alarmes et rappels** → autorisé.

## 6. Test de fiabilité

1. Quand une proposition arrive, touche **Prêt**.
2. Verrouille l'écran et pose le téléphone.
3. À l'heure de fin, tu dois recevoir « … — timer terminé » avec **Terminé** et **Débordé**.
4. Si rien n'arrive, dis-moi à quelle heure c'était et quel téléphone : je saurai quelle partie ajuster.

Sans réponse 15 minutes après la fin du timer, la session est clôturée comme faite, avec sa durée prévue.
Après « Débordé », rien n'est clôturé tout seul : il faut toucher **Fini**.

## 7. Ce qui est dans les fichiers de données

- Semestre 1 uniquement. Les cours du semestre 2 ne sont pas inclus.
- Colloscope : trinôme 11 (toi) et trinôme 13 (Cameo), colles passées comprises (elles ne changent rien).
- DS : 14/11, 21/11, 28/11, 05/12, 12/12, 09/01, 16/01 ; Chimie et SI toujours ensemble ; pas de DS le 19/12.
- Congés : Toussaint, 11/11, Noël (zone A).
