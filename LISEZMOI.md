# Séquenceur PCSI — étape 4 (application Android)

Étapes 1 à 3 : moteur, données. Étape 4 : application Android (alarmes, notifications avec boutons,
timer, widget, écran minimal) + cycle de vie des sessions testé côté moteur.

## Obtenir l'APK
1. `git add . && git commit -m "etape 4" && git push`
2. Onglet « Actions » du dépôt → dernier run → section « Artifacts » → `apk-debug`.
3. Télécharge, dézippe, envoie `app-debug.apk` sur le téléphone et installe-le
   (autoriser l'installation depuis cette source).

## Premier lancement
1. Autoriser les notifications.
2. « Importer data.json » (le fichier est choisi dans le téléphone).
3. Régler une fois : notifications avec bannières, batterie « sans restriction »,
   démarrage automatique, application verrouillée dans les applis récentes.
4. Ajouter le widget « Séquenceur » sur l'écran d'accueil.

Si le build est rouge : copie-moi les dernières lignes du log.
