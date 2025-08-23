# Système d'Importation PDF - FranWan

## 🎯 Fonctionnalité

L'application FranWan dispose d'un système d'importation de cours via fichiers PDF. Cette fonctionnalité permet d'importer automatiquement votre emploi du temps depuis un document PDF.

## 📱 Comment utiliser

1. **Cliquer sur le bouton "📄 Importer PDF"** dans l'interface principale
2. **Sélectionner un fichier PDF** depuis votre appareil
3. **Confirmer l'importation** dans le dialogue qui s'ouvre
4. **Attendre le traitement** (une barre de progression s'affiche)
5. **Vérifier le résultat** - les cours importés sont ajoutés à votre emploi du temps

## 📋 Format attendu

Le système reconnaît automatiquement les cours dans le PDF selon ce format :

```
Lundi 08:00 Mathématiques A101
Mardi 14:30 Physique B203
Mercredi 10:15 Histoire C305
Jeudi 16:30 Anglais D407
Vendredi 08:00 Chimie E509
```

## ⏰ Formats d'heure supportés

- **Format 24h** : 08:00, 14:30, 16:45
- **Format français** : 8h00, 14h30, 16h45

## 🔍 Règles de reconnaissance

- **Jours** : Lundi, Mardi, Mercredi, Jeudi, Vendredi, Samedi, Dimanche
- **Heures** : Format 24h ou français
- **Cours** : Nom du cours (peut contenir des espaces)
- **Salles** : Code de salle (lettre + chiffres, ex: A101, B203)

## 💡 Conseils pour un import réussi

1. **Utilisez un PDF avec du texte** (pas d'image scannée)
2. **Respectez le format** jour + heure + cours + salle
3. **Évitez les caractères spéciaux** dans les noms de cours
4. **Vérifiez que le PDF est lisible** et non corrompu

## 🆘 Gestion des erreurs

- **Aucun cours trouvé** : Vérifiez le format du PDF
- **Erreur d'importation** : Vérifiez que le fichier n'est pas corrompu
- **Permission refusée** : Autorisez l'accès aux fichiers dans les paramètres

## ⚙️ Fonctionnalités techniques

- **Parsing automatique** du contenu PDF
- **Reconnaissance intelligente** des formats d'heure
- **Validation des données** avant import
- **Fusion avec l'emploi existant** (pas de remplacement)
- **Interface utilisateur intuitive** avec progression

## 📝 Créer un PDF de test

Pour tester le système, vous pouvez :
1. **Copier le format d'exemple** dans Word/Google Docs
2. **Exporter en PDF**
3. **Tester l'importation** dans FranWan

## 🆘 Support

Si vous rencontrez des problèmes :
1. Vérifiez le format de votre document
2. Assurez-vous que le PDF contient du texte (pas d'image)
3. Testez avec un PDF simple d'abord
