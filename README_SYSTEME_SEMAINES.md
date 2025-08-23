# 🗓️ Système de Semaines Multiples - FranWan

## 🎯 Fonctionnalité

Le système de semaines multiples permet de gérer **deux emplois du temps différents** :
- **Semaine 1** : Premier emploi du temps
- **Semaine 2** : Deuxième emploi du temps (alternant)

## ✨ Caractéristiques

### 🔄 **Basculement entre semaines**
- Boutons **"Semaine 1"** et **"Semaine 2"** pour changer facilement
- Indicateur visuel de la semaine active
- Sauvegarde automatique lors du changement

### 📚 **Gestion séparée des cours**
- Chaque semaine a ses propres cours
- Import PDF spécifique à chaque semaine
- Suppression en masse par semaine
- Statistiques détaillées par semaine

### 💾 **Persistance des données**
- Sauvegarde automatique de chaque semaine
- Migration automatique des anciens emplois du temps
- Compatibilité avec l'ancien système

## 🚀 Utilisation

### 1. **Changement de semaine**
```
Cliquez sur "Semaine 1" ou "Semaine 2"
↓
L'emploi du temps change automatiquement
↓
Les modifications sont sauvegardées
```

### 2. **Import PDF par semaine**
```
1. Sélectionnez la semaine active
2. Cliquez sur "📄 Importer PDF"
3. Les cours sont importés dans la semaine sélectionnée
```

### 3. **Suppression par semaine**
```
1. Sélectionnez la semaine active
2. Cliquez sur "🗑️ Supprimer en masse"
3. Les suppressions n'affectent que la semaine active
```

## 📊 Interface

### **Sélecteur de semaine**
- **Indicateur** : "Semaine X active"
- **Bouton Semaine 1** : Bleu foncé (actif) ou gris (inactif)
- **Bouton Semaine 2** : Bleu foncé (actif) ou gris (inactif)

### **Statistiques**
```
📊 Statistiques des semaines :
Semaine 1 : X cours
Semaine 2 : Y cours
Semaine active (X) : Z cours

📅 Répartition par jour :
Lundi : X cours
Mardi : Y cours
...
```

## 🔧 Fonctionnalités techniques

### **Sauvegarde automatique**
- Chaque changement de semaine sauvegarde l'état précédent
- Données persistantes entre les sessions
- Migration automatique des anciens formats

### **Gestion des cours**
- Chaque cours a un attribut `week` (1 ou 2)
- Filtrage automatique selon la semaine active
- Mise à jour en temps réel de l'interface

### **Import PDF intelligent**
- Les cours importés sont automatiquement assignés à la semaine active
- Format compatible avec l'ancien système
- Validation et normalisation des données

## 📱 Compatibilité

### **Ancien système**
- ✅ Migration automatique des emplois du temps existants
- ✅ Conservation de toutes les fonctionnalités
- ✅ Rétrocompatibilité complète

### **Nouveau système**
- ✅ Gestion de deux emplois du temps séparés
- ✅ Basculement instantané entre semaines
- ✅ Sauvegarde séparée pour chaque semaine

## 🎨 Personnalisation

### **Couleurs des boutons**
- **Semaine active** : Bleu foncé (#3498DB)
- **Semaine inactive** : Gris (#95A5A6)
- **Indicateur** : Couleur dynamique selon la semaine

### **Layout responsive**
- Boutons adaptés aux différentes tailles d'écran
- Espacement optimisé pour une meilleure lisibilité
- Intégration harmonieuse avec l'interface existante

## 🔍 Dépannage

### **Problème courant : Cours non visibles**
```
Vérifiez que vous êtes sur la bonne semaine
↓
Cliquez sur le bouton de la semaine souhaitée
↓
Les cours devraient apparaître
```

### **Problème courant : Suppression qui ne fonctionne pas**
```
Vérifiez que vous êtes sur la bonne semaine
↓
Les suppressions n'affectent que la semaine active
↓
Utilisez "🗑️ Supprimer en masse" pour la semaine active
```

## 📈 Avantages

1. **Flexibilité** : Gestion de deux emplois du temps différents
2. **Organisation** : Séparation claire des semaines
3. **Efficacité** : Basculement rapide entre les semaines
4. **Sécurité** : Sauvegarde automatique des modifications
5. **Compatibilité** : Fonctionne avec l'ancien système

## 🎯 Cas d'usage

- **Étudiants** : Semaines paires/impaires
- **Professeurs** : Groupes différents par semaine
- **Entreprises** : Planning alterné
- **Événements** : Programmes différents selon la semaine

---

*Système développé pour FranWan - Gestionnaire d'emploi du temps intelligent* 🚀
