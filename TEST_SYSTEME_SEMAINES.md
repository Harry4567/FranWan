# 🧪 Test du Système de Semaines Multiples

## ✅ **Tests à effectuer**

### 1. **Initialisation et migration**
- [ ] L'application se lance sans erreur
- [ ] Les anciens emplois du temps sont migrés vers la semaine 1
- [ ] L'indicateur affiche "Semaine 1 active"
- [ ] Le bouton Semaine 1 est bleu (actif)
- [ ] Le bouton Semaine 2 est gris (inactif)

### 2. **Basculement entre semaines**
- [ ] Cliquer sur "Semaine 2" change l'emploi du temps
- [ ] L'indicateur affiche "Semaine 2 active"
- [ ] Le bouton Semaine 2 devient bleu (actif)
- [ ] Le bouton Semaine 1 devient gris (inactif)
- [ ] L'emploi du temps de la semaine 2 est vide (nouvelle semaine)

### 3. **Import PDF par semaine**
- [ ] **Semaine 1 active** : Importer un PDF → cours ajoutés à la semaine 1
- [ ] **Semaine 2 active** : Importer un PDF → cours ajoutés à la semaine 2
- [ ] Changer de semaine → voir les cours correspondants
- [ ] Les cours sont bien séparés entre les semaines

### 4. **Suppression par semaine**
- [ ] **Semaine 1** : Ajouter des cours, puis supprimer en masse → cours supprimés
- [ ] **Semaine 2** : Ajouter des cours, puis supprimer en masse → cours supprimés
- [ ] Changer de semaine → les cours de l'autre semaine sont préservés
- [ ] Suppression par jour fonctionne par semaine
- [ ] Suppression par nom fonctionne par semaine

### 5. **Statistiques et affichage**
- [ ] Les statistiques affichent le bon nombre de cours par semaine
- [ ] L'affichage se met à jour lors du changement de semaine
- [ ] Les cours du jour sont filtrés selon la semaine active

### 6. **Persistance des données**
- [ ] Fermer et relancer l'application
- [ ] Les deux semaines sont conservées
- [ ] La semaine active est restaurée
- [ ] Tous les cours sont préservés

## 🔧 **Scénarios de test**

### **Scénario A : Première utilisation**
```
1. Lancer l'application
2. Vérifier que la semaine 1 est active
3. Ajouter quelques cours via l'interface
4. Changer vers la semaine 2
5. Vérifier que la semaine 2 est vide
6. Ajouter des cours différents
7. Basculement entre les semaines
8. Vérifier que chaque semaine garde ses cours
```

### **Scénario B : Import PDF**
```
1. Semaine 1 active
2. Importer un PDF avec des cours
3. Vérifier que les cours apparaissent
4. Changer vers la semaine 2
5. Vérifier que la semaine 2 est vide
6. Importer un PDF différent
7. Vérifier que les cours sont séparés
```

### **Scénario C : Suppression en masse**
```
1. Semaine 1 : ajouter des cours
2. Semaine 2 : ajouter des cours
3. Semaine 1 : supprimer tous les cours
4. Vérifier que la semaine 1 est vide
5. Changer vers la semaine 2
6. Vérifier que la semaine 2 a encore ses cours
```

### **Scénario D : Migration des anciens données**
```
1. Avoir un emploi du temps existant
2. Mettre à jour l'application
3. Vérifier que les cours sont dans la semaine 1
4. Vérifier que la semaine 2 est vide
5. Tester toutes les fonctionnalités
```

## 🐛 **Problèmes potentiels à vérifier**

### **Problème 1 : Cours mélangés entre semaines**
- [ ] Vérifier que `updateCurrentSchedule()` est appelé
- [ ] Vérifier que `schedule` pointe vers la bonne liste
- [ ] Vérifier que les filtres fonctionnent

### **Problème 2 : Sauvegarde qui ne fonctionne pas**
- [ ] Vérifier que `saveData()` sauvegarde les deux semaines
- [ ] Vérifier que `loadData()` charge les deux semaines
- [ ] Vérifier que la migration fonctionne

### **Problème 3 : Interface qui ne se met pas à jour**
- [ ] Vérifier que `updateWeekSelectorUI()` est appelé
- [ ] Vérifier que `updateTodayScheduleDisplay()` est appelé
- [ ] Vérifier que les listeners sont bien attachés

## 📱 **Test sur différents appareils**

### **Test sur émulateur**
- [ ] API 24 (Android 7.0)
- [ ] API 30 (Android 11)
- [ ] API 33 (Android 13)

### **Test sur appareil physique**
- [ ] Téléphone Android
- [ ] Tablette Android
- [ ] Différentes tailles d'écran

## 🎯 **Critères de réussite**

### **Fonctionnel**
- ✅ Basculement entre semaines fonctionne
- ✅ Import PDF fonctionne par semaine
- ✅ Suppression en masse fonctionne par semaine
- ✅ Sauvegarde/chargement fonctionne
- ✅ Migration des anciens données fonctionne

### **Interface**
- ✅ Indicateur de semaine active
- ✅ Boutons de changement de semaine
- ✅ Statistiques mises à jour
- ✅ Affichage des cours filtré

### **Performance**
- ✅ Changement de semaine rapide (< 100ms)
- ✅ Pas de fuite mémoire
- ✅ Sauvegarde rapide
- ✅ Chargement rapide

---

*Tests à effectuer après chaque modification du système* 🧪
