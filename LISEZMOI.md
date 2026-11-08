# Séquenceur PCSI — étape 1 (fondations et score)

Contenu : modèle de données, paramètres, paliers de révision, score deepwork (section 7), tests.
Rien n'a été compilé : l'intégration continue (GitHub Actions) sert de première vérification.

## Lancer la vérification (une fois)
1. Crée un dépôt GitHub privé vide.
2. Dans ce dossier : `git init && git add . && git commit -m "etape 1"`
3. `git branch -M main && git remote add origin <URL du dépôt> && git push -u origin main`
4. Onglet « Actions » du dépôt : le job « Tests du moteur » doit passer au vert.
   S'il est rouge : ouvre le job, copie les dernières lignes du log et envoie-les moi.
