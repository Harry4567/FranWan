package com.example.franwan

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.franwan.auth.AuthRepository
import com.example.franwan.auth.LoginActivity
import com.example.franwan.auth.SessionManager
import com.example.franwan.utils.PdfParser
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
 

// Modèle de données pour un cours
data class ClassItem(
    val day: String,
    val time: String,
    val course: String,
    val room: String,
    var dateTime: Long = 0L,
    val week: Int = 1 // 1 = Semaine 1, 2 = Semaine 2
)

// Modèle de données pour les changements quotidiens
data class DailyChange(
    val type: String,
    val originalCourse: String = "",
    val originalTime: String = "",
    val newCourse: String = "",
    val newTime: String = "",
    val newRoom: String = "",
    val isNew: Boolean = false
)

class MainActivity : AppCompatActivity() {

    // Constante : Nom du cours invisible pour le rappel quotidien (doit être la même que dans NotificationHelper)
    private val INTERNAL_DAILY_REMINDER_COURSE_NAME = "INTERNAL_DAILY_REMINDER_COURSE"

    // Vues de l'interface utilisateur
    private lateinit var nextCourseText: TextView
    private lateinit var nextCourseTime: TextView
    private lateinit var timeLeftText: TextView
    private lateinit var simulateNotificationButton: Button
    private lateinit var manageScheduleButton: Button
    private lateinit var dailyChangesButton: Button
    private lateinit var setDailyReminderTimeButton: Button
    private lateinit var setReminderDelayButton: Button
    private lateinit var setCourseDaysButton: Button
    private lateinit var vacationPauseButton: Button
    private lateinit var todayScheduleTitle: TextView
    private lateinit var todayScheduleRecyclerView: RecyclerView
    private lateinit var noClassTodayText: TextView
    private lateinit var requestBatteryOptimizationButton: Button
    private lateinit var appVersionText: TextView // NOUVEAU
    private lateinit var appUserText: TextView    // NOUVEAU
    private lateinit var settingsButton: ImageButton // NOUVEAU
    private lateinit var importPdfButton: Button // NOUVEAU
    private lateinit var bulkDeleteButton: Button // NOUVEAU
    
    // Sélecteur de semaine
    private lateinit var weekSelectorLayout: LinearLayout
    private lateinit var week1Button: Button
    private lateinit var week2Button: Button
    private lateinit var currentWeekIndicator: TextView
 

    // Adaptateur pour le RecyclerView de l'emploi du temps du jour
    private lateinit var todayScheduleAdapter: ClassAdapter

    // Stockage local
    private lateinit var sharedPreferences: SharedPreferences
    private val gson = Gson()

    // Données de l'application
    private var schedule: MutableList<ClassItem> = mutableListOf()
    private var dailyChanges: MutableMap<String, MutableList<DailyChange>> = mutableMapOf()
    private var selectedCourseDays: MutableSet<String> = mutableSetOf() // Jours de cours sélectionnés
    
    // Système de semaines multiples
    private var currentWeek: Int = 1 // Semaine active (1 ou 2)
    private var week1Schedule: MutableList<ClassItem> = mutableListOf()
    private var week2Schedule: MutableList<ClassItem> = mutableListOf()

    // Variables pour la pause vacances
    private var isVacationModeActive: Boolean = false
    private var vacationEndDateMillis: Long = 0L

    // Gestion du temps
    private val handler = Handler(Looper.getMainLooper())
    
    // Importation PDF
    private lateinit var pdfParser: PdfParser
    private var selectedPdfUri: Uri? = null
    
    // Variables pour la suppression en masse
    private var currentDeleteAction: (() -> Unit)? = null
    private var currentDeleteDescription = ""
    
    // Lanceur d'activité pour sélectionner un fichier PDF
    private val selectPdfLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedPdfUri = it
            showImportPdfDialog()
        }
    }
    private val updateTimeRunnable = object : Runnable {
        override fun run() {
            findAndDisplayNextClassAndUI()
            handler.postDelayed(this, 1000)
        }
    }

 

    private val daysOfWeek = listOf(
        "Dimanche", "Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi"
    )

    // Lanceur d'activité pour demander la permission de notification (Android 13+)
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Suppression du Toast pour éviter l'affichage répétitif
            NotificationHelper.scheduleNextClassNotification(this)
            Log.d("MainActivity", "Permission POST_NOTIFICATIONS accordée. Notifications planifiées.")
        } else {
            AlertDialog.Builder(this)
                .setTitle("Permission de notification refusée")
                .setMessage("Pour recevoir des rappels de cours, veuillez activer les notifications pour cette application dans les paramètres de votre téléphone.")
                .setPositiveButton("OK", null)
                .show()
            Log.d("MainActivity", "Permission POST_NOTIFICATIONS refusée.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sessionManager = SessionManager(this)
        if (!sessionManager.canAccessApp()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        setContentView(R.layout.activity_main)

        nextCourseText = findViewById(R.id.nextCourseText)
        nextCourseTime = findViewById(R.id.nextCourseTime)
        timeLeftText = findViewById(R.id.timeLeftText)
        simulateNotificationButton = findViewById(R.id.simulateNotificationButton)
        manageScheduleButton = findViewById(R.id.manageScheduleButton)
        dailyChangesButton = findViewById(R.id.dailyChangesButton)
        setDailyReminderTimeButton = findViewById(R.id.setDailyReminderTimeButton)
        setReminderDelayButton = findViewById(R.id.setReminderDelayButton)
        setCourseDaysButton = findViewById(R.id.setCourseDaysButton)
        vacationPauseButton = findViewById(R.id.vacationPauseButton)
        todayScheduleTitle = findViewById(R.id.todayScheduleTitle)
        todayScheduleRecyclerView = findViewById(R.id.todayScheduleRecyclerView)
        noClassTodayText = findViewById(R.id.noClassTodayText)
        requestBatteryOptimizationButton = findViewById(R.id.requestBatteryOptimizationButton)
        appVersionText = findViewById(R.id.appVersionText) // NOUVEAU
        appUserText = findViewById(R.id.appUserText)       // NOUVEAU
        settingsButton = findViewById(R.id.settingsButton) // NOUVEAU
        importPdfButton = findViewById(R.id.importPdfButton) // NOUVEAU
        bulkDeleteButton = findViewById(R.id.bulkDeleteButton) // NOUVEAU
        
        // Initialiser le sélecteur de semaine
        weekSelectorLayout = findViewById(R.id.weekSelectorLayout)
        week1Button = findViewById(R.id.week1Button)
        week2Button = findViewById(R.id.week2Button)
        currentWeekIndicator = findViewById(R.id.currentWeekIndicator)

        sharedPreferences = getSharedPreferences("ClassSchedulerApp", Context.MODE_PRIVATE)

 

        loadData()

        todayScheduleRecyclerView.layoutManager = LinearLayoutManager(this)
        todayScheduleAdapter = ClassAdapter(mutableListOf(), false, INTERNAL_DAILY_REMINDER_COURSE_NAME) { _, _ -> /* Pas d'action de suppression dans la vue principale */ }
        todayScheduleRecyclerView.adapter = todayScheduleAdapter

        // Vérifier si la permission de notification est déjà accordée avant de la demander
        if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        NotificationHelper.createNotificationChannels(this)
        
        // Initialiser le parser PDF
        PdfParser.initPdfBox(this)
        pdfParser = PdfParser(this)

        simulateNotificationButton.setOnClickListener {
            NotificationHelper.showNotification(
                this,
                CLASS_REMINDER_NOTIFICATION_ID,
                CLASS_REMINDER_CHANNEL_ID,
                "Test de Notification",
                "Ceci est une notification de test pour votre prochain cours !",
                "Test", "Test Room"
            )
            Log.d("MainActivity", "Bouton Simuler Notification cliqué.")
        }

        manageScheduleButton.setOnClickListener {
            showManageScheduleDialog()
            Log.d("MainActivity", "Bouton Gérer Emploi du Temps Annuel cliqué.")
        }

        dailyChangesButton.setOnClickListener {
            showDailyChangesDialog()
            Log.d("MainActivity", "Bouton Modifications Quotidiennes cliqué.")
        }

        setDailyReminderTimeButton.setOnClickListener {
            showTimePickerDialog()
            Log.d("MainActivity", "Bouton Définir l'heure du rappel quotidien cliqué.")
        }

        setReminderDelayButton.setOnClickListener {
            showReminderDelayDialog()
            Log.d("MainActivity", "Bouton Délai des rappels cliqué.")
        }

        setCourseDaysButton.setOnClickListener {
            showSetCourseDaysDialog()
            Log.d("MainActivity", "Bouton Définir les jours de cours cliqué.")
        }

        vacationPauseButton.setOnClickListener {
            showVacationPauseDialog()
            Log.d("MainActivity", "Bouton Pause vacances cliqué.")
        }

        requestBatteryOptimizationButton.setOnClickListener {
            checkAndRequestBatteryOptimization()
            Log.d("MainActivity", "Bouton Vérifier Optimisation Batterie cliqué.")
        }

        settingsButton.setOnClickListener {
            showSettingsMenu()
            Log.d("MainActivity", "Bouton Paramètres cliqué.")
        }
        
        importPdfButton.setOnClickListener {
            selectPdfLauncher.launch("application/pdf")
            Log.d("MainActivity", "Bouton Importer PDF cliqué.")
        }
        
        bulkDeleteButton.setOnClickListener {
            showBulkDeleteDialog()
            Log.d("MainActivity", "Bouton Supprimer en masse cliqué.")
        }
        
        // Listeners pour le sélecteur de semaine
        week1Button.setOnClickListener {
            switchWeek(1)
            Log.d("MainActivity", "Bouton Semaine 1 cliqué")
        }
        
        week2Button.setOnClickListener {
            switchWeek(2)
            Log.d("MainActivity", "Bouton Semaine 2 cliqué")
        }

 

        // Mettre à jour l'affichage de la version et du pseudo
        updateAppInfoDisplay() // NOUVEAU
        
        // Initialiser l'interface du sélecteur de semaine
        updateWeekSelectorUI()

        updateTodayScheduleDisplay()

        handleIntent(intent)
        Log.d("MainActivity", "MainActivity onCreate terminé.")
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
        Log.d("MainActivity", "MainActivity onNewIntent appelé.")
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_DAILY_CHANGES, false) == true) {
            showDailyChangesDialog()
            Log.d("MainActivity", "Ouverture du dialogue des modifications quotidiennes via Intent.")
        }
    }

    private fun showTimePickerDialog() {
        val currentHour = sharedPreferences.getInt("dailyReminderHour", 7)
        val currentMinute = sharedPreferences.getInt("dailyReminderMinute", 0)

        val timePickerDialog = TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                sharedPreferences.edit()
                    .putInt("dailyReminderHour", hourOfDay)
                    .putInt("dailyReminderMinute", minute)
                    .apply()
                Toast.makeText(this, "Rappel quotidien défini pour ${String.format("%02d:%02d", hourOfDay, minute)}", Toast.LENGTH_LONG).show()
                NotificationHelper.scheduleNextClassNotification(this)
                Log.d("MainActivity", "Heure de rappel quotidien définie à ${String.format("%02d:%02d", hourOfDay, minute)}")
            },
            currentHour,
            currentMinute,
            true
        )
        timePickerDialog.show()
    }

    private fun showReminderDelayDialog() {
        val delayOptions = arrayOf("Immédiat (à l'heure du cours)", "1 minute", "2 minutes", "3 minutes", "5 minutes", "10 minutes", "15 minutes", "30 minutes", "1 heure", "Délai personnalisé...")
        val delayValues = intArrayOf(0, 1, 2, 3, 5, 10, 15, 30, 60, -1) // -1 pour délai personnalisé
        
        val currentDelay = sharedPreferences.getInt("reminderDelayMinutes", 5) // Valeur par défaut : 5 minutes
        val currentIndex = delayValues.indexOf(currentDelay).takeIf { it >= 0 } ?: 4 // Index par défaut pour 5 minutes

        AlertDialog.Builder(this)
            .setTitle("Choisir le délai des rappels de cours")
            .setSingleChoiceItems(delayOptions, currentIndex) { dialog, which ->
                val selectedDelay = delayValues[which]
                
                if (selectedDelay == -1) {
                    // Délai personnalisé
                    showCustomDelayDialog()
                    dialog.dismiss()
                } else {
                    sharedPreferences.edit().putInt("reminderDelayMinutes", selectedDelay).apply()
                    updateReminderDelayButtonState()
                    NotificationHelper.scheduleNextClassNotification(this)
                    Toast.makeText(this, "Délai des rappels défini à ${delayOptions[which]}", Toast.LENGTH_SHORT).show()
                    Log.d("MainActivity", "Délai des rappels défini à $selectedDelay minutes")
                    dialog.dismiss()
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun showCustomDelayDialog() {
        val dialogView = LayoutInflater.from(this).inflate(android.R.layout.simple_list_item_1, null)
        val editText = EditText(this).apply {
            hint = "Entrez le délai en minutes (ex: 45)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(sharedPreferences.getInt("reminderDelayMinutes", 5).toString())
        }
        
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 30, 50, 30)
            addView(editText)
        }

        AlertDialog.Builder(this)
            .setTitle("Délai personnalisé")
            .setView(container)
            .setPositiveButton("OK") { dialog, _ ->
                val customDelay = editText.text.toString().toIntOrNull()
                if (customDelay != null && customDelay >= 0) {
                    sharedPreferences.edit().putInt("reminderDelayMinutes", customDelay).apply()
                    updateReminderDelayButtonState()
                    NotificationHelper.scheduleNextClassNotification(this)
                    Toast.makeText(this, "Délai personnalisé défini à $customDelay minutes", Toast.LENGTH_SHORT).show()
                    Log.d("MainActivity", "Délai personnalisé défini à $customDelay minutes")
                } else {
                    Toast.makeText(this, "Veuillez entrer un nombre valide", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun showSetCourseDaysDialog() {
        val checkedItems = BooleanArray(daysOfWeek.size) { i ->
            selectedCourseDays.contains(daysOfWeek[i])
        }

        AlertDialog.Builder(this)
            .setTitle("Sélectionner les jours de cours")
            .setMultiChoiceItems(daysOfWeek.toTypedArray(), checkedItems) { dialog, which, isChecked ->
                if (isChecked) {
                    selectedCourseDays.add(daysOfWeek[which])
                } else {
                    selectedCourseDays.remove(daysOfWeek[which])
                }
            }
            .setPositiveButton("OK") { dialog, which ->
                saveData() // Sauvegarder les jours sélectionnés
                NotificationHelper.scheduleNextClassNotification(this) // Re-planifier toutes les notifications
                updateTodayScheduleDisplay() // Mettre à jour l'affichage de l'emploi du temps du jour
                updateCourseDaysButtonState() // Mettre à jour l'état du bouton
                Toast.makeText(this, "Jours de cours mis à jour.", Toast.LENGTH_SHORT).show()
                Log.d("MainActivity", "Jours de cours sélectionnés: $selectedCourseDays")
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun showVacationPauseDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_vacation_pause, null)
        val builder = AlertDialog.Builder(this)
        builder.setView(dialogView)
        val dialog = builder.create()

        val vacationSwitch: Switch = dialogView.findViewById(R.id.vacationSwitch)
        val endDateText: TextView = dialogView.findViewById(R.id.endDateText)
        val selectEndDateButton: Button = dialogView.findViewById(R.id.selectEndDateButton)
        val saveButton: Button = dialogView.findViewById(R.id.saveVacationButton)

        // Initialiser le switch et le texte de la date de fin
        vacationSwitch.isChecked = isVacationModeActive
        updateEndDateDisplay(endDateText, vacationEndDateMillis)

        // Gérer l'état initial des éléments en fonction du switch
        selectEndDateButton.isEnabled = vacationSwitch.isChecked

        vacationSwitch.setOnCheckedChangeListener { _, isChecked ->
            isVacationModeActive = isChecked
            selectEndDateButton.isEnabled = isChecked
            if (!isChecked) {
                vacationEndDateMillis = 0L // Réinitialiser la date de fin si la pause est désactivée
                updateEndDateDisplay(endDateText, vacationEndDateMillis)
            } else {
                // Si activé, mais pas de date de fin définie, ouvrir le DatePicker
                if (vacationEndDateMillis == 0L || Calendar.getInstance().timeInMillis >= vacationEndDateMillis) {
                    showDatePickerForVacation(endDateText)
                }
            }
        }

        selectEndDateButton.setOnClickListener {
            showDatePickerForVacation(endDateText)
        }

        saveButton.setOnClickListener {
            saveData() // Sauvegarder l'état de la pause vacances
            NotificationHelper.scheduleNextClassNotification(this) // Re-planifier les notifications
            updateVacationPauseButtonState() // Mettre à jour le bouton principal
            Toast.makeText(this, "Paramètres de pause vacances sauvegardés.", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
            Log.d("MainActivity", "Pause vacances: active=$isVacationModeActive, fin=${if (vacationEndDateMillis > 0) SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(vacationEndDateMillis)) else "N/A"}")
        }

        dialog.setOnDismissListener {
            // S'assurer que le bouton principal est à jour même si on annule
            updateVacationPauseButtonState()
            NotificationHelper.scheduleNextClassNotification(this) // Re-planifier au cas où
        }
        dialog.show()
    }

    private fun showDatePickerForVacation(endDateTextView: TextView) {
        val calendar = Calendar.getInstance()
        if (vacationEndDateMillis > 0) {
            calendar.timeInMillis = vacationEndDateMillis
        }

        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val datePickerDialog = DatePickerDialog(
            this,
            { _, selectedYear, selectedMonth, selectedDay ->
                val selectedCalendar = Calendar.getInstance().apply {
                    set(selectedYear, selectedMonth, selectedDay, 23, 59, 59) // Fin de journée
                    set(Calendar.MILLISECOND, 999)
                }
                vacationEndDateMillis = selectedCalendar.timeInMillis
                updateEndDateDisplay(endDateTextView, vacationEndDateMillis)
            },
            year,
            month,
            day
        )
        datePickerDialog.datePicker.minDate = System.currentTimeMillis() // Ne peut pas choisir une date passée
        datePickerDialog.show()
    }

    private fun updateEndDateDisplay(textView: TextView, millis: Long) {
        if (millis > 0) {
            val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            textView.text = "Fin le : ${dateFormat.format(Date(millis))}"
        } else {
            textView.text = "Date de fin non définie"
        }
    }


    override fun onResume() {
        super.onResume()
        handler.post(updateTimeRunnable)
        loadData()
        updateTodayScheduleDisplay()
        NotificationHelper.scheduleNextClassNotification(this)
        Log.d("MainActivity", "MainActivity onResume appelé. Notifications re-planifiées.")
        updateBatteryOptimizationButtonState()
        updateCourseDaysButtonState()
        updateVacationPauseButtonState()
        updateReminderDelayButtonState()
        updateAppInfoDisplay() // Mettre à jour l'affichage de la version et du pseudo
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(updateTimeRunnable)
        NotificationHelper.scheduleNextClassNotification(this)
        Log.d("MainActivity", "MainActivity onPause appelé. Notifications re-planifiées en arrière-plan.")
    }

    // --- Gestion des données ---
    private fun loadData() {
        // Charger les semaines multiples
        val week1Json = sharedPreferences.getString("week1Schedule", null)
        if (week1Json != null) {
            val type = object : TypeToken<MutableList<ClassItem>>() {}.type
            week1Schedule = gson.fromJson(week1Json, type)
        } else {
            week1Schedule = mutableListOf()
        }
        
        val week2Json = sharedPreferences.getString("week2Schedule", null)
        if (week2Json != null) {
            val type = object : TypeToken<MutableList<ClassItem>>() {}.type
            week2Schedule = gson.fromJson(week2Json, type)
        } else {
            week2Schedule = mutableListOf()
        }
        
        // Charger la semaine active
        currentWeek = sharedPreferences.getInt("currentWeek", 1)
        
        // Charger l'ancien format pour la compatibilité
        val scheduleJson = sharedPreferences.getString("classSchedule", null)
        if (scheduleJson != null) {
            try {
                val type = object : TypeToken<MutableList<ClassItem>>() {}.type
                val oldSchedule: MutableList<ClassItem>? = gson.fromJson(scheduleJson, type)
                // Migrer les anciens cours vers la semaine 1
                if (oldSchedule != null && oldSchedule.isNotEmpty()) {
                    val migratedCourses = oldSchedule.map { course -> 
                        course.copy(week = 1) 
                    }
                    week1Schedule.addAll(migratedCourses)
                    // Supprimer l'ancien format
                    sharedPreferences.edit().remove("classSchedule").apply()
                    Log.d("MainActivity", "Migration de ${migratedCourses.size} cours vers la semaine 1")
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Erreur lors de la migration des anciens cours", e)
            }
        }
        
        // Mettre à jour le schedule actuel selon la semaine active
        updateCurrentSchedule()

        val dailyChangesJson = sharedPreferences.getString("dailyClassChanges", null)
        if (dailyChangesJson != null) {
            dailyChanges = gson.fromJson(dailyChangesJson, object : TypeToken<MutableMap<String, MutableList<DailyChange>>>() {}.type) as MutableMap<String, MutableList<DailyChange>>
        } else {
            dailyChanges = mutableMapOf()
        }

        val savedCourseDays = sharedPreferences.getStringSet("courseDays", null)
        selectedCourseDays = if (savedCourseDays != null && savedCourseDays.isNotEmpty()) {
            savedCourseDays.toMutableSet()
        } else {
            daysOfWeek.toMutableSet()
        }

        // Charger l'état de la pause vacances
        isVacationModeActive = sharedPreferences.getBoolean("isVacationModeActive", false)
        vacationEndDateMillis = sharedPreferences.getLong("vacationEndDateMillis", 0L)

        // Vérifier si la pause vacances est expirée au chargement
        if (isVacationModeActive && Calendar.getInstance().timeInMillis >= vacationEndDateMillis) {
            isVacationModeActive = false
            vacationEndDateMillis = 0L
            sharedPreferences.edit()
                .putBoolean("isVacationModeActive", false)
                .putLong("vacationEndDateMillis", 0L)
                .apply()
            Log.d("MainActivity", "Pause vacances expirée au chargement. Désactivée.")
        }

        Log.d("MainActivity", "Données chargées. Semaine $currentWeek active. Semaine 1: ${week1Schedule.size} cours, Semaine 2: ${week2Schedule.size} cours, DailyChanges: ${dailyChanges.size} jours, Jours de cours: $selectedCourseDays, Pause vacances: $isVacationModeActive jusqu'à ${if (vacationEndDateMillis > 0) SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(vacationEndDateMillis)) else "N/A"}.")
    }
    
    // NOUVELLE FONCTION : Met à jour le schedule actuel selon la semaine active
    private fun updateCurrentSchedule() {
        schedule = when (currentWeek) {
            1 -> week1Schedule.toMutableList()
            2 -> week2Schedule.toMutableList()
            else -> week1Schedule.toMutableList()
        }
        Log.d("MainActivity", "Schedule mis à jour pour la semaine $currentWeek (${schedule.size} cours)")
    }
    
    // NOUVELLE FONCTION : Change de semaine
    private fun switchWeek(newWeek: Int) {
        if (newWeek != currentWeek && newWeek in 1..2) {
            // Sauvegarder les modifications de la semaine actuelle
            when (currentWeek) {
                1 -> week1Schedule = schedule.toMutableList()
                2 -> week2Schedule = schedule.toMutableList()
            }
            
            // Changer de semaine
            currentWeek = newWeek
            updateCurrentSchedule()
            
            // Mettre à jour l'interface
            updateWeekSelectorUI()
            updateTodayScheduleDisplay()
            
            // Sauvegarder
            saveData()
            
            Log.d("MainActivity", "Changement vers la semaine $currentWeek (${schedule.size} cours)")
        }
    }
    
    // NOUVELLE FONCTION : Met à jour l'interface du sélecteur de semaine
    private fun updateWeekSelectorUI() {
        week1Button.backgroundTintList = ContextCompat.getColorStateList(this, 
            if (currentWeek == 1) android.R.color.holo_blue_dark else android.R.color.darker_gray)
        week2Button.backgroundTintList = ContextCompat.getColorStateList(this, 
            if (currentWeek == 2) android.R.color.holo_blue_dark else android.R.color.darker_gray)
        
        currentWeekIndicator.text = "Semaine $currentWeek active"
        currentWeekIndicator.setTextColor(ContextCompat.getColor(this, 
            if (currentWeek == 1) android.R.color.holo_blue_dark else android.R.color.holo_green_dark))
    }

    private fun saveData() {
        val editor = sharedPreferences.edit()
        editor.putString("classSchedule", gson.toJson(schedule))
        editor.putString("dailyClassChanges", gson.toJson(dailyChanges))
        editor.putStringSet("courseDays", selectedCourseDays)
        // Sauvegarder l'état de la pause vacances
        editor.putBoolean("isVacationModeActive", isVacationModeActive)
        editor.putLong("vacationEndDateMillis", vacationEndDateMillis)
        
        // Sauvegarder les semaines multiples
        editor.putString("week1Schedule", gson.toJson(week1Schedule))
        editor.putString("week2Schedule", gson.toJson(week2Schedule))
        editor.putInt("currentWeek", currentWeek)
        
        editor.apply()
        Log.d("MainActivity", "Données sauvegardées (semaine $currentWeek active).")
    }

    // --- Logique du prochain cours et mise à jour UI ---
    private fun findAndDisplayNextClassAndUI() {
        val now = Calendar.getInstance()
        val currentDayIndex = now.get(Calendar.DAY_OF_WEEK) - 1
        val currentTimeInMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

        var upcomingClasses = mutableListOf<ClassItem>()

        for (i in 0..7) {
            val targetCalendar = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, i)
            }
            val targetDayIndex = targetCalendar.get(Calendar.DAY_OF_WEEK) - 1
            val targetDayName = daysOfWeek[targetDayIndex]

            if (!selectedCourseDays.contains(targetDayName)) {
                continue
            }

            var dailyScheduleForTargetDay = schedule.filter { it.day == targetDayName }.toMutableList()

            if (i == 0) {
                dailyChanges[targetDayName]?.forEach { change ->
                    when (change.type) {
                        "cancel" -> {
                            dailyScheduleForTargetDay.removeAll {
                                it.course == change.originalCourse && it.time == change.originalTime
                            }
                        }
                        "modify" -> {
                            if (change.isNew) {
                                dailyScheduleForTargetDay.add(ClassItem(targetDayName, change.newTime, change.newCourse, change.newRoom))
                            } else {
                                dailyScheduleForTargetDay.replaceAll { c ->
                                    if (c.course == change.originalCourse && c.time == change.originalTime) {
                                        c.copy(
                                            course = change.newCourse.ifEmpty { c.course },
                                            time = change.newTime.ifEmpty { c.time },
                                            room = change.newRoom.ifEmpty { c.room }
                                        )
                                    } else {
                                        c
                                    }
                                }
                            }
                        }
                    }
                }
            }

            dailyScheduleForTargetDay.forEach { classItem ->
                val (hour, minute) = classItem.time.split(":").map { it.toInt() }
                val classCalendar = Calendar.getInstance().apply {
                    timeInMillis = targetCalendar.timeInMillis
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                if (classCalendar.timeInMillis > now.timeInMillis) {
                    upcomingClasses.add(classItem.copy(dateTime = classCalendar.timeInMillis))
                }
            }
        }

        upcomingClasses.sortBy { it.dateTime }

        val nextClass = upcomingClasses.firstOrNull {
            it.dateTime > now.timeInMillis && it.course != INTERNAL_DAILY_REMINDER_COURSE_NAME
        }

        if (nextClass != null) {
            val timeDiff = nextClass.dateTime - now.timeInMillis
            val hours = TimeUnit.MILLISECONDS.toHours(timeDiff)
            val minutes = TimeUnit.MILLISECONDS.toMinutes(timeDiff) % 60
            val seconds = TimeUnit.MILLISECONDS.toSeconds(timeDiff) % 60

            nextCourseText.text = "${nextClass.course} en salle ${nextClass.room}"
            nextCourseTime.text = "(${nextClass.day} à ${nextClass.time})"
            timeLeftText.text = "Commence dans : ${if (hours > 0) "${hours}h " else ""}${minutes}m ${seconds}s"
            timeLeftText.visibility = View.VISIBLE
        } else {
            nextCourseText.text = "Aucun cours à venir."
            nextCourseTime.text = ""
            timeLeftText.visibility = View.GONE
        }
    }


    // --- Affichage de l'emploi du temps du jour ---
    private fun updateTodayScheduleDisplay() {
        val now = Calendar.getInstance()
        val currentDayIndex = now.get(Calendar.DAY_OF_WEEK) - 1
        val todayName = daysOfWeek[currentDayIndex]
        todayScheduleTitle.text = "Votre Emploi du Temps pour $todayName :"

        var displaySchedule = schedule.filter { it.day == todayName }.toMutableList()

        dailyChanges[todayName]?.forEach { change ->
            when (change.type) {
                "cancel" -> {
                    displaySchedule.removeAll {
                        it.course == change.originalCourse && it.time == change.originalTime
                    }
                }
                "modify" -> {
                    if (change.isNew) {
                        displaySchedule.add(ClassItem(todayName, change.newTime, change.newCourse, change.newRoom))
                    } else {
                        displaySchedule.replaceAll { c ->
                            if (c.course == change.originalCourse && c.time == change.originalTime) {
                                c.copy(
                                    course = change.newCourse.ifEmpty { c.course },
                                    time = change.newTime.ifEmpty { c.time },
                                    room = change.newRoom.ifEmpty { c.room }
                                )
                            } else {
                                c
                            }
                        }
                    }
                }
            }
        }

        displaySchedule = displaySchedule.filter { it.course != INTERNAL_DAILY_REMINDER_COURSE_NAME }.toMutableList()

        displaySchedule.sortBy {
            val (h, m) = it.time.split(":").map { it.toInt() }
            h * 60 + m
        }

        todayScheduleAdapter.updateData(displaySchedule)

        if (displaySchedule.isEmpty()) {
            noClassTodayText.visibility = View.VISIBLE
            todayScheduleRecyclerView.visibility = View.GONE
        } else {
            noClassTodayText.visibility = View.GONE
            todayScheduleRecyclerView.visibility = View.VISIBLE
        }
        Log.d("MainActivity", "Affichage de l'emploi du temps du jour mis à jour. Cours affichés: ${displaySchedule.size}")
    }

    // --- Dialogue pour gérer l'emploi du temps annuel ---
    private fun showManageScheduleDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_manage_schedule, null)
        val builder = AlertDialog.Builder(this)
        builder.setView(dialogView)
        val dialog = builder.create()

        val addDayInput: EditText = dialogView.findViewById(R.id.addDayInput)
        val addTimeInput: EditText = dialogView.findViewById(R.id.addTimeInput)
        val addCourseInput: EditText = dialogView.findViewById(R.id.addCourseInput)
        val addRoomInput: EditText = dialogView.findViewById(R.id.addRoomInput)
        val addClassButton: Button = dialogView.findViewById(R.id.addClassButton)
        val annualScheduleRecyclerView: RecyclerView = dialogView.findViewById(R.id.annualScheduleRecyclerView)

        var annualScheduleAdapterInstance: ClassAdapter? = null

        val onDeleteClickLambda: (Int, ClassItem) -> Unit = { position, item ->
            AlertDialog.Builder(this)
                .setTitle("Supprimer un cours")
                .setMessage("Voulez-vous vraiment supprimer ce cours ?")
                .setPositiveButton("Supprimer") { _, _ ->
                    schedule.removeAt(position)
                    saveData()
                    updateTodayScheduleDisplay()
                    annualScheduleAdapterInstance?.updateData(schedule.toMutableList())
                    Toast.makeText(this, "Cours supprimé.", Toast.LENGTH_SHORT).show()
                    NotificationHelper.scheduleNextClassNotification(this)
                    Log.d("MainActivity", "Cours supprimé: ${item.course}")
                }
                .setNegativeButton("Annuler", null)
                .show()
        }

        annualScheduleAdapterInstance = ClassAdapter(schedule.toMutableList(), true, INTERNAL_DAILY_REMINDER_COURSE_NAME, onDeleteClickLambda)

        annualScheduleRecyclerView.layoutManager = LinearLayoutManager(this)
        annualScheduleRecyclerView.adapter = annualScheduleAdapterInstance

        annualScheduleAdapterInstance?.updateData(schedule.toMutableList().apply {
            sortBy {
                val dayOrder = daysOfWeek.indexOf(it.day)
                val (h, m) = it.time.split(":").map { it.toInt() }
                dayOrder * 10000 + h * 60 + m
            }
        })

        addClassButton.setOnClickListener {
            val day = addDayInput.text.toString().trim()
            val time = addTimeInput.text.toString().trim()
            val course = addCourseInput.text.toString().trim()
            val room = addRoomInput.text.toString().trim()

            if (day.isNotEmpty() && time.isNotEmpty() && course.isNotEmpty() && room.isNotEmpty()) {
                val newClass = ClassItem(day, time, course, room)
                schedule.add(newClass)
                saveData()
                updateTodayScheduleDisplay()
                annualScheduleAdapterInstance?.updateData(schedule.toMutableList().apply {
                    sortBy {
                        val dayOrder = daysOfWeek.indexOf(it.day)
                        val (h, m) = it.time.split(":").map { it.toInt() }
                        dayOrder * 10000 + h * 60 + m
                    }
                })
                Toast.makeText(this, "Cours ajouté !", Toast.LENGTH_SHORT).show()
                addDayInput.text.clear()
                addTimeInput.text.clear()
                addCourseInput.text.clear()
                addRoomInput.text.clear()
                NotificationHelper.scheduleNextClassNotification(this)
                Log.d("MainActivity", "Cours ajouté: $newClass")
            } else {
                Toast.makeText(this, "Veuillez remplir tous les champs.", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.setOnDismissListener {
            loadData()
            updateTodayScheduleDisplay()
            NotificationHelper.scheduleNextClassNotification(this)
            Log.d("MainActivity", "Dialogue Gérer Emploi du Temps Annuel fermé.")
        }
        dialog.show()
    }

    // --- Dialogue pour les modifications quotidiennes ---
    private fun showDailyChangesDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_daily_changes, null)
        val builder = AlertDialog.Builder(this)
        builder.setView(dialogView)
        val dialog = builder.create()

        val currentDay = daysOfWeek[Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1]
        dialogView.findViewById<TextView>(R.id.dailyChangesTitle).text = "Modifications Quotidiennes pour $currentDay"

        val cancelCourseInput: EditText = dialogView.findViewById(R.id.cancelCourseInput)
        val cancelTimeInput: EditText = dialogView.findViewById(R.id.cancelTimeInput)
        val cancelClassButton: Button = dialogView.findViewById(R.id.cancelClassButton)

        cancelClassButton.setOnClickListener {
            val course = cancelCourseInput.text.toString().trim()
            val time = cancelTimeInput.text.toString().trim()
            if (course.isNotEmpty() && time.isNotEmpty()) {
                addDailyChange("cancel", course, time)
                Toast.makeText(this, "Cours annulé pour aujourd'hui.", Toast.LENGTH_SHORT).show()
                cancelCourseInput.text.clear()
                cancelTimeInput.text.clear()
                dialog.dismiss()
                Log.d("MainActivity", "Changement quotidien: Cours annulé ($course à $time)")
            } else {
                Toast.makeText(this, "Veuillez entrer le cours et l'heure à annuler.", Toast.LENGTH_SHORT).show()
            }
        }

        val modifyOriginalCourseInput: EditText = dialogView.findViewById(R.id.modifyOriginalCourseInput)
        val modifyOriginalTimeInput: EditText = dialogView.findViewById(R.id.modifyOriginalTimeInput)
        val modifyNewRoomInput: EditText = dialogView.findViewById(R.id.modifyNewRoomInput)
        val modifyNewCourseNameInput: EditText = dialogView.findViewById(R.id.modifyNewCourseNameInput)
        val modifyNewCourseTimeInput: EditText = dialogView.findViewById(R.id.modifyNewCourseTimeInput)
        val applyModificationButton: Button = dialogView.findViewById(R.id.applyModificationButton)

        applyModificationButton.setOnClickListener {
            val originalCourse = modifyOriginalCourseInput.text.toString().trim()
            val originalTime = modifyOriginalTimeInput.text.toString().trim()
            val newRoom = modifyNewRoomInput.text.toString().trim()
            val newCourseName = modifyNewCourseNameInput.text.toString().trim()
            val newCourseTime = modifyNewCourseTimeInput.text.toString().trim()

            if (originalCourse.isNotEmpty() && originalTime.isNotEmpty() && (newRoom.isNotEmpty() || newCourseName.isNotEmpty() || newCourseTime.isNotEmpty())) {
                addDailyChange("modify", originalCourse, originalTime, newRoom, newCourseName, newCourseTime, false)
                Toast.makeText(this, "Modification appliquée pour aujourd'hui.", Toast.LENGTH_SHORT).show()
                modifyOriginalCourseInput.text.clear()
                modifyOriginalTimeInput.text.clear()
                modifyNewRoomInput.text.clear()
                modifyNewCourseNameInput.text.clear()
                modifyNewCourseTimeInput.text.clear()
                dialog.dismiss()
                Log.d("MainActivity", "Changement quotidien: Cours modifié ($originalCourse à $originalTime -> $newCourseName à $newCourseTime en salle $newRoom)")
            } else {
                Toast.makeText(this, "Veuillez spécifier le cours original et au moins une modification.", Toast.LENGTH_SHORT).show()
            }
        }

        val addNewCourseNameInput: EditText = dialogView.findViewById(R.id.addNewCourseNameInput)
        val addNewCourseTimeInput: EditText = dialogView.findViewById(R.id.addNewCourseTimeInput)
        val addNewRoomInput: EditText = dialogView.findViewById(R.id.addNewRoomInput)
        val addNewCourseButton: Button = dialogView.findViewById(R.id.addNewCourseButton)

        addNewCourseButton.setOnClickListener {
            val newCourseName = addNewCourseNameInput.text.toString().trim()
            val newCourseTime = addNewCourseTimeInput.text.toString().trim()
            val newRoom = addNewRoomInput.text.toString().trim()

            if (newCourseName.isNotEmpty() && newCourseTime.isNotEmpty() && newRoom.isNotEmpty()) {
                addDailyChange("modify", "", "", newRoom, newCourseName, newCourseTime, true)
                Toast.makeText(this, "Nouveau cours ponctuel ajouté.", Toast.LENGTH_SHORT).show()
                addNewCourseNameInput.text.clear()
                addNewCourseTimeInput.text.clear()
                addNewRoomInput.text.clear()
                dialog.dismiss()
                Log.d("MainActivity", "Changement quotidien: Nouveau cours ponctuel ajouté ($newCourseName à $newCourseTime en salle $newRoom)")
            } else {
                Toast.makeText(this, "Veuillez remplir tous les champs pour le nouveau cours.", Toast.LENGTH_SHORT).show()
            }
        }

        val currentChangesRecyclerView: RecyclerView = dialogView.findViewById(R.id.currentChangesRecyclerView)
        currentChangesRecyclerView.layoutManager = LinearLayoutManager(this)
        val currentDailyChangesForToday = dailyChanges[currentDay] ?: mutableListOf()
        val changesAdapter = DailyChangesAdapter(currentDailyChangesForToday)
        currentChangesRecyclerView.adapter = changesAdapter

        val resetButton: Button = dialogView.findViewById(R.id.resetDailyChangesButton)
        if (currentDailyChangesForToday.isEmpty()) {
            resetButton.visibility = View.GONE
        } else {
            resetButton.visibility = View.VISIBLE
        }
        resetButton.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Réinitialiser les changements")
                .setMessage("Voulez-vous vraiment annuler toutes les modifications pour aujourd'hui ?")
                .setPositiveButton("Réinitialiser") { _, _ ->
                    dailyChanges.remove(currentDay)
                    saveData()
                    updateTodayScheduleDisplay()
                    Toast.makeText(this, "Changements quotidiens réinitialisés.", Toast.LENGTH_SHORT).show()
                    NotificationHelper.scheduleNextClassNotification(this)
                    Log.d("MainActivity", "Changements quotidiens réinitialisés pour $currentDay.")
                }
                .setNegativeButton("Annuler", null)
                .show()
        }

        dialog.setOnDismissListener {
            loadData()
            updateTodayScheduleDisplay()
            NotificationHelper.scheduleNextClassNotification(this)
            Log.d("MainActivity", "Dialogue Modifications Quotidiennes fermé.")
        }
        dialog.show()
    }

    private fun addDailyChange(
        type: String,
        originalCourse: String,
        originalTime: String,
        newRoom: String = "",
        newCourse: String = "",
        newTime: String = "",
        isNew: Boolean = false
    ) {
        val currentDay = daysOfWeek[Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1]
        val changesForToday = dailyChanges.getOrPut(currentDay) { mutableListOf() }
        changesForToday.add(DailyChange(type, originalCourse, originalTime, newCourse, newTime, newRoom, isNew))
        saveData()
    }

    class ClassAdapter(
        private var classList: MutableList<ClassItem>,
        private val showDeleteButton: Boolean,
        private val internalDailyReminderCourseName: String,
        private val onDeleteClick: (Int, ClassItem) -> Unit
    ) : RecyclerView.Adapter<ClassAdapter.ClassViewHolder>() {

        class ClassViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val classItemText: TextView = itemView.findViewById(R.id.classItemText)
            val deleteButton: ImageButton = itemView.findViewById(R.id.deleteClassButton)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClassViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_class, parent, false)
            return ClassViewHolder(view)
        }

        override fun onBindViewHolder(holder: ClassViewHolder, position: Int) {
            val currentItem = classList[position]
            holder.classItemText.text = "${currentItem.time} - ${currentItem.course} (Salle: ${currentItem.room})"

            if (showDeleteButton) {
                holder.deleteButton.visibility = View.VISIBLE
                holder.deleteButton.setOnClickListener {
                    onDeleteClick(holder.adapterPosition, currentItem)
                }
            } else {
                holder.deleteButton.visibility = View.GONE
            }
        }

        override fun getItemCount() = classList.size

        fun updateData(newList: MutableList<ClassItem>) {
            classList = newList.filter { it.course != internalDailyReminderCourseName }.toMutableList()
            notifyDataSetChanged()
        }
    }

    class DailyChangesAdapter(private var changesList: MutableList<DailyChange>) :
        RecyclerView.Adapter<DailyChangesAdapter.ChangeViewHolder>() {

        class ChangeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val changeItemText: TextView = itemView.findViewById(R.id.classItemText)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChangeViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_class, parent, false)
            view.findViewById<ImageButton>(R.id.deleteClassButton)?.visibility = View.GONE
            return ChangeViewHolder(view)
        }

        override fun onBindViewHolder(holder: ChangeViewHolder, position: Int) {
            val change = changesList[position]
            holder.changeItemText.text = when (change.type) {
                "cancel" -> "❌ Annulation: ${change.originalCourse} à ${change.originalTime}"
                "modify" -> {
                    if (change.isNew) {
                        "✏️ Nouveau Cours: ${change.newCourse} (${change.newTime}) Salle: ${change.newRoom}"
                    } else {
                        "✏️ Modification: De ${change.originalCourse} (${change.originalTime}) -> " +
                                "${change.newCourse.ifEmpty { "même cours" }} " +
                                "(${change.newTime.ifEmpty { "même heure" }}) " +
                                "Salle: ${change.newRoom.ifEmpty { "même salle" }}"
                    }
                }
                else -> ""
            }
            try {
                holder.changeItemText.setTextColor(
                    ContextCompat.getColor(holder.itemView.context, R.color.colorPrimaryDark)
                )
            } catch (e: Exception) {
                holder.changeItemText.setTextColor(Color.parseColor("#A07740"))
            }
        }

        override fun getItemCount() = changesList.size
    }

    // --- Fonctions d'optimisation de batterie ---
    private fun checkAndRequestBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val packageName = packageName
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                AlertDialog.Builder(this)
                    .setTitle("Désactiver l'optimisation de batterie")
                    .setMessage("Pour garantir que les rappels fonctionnent de manière fiable même lorsque l'application est fermée, veuillez autoriser cette application à ignorer les optimisations de batterie. Cela aide à prévenir que le système ne la mette en veille.")
                    .setPositiveButton("Continuer") { dialog, which ->
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        intent.data = Uri.parse("package:$packageName")
                        try {
                            startActivity(intent)
                        } catch (e: Exception) {
                            Log.e("MainActivity", "Impossible de lancer les paramètres d'optimisation batterie: ${e.message}")
                            Toast.makeText(this, "Impossible d'ouvrir les paramètres. Veuillez le faire manuellement : Paramètres > Applications > Votre App > Batterie > Désactiver l'optimisation.", Toast.LENGTH_LONG).show()
                        }
                    }
                    .setNegativeButton("Annuler") { dialog, which ->
                        Toast.makeText(this, "Les rappels pourraient ne pas être fiables sans cette permission.", Toast.LENGTH_LONG).show()
                    }
                    .show()
                Log.d("MainActivity", "Demande d'ignorance de l'optimisation batterie affichée.")
            } else {
                Toast.makeText(this, "L'optimisation de batterie est déjà ignorée pour cette application. Les rappels devraient être fiables.", Toast.LENGTH_LONG).show()
                Log.d("MainActivity", "L'optimisation batterie est déjà ignorée.")
            }
        } else {
            Toast.makeText(this, "L'optimisation de batterie n'est pas applicable sur cette version d'Android.", Toast.LENGTH_SHORT).show()
            Log.d("MainActivity", "Version Android < M, optimisation batterie non applicable.")
        }
    }

    // Met à jour le texte du bouton d'optimisation de batterie
    private fun updateBatteryOptimizationButtonState() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (pm.isIgnoringBatteryOptimizations(packageName)) {
                requestBatteryOptimizationButton.text = "Optimisation Batterie: OK ✅"
                requestBatteryOptimizationButton.setBackgroundColor(Color.parseColor("#2ECC71"))
            } else {
                requestBatteryOptimizationButton.text = "Optimisation Batterie: Requis ⚠️"
                requestBatteryOptimizationButton.setBackgroundColor(Color.parseColor("#E67E22"))
            }
        } else {
            requestBatteryOptimizationButton.text = "Optimisation Batterie: N/A"
            requestBatteryOptimizationButton.setBackgroundColor(Color.parseColor("#95A5A6"))
        }
    }

    // Met à jour le texte du bouton des jours de cours
    private fun updateCourseDaysButtonState() {
        if (selectedCourseDays.size == daysOfWeek.size) {
            setCourseDaysButton.text = "Jours de Cours: Tous les jours"
        } else if (selectedCourseDays.isEmpty()) {
            setCourseDaysButton.text = "Jours de Cours: Aucun"
        } else {
            val sortedDays = daysOfWeek.filter { selectedCourseDays.contains(it) }
            val displayNames = sortedDays.map { it.substring(0, 2) }
            setCourseDaysButton.text = "Jours de Cours: ${displayNames.joinToString(", ")}"
        }
    }

    // Met à jour le texte du bouton de pause vacances
    private fun updateVacationPauseButtonState() {
        if (isVacationModeActive) {
            if (vacationEndDateMillis > 0 && Calendar.getInstance().timeInMillis < vacationEndDateMillis) {
                val dateFormat = SimpleDateFormat("dd/MM", Locale.getDefault())
                val endDateFormatted = dateFormat.format(Date(vacationEndDateMillis))
                vacationPauseButton.text = "Pause Vacances: Active jusqu'au $endDateFormatted 🏖️"
                vacationPauseButton.setBackgroundColor(Color.parseColor("#FF6347"))
            } else {
                vacationPauseButton.text = "Pause Vacances: Expirée (cliquez pour réactiver)"
                vacationPauseButton.setBackgroundColor(Color.parseColor("#808080"))
            }
        } else {
            vacationPauseButton.text = "Pause Vacances: Désactivée"
            vacationPauseButton.setBackgroundColor(Color.parseColor("#6A5ACD"))
        }
    }

    // Met à jour le texte du bouton de délai des rappels
    private fun updateReminderDelayButtonState() {
        val delayMinutes = sharedPreferences.getInt("reminderDelayMinutes", 5)
        val delayText = when (delayMinutes) {
            0 -> "Immédiat"
            1 -> "1 min"
            60 -> "1 heure"
            else -> "$delayMinutes min"
        }
        setReminderDelayButton.text = "Délai des rappels: $delayText"
    }

    // NOUVELLE FONCTION : Met à jour l'affichage de la version et du pseudo
    private fun updateAppInfoDisplay() {
        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            val versionName = pInfo.versionName
            appVersionText.text = "Version: $versionName"
        } catch (e: Exception) {
            Log.e("MainActivity", "Erreur lors de la récupération de la version de l'application: ${e.message}")
            appVersionText.text = "Version: N/A"
        }

        val sessionManager = com.example.franwan.auth.SessionManager(this)
        val userName = if (sessionManager.isGuestMode()) {
            "Invité"
        } else {
            sessionManager.getUserDisplayName() ?: "Utilisateur"
        }
        appUserText.text = "Utilisateur: $userName"
    }

    // NOUVELLE FONCTION : Affiche le menu des paramètres
    private fun showSettingsMenu() {
        val sessionManager = SessionManager(this)
        val popupMenu = PopupMenu(this, settingsButton)
        
        if (sessionManager.isGuestMode()) {
            // Mode invité : option pour se connecter
            popupMenu.menu.add("Se connecter")
        } else {
            // Mode connecté : afficher les infos du compte et option de déconnexion
            val displayName = sessionManager.getUserDisplayName() ?: "Utilisateur"
            popupMenu.menu.add("Compte: $displayName")
            popupMenu.menu.add("Se déconnecter")
        }
        
        popupMenu.setOnMenuItemClickListener { menuItem ->
            when (menuItem.title) {
                "Se connecter" -> {
                    // Désactiver le mode invité et rediriger vers la page de connexion
                    sessionManager.setGuestMode(false)
                    startActivity(Intent(this, LoginActivity::class.java))
                    finish()
                }
                "Se déconnecter" -> {
                    // Afficher une confirmation de déconnexion
                    AlertDialog.Builder(this)
                        .setTitle("Se déconnecter")
                        .setMessage("Voulez-vous vraiment vous déconnecter ?")
                        .setPositiveButton("Déconnecter") { _, _ ->
                            sessionManager.clearSession()
                            startActivity(Intent(this, LoginActivity::class.java))
                            finish()
                        }
                        .setNegativeButton("Annuler", null)
                        .show()
                }
                else -> {
                    // Afficher les informations du compte
                    if (menuItem.title.toString().startsWith("Compte:")) {
                        val displayName = sessionManager.getUserDisplayName() ?: "Utilisateur"
                        AlertDialog.Builder(this)
                            .setTitle("Informations du compte")
                            .setMessage("Pseudo: $displayName")
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }
            }
            true
        }
        
        popupMenu.show()
    }
    
    // NOUVELLE FONCTION : Affiche le dialogue d'importation PDF
    private fun showImportPdfDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_import_pdf, null)
        val builder = AlertDialog.Builder(this)
        builder.setView(dialogView)
        val dialog = builder.create()
        
        val selectPdfButton: Button = dialogView.findViewById(R.id.selectPdfButton)
        val selectedFileText: TextView = dialogView.findViewById(R.id.selectedFileText)
        val importButton: Button = dialogView.findViewById(R.id.importButton)
        val cancelButton: Button = dialogView.findViewById(R.id.cancelButton)
        val importProgressBar: ProgressBar = dialogView.findViewById(R.id.importProgressBar)
        val importStatusText: TextView = dialogView.findViewById(R.id.importStatusText)
        
        // Afficher le nom du fichier sélectionné
        selectedPdfUri?.let { uri ->
            val fileName = uri.lastPathSegment ?: "Fichier PDF"
            selectedFileText.text = "Fichier sélectionné: $fileName"
            importButton.isEnabled = true
        }
        
        // Bouton pour sélectionner un autre fichier
        selectPdfButton.setOnClickListener {
            selectPdfLauncher.launch("application/pdf")
            dialog.dismiss()
        }
        
        // Bouton d'importation
        importButton.setOnClickListener {
            importPdfFile(uri = selectedPdfUri!!, dialog, importProgressBar, importStatusText, importButton)
        }
        
        // Bouton d'annulation
        cancelButton.setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    // NOUVELLE FONCTION : Importe le fichier PDF sélectionné
    private fun importPdfFile(uri: Uri, dialog: AlertDialog, progressBar: ProgressBar, statusText: TextView, importButton: Button) {
        progressBar.visibility = View.VISIBLE
        importButton.isEnabled = false
        statusText.text = "Importation en cours..."
        
        // Exécuter l'importation dans un thread séparé
        Thread {
            try {
                val inputStream = contentResolver.openInputStream(uri)
                val courses = pdfParser.parsePdf(inputStream!!)
                inputStream?.close()
                
                // Mettre à jour l'UI sur le thread principal
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    
                    if (courses.isNotEmpty()) {
                        // Ajouter les cours importés à la semaine active
                        val coursesWithWeek = courses.map { it.copy(week = currentWeek) }
                        schedule.addAll(coursesWithWeek)
                        
                        // Mettre à jour la liste de la semaine active
                        when (currentWeek) {
                            1 -> week1Schedule.addAll(coursesWithWeek)
                            2 -> week2Schedule.addAll(coursesWithWeek)
                        }
                        
                        saveData()
                        updateTodayScheduleDisplay()
                        
                        statusText.text = "${courses.size} cours importés dans la semaine $currentWeek !"
                        statusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
                        
                        // Fermer le dialogue après 2 secondes
                        Handler(Looper.getMainLooper()).postDelayed({
                            dialog.dismiss()
                            Toast.makeText(this, "${courses.size} cours importés dans la semaine $currentWeek !", Toast.LENGTH_LONG).show()
                        }, 2000)
                        
                    } else {
                        statusText.text = "Aucun cours trouvé dans le PDF. Vérifiez le format."
                        statusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
                        importButton.isEnabled = true
                    }
                }
                
            } catch (e: Exception) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    statusText.text = "Erreur lors de l'importation: ${e.message}"
                    statusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
                    importButton.isEnabled = true
                    Log.e("MainActivity", "Erreur lors de l'importation PDF", e)
                }
            }
        }.start()
    }
    
    // NOUVELLE FONCTION : Affiche le dialogue de suppression en masse
    private fun showBulkDeleteDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_bulk_delete_courses, null)
        val builder = AlertDialog.Builder(this)
        builder.setView(dialogView)
        val dialog = builder.create()
        
        val deleteAllButton: Button = dialogView.findViewById(R.id.deleteAllCoursesButton)
        val deleteMondayButton: Button = dialogView.findViewById(R.id.deleteMondayButton)
        val deleteTuesdayButton: Button = dialogView.findViewById(R.id.deleteTuesdayButton)
        val deleteWednesdayButton: Button = dialogView.findViewById(R.id.deleteWednesdayButton)
        val deleteThursdayButton: Button = dialogView.findViewById(R.id.deleteThursdayButton)
        val deleteFridayButton: Button = dialogView.findViewById(R.id.deleteFridayButton)
        val deleteSaturdayButton: Button = dialogView.findViewById(R.id.deleteSaturdayButton)
        val deleteSundayButton: Button = dialogView.findViewById(R.id.deleteSundayButton)
        val deleteByCourseTypeButton: Button = dialogView.findViewById(R.id.deleteByCourseTypeButton)
        val statisticsText: TextView = dialogView.findViewById(R.id.statisticsText)
        val cancelButton: Button = dialogView.findViewById(R.id.cancelButton)
        
        // Mettre à jour les statistiques
        updateDeleteStatistics(statisticsText)
        
        // Supprimer tous les cours - APPROCHE DIRECTE
        deleteAllButton.setOnClickListener {
            Log.d("MainActivity", "Bouton 'Supprimer TOUS' cliqué")
            val totalCourses = schedule.size
            if (totalCourses > 0) {
                AlertDialog.Builder(this)
                    .setTitle("Confirmation de suppression")
                    .setMessage("Êtes-vous sûr de vouloir supprimer TOUS les cours ($totalCourses cours) ?\n\n⚠️ Cette action est irréversible !")
                    .setPositiveButton("🗑️ Supprimer") { _, _ ->
                        Log.d("MainActivity", "Suppression de tous les cours confirmée")
                        deleteAllCourses()
                        dialog.dismiss()
                        Toast.makeText(this, "Tous les cours supprimés avec succès", Toast.LENGTH_LONG).show()
                    }
                    .setNegativeButton("❌ Annuler", null)
                    .show()
            } else {
                Toast.makeText(this, "Aucun cours à supprimer", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Supprimer par jour - APPROCHE DIRECTE
        val dayButtons = mapOf(
            "Lundi" to deleteMondayButton,
            "Mardi" to deleteTuesdayButton,
            "Mercredi" to deleteWednesdayButton,
            "Jeudi" to deleteThursdayButton,
            "Vendredi" to deleteFridayButton,
            "Samedi" to deleteSaturdayButton,
            "Dimanche" to deleteSundayButton
        )
        
        dayButtons.forEach { (day, button) ->
            button.setOnClickListener {
                Log.d("MainActivity", "Bouton $day cliqué")
                val dayCourses = schedule.filter { it.day == day }
                Log.d("MainActivity", "Cours trouvés pour $day : ${dayCourses.size}")
                if (dayCourses.isNotEmpty()) {
                    AlertDialog.Builder(this)
                        .setTitle("Confirmation de suppression")
                        .setMessage("Êtes-vous sûr de vouloir supprimer tous les cours du $day (${dayCourses.size} cours) ?\n\n⚠️ Cette action est irréversible !")
                        .setPositiveButton("🗑️ Supprimer") { _, _ ->
                            Log.d("MainActivity", "Suppression des cours du $day confirmée")
                            deleteCoursesByDay(day)
                            dialog.dismiss()
                            Toast.makeText(this, "Cours du $day supprimés avec succès", Toast.LENGTH_LONG).show()
                        }
                        .setNegativeButton("❌ Annuler", null)
                        .show()
                } else {
                    Toast.makeText(this, "Aucun cours trouvé pour le $day", Toast.LENGTH_SHORT).show()
                }
            }
        }
        
        // Supprimer par type de cours
        deleteByCourseTypeButton.setOnClickListener {
            showDeleteByCourseNameDialog()
            dialog.dismiss()
        }
        
        // Annuler
        cancelButton.setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    // NOUVELLE FONCTION : Affiche le dialogue de suppression par nom de cours
    private fun showDeleteByCourseNameDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_delete_by_course_name, null)
        val builder = AlertDialog.Builder(this)
        builder.setView(dialogView)
        val dialog = builder.create()
        
        val courseNameInput: EditText = dialogView.findViewById(R.id.courseNameInput)
        val matchingCoursesText: TextView = dialogView.findViewById(R.id.matchingCoursesText)
        val deleteButton: Button = dialogView.findViewById(R.id.deleteButton)
        val cancelButton: Button = dialogView.findViewById(R.id.cancelButton)
        
        // Recherche en temps réel
        courseNameInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val searchTerm = s.toString().trim()
                if (searchTerm.isNotEmpty()) {
                    val matchingCourses = schedule.filter { 
                        it.course.contains(searchTerm, ignoreCase = true) 
                    }
                    if (matchingCourses.isNotEmpty()) {
                        matchingCoursesText.text = "Cours trouvés : ${matchingCourses.size}\n" +
                            matchingCourses.take(3).joinToString("\n") { "• ${it.course} (${it.day} ${it.time})" } +
                            if (matchingCourses.size > 3) "\n..." else ""
                        deleteButton.isEnabled = true
                    } else {
                        matchingCoursesText.text = "Aucun cours trouvé"
                        deleteButton.isEnabled = false
                    }
                } else {
                    matchingCoursesText.text = ""
                    deleteButton.isEnabled = false
                }
            }
        })
        
        // Supprimer
        deleteButton.setOnClickListener {
            val searchTerm = courseNameInput.text.toString().trim()
            if (searchTerm.isNotEmpty()) {
                val matchingCourses = schedule.filter { 
                    it.course.contains(searchTerm, ignoreCase = true) 
                }
                if (matchingCourses.isNotEmpty()) {
                    AlertDialog.Builder(this)
                        .setTitle("Confirmation de suppression")
                        .setMessage("Supprimer ${matchingCourses.size} cours contenant \"$searchTerm\" ?\n\n⚠️ Cette action est irréversible !")
                        .setPositiveButton("🗑️ Supprimer") { _, _ ->
                            deleteCoursesBySearchTerm(searchTerm)
                            dialog.dismiss()
                            Toast.makeText(this, "${matchingCourses.size} cours supprimés", Toast.LENGTH_LONG).show()
                        }
                        .setNegativeButton("❌ Annuler", null)
                        .show()
                }
            }
        }
        
        // Annuler
        cancelButton.setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    // NOUVELLE FONCTION : Met à jour les statistiques de suppression
    private fun updateDeleteStatistics(statisticsText: TextView) {
        val totalCourses = schedule.size
        val coursesByDay = schedule.groupBy { it.day }
        val week1Total = week1Schedule.size
        val week2Total = week2Schedule.size
        
        val stats = buildString {
            append("📊 Statistiques des semaines :\n")
            append("Semaine 1 : $week1Total cours\n")
            append("Semaine 2 : $week2Total cours\n")
            append("Semaine active ($currentWeek) : $totalCourses cours\n")
            append("\n📅 Répartition par jour :\n")
            coursesByDay.forEach { (day, courses) ->
                append("$day : ${courses.size} cours\n")
            }
        }
        
        statisticsText.text = stats
    }
    
    // NOUVELLE FONCTION : Supprime tous les cours
    private fun deleteAllCourses() {
        Log.d("MainActivity", "=== DÉBUT deleteAllCourses ===")
        val deletedCount = schedule.size
        Log.d("MainActivity", "Nombre de cours avant suppression : $deletedCount")
        
        // Vider la semaine active
        schedule.clear()
        
        // Mettre à jour la liste de la semaine active
        when (currentWeek) {
            1 -> week1Schedule.clear()
            2 -> week2Schedule.clear()
        }
        
        Log.d("MainActivity", "Liste des cours vidée, nouvelle taille : ${schedule.size}")
        saveData()
        Log.d("MainActivity", "Données sauvegardées")
        updateTodayScheduleDisplay()
        Log.d("MainActivity", "Affichage mis à jour")
        Log.d("MainActivity", "=== FIN deleteAllCourses : $deletedCount cours supprimés de la semaine $currentWeek ===")
    }
    
    // NOUVELLE FONCTION : Supprime tous les cours d'un jour spécifique
    private fun deleteCoursesByDay(day: String) {
        Log.d("MainActivity", "=== DÉBUT deleteCoursesByDay pour $day ===")
        val deletedCount = schedule.count { it.day == day }
        Log.d("MainActivity", "Nombre de cours pour $day avant suppression : $deletedCount")
        // Supprimer de la semaine active
        schedule.removeAll { it.day == day }
        
        // Mettre à jour la liste de la semaine active
        when (currentWeek) {
            1 -> week1Schedule.removeAll { it.day == day }
            2 -> week2Schedule.removeAll { it.day == day }
        }
        
        Log.d("MainActivity", "Cours du $day supprimés, nouvelle taille totale : ${schedule.size}")
        saveData()
        Log.d("MainActivity", "Données sauvegardées")
        updateTodayScheduleDisplay()
        Log.d("MainActivity", "Affichage mis à jour")
        Log.d("MainActivity", "=== FIN deleteCoursesBySearchTerm : $deletedCount cours supprimés de la semaine $currentWeek ===")
    }
    
    // NOUVELLE FONCTION : Supprime les cours par terme de recherche
    private fun deleteCoursesBySearchTerm(searchTerm: String) {
        Log.d("MainActivity", "=== DÉBUT deleteCoursesBySearchTerm pour \"$searchTerm\" ===")
        val deletedCount = schedule.count { 
            it.course.contains(searchTerm, ignoreCase = true) 
        }
        Log.d("MainActivity", "Nombre de cours contenant \"$searchTerm\" avant suppression : $deletedCount")
        // Supprimer de la semaine active
        schedule.removeAll { 
            it.course.contains(searchTerm, ignoreCase = true) 
        }
        
        // Mettre à jour la liste de la semaine active
        when (currentWeek) {
            1 -> week1Schedule.removeAll { it.course.contains(searchTerm, ignoreCase = true) }
            2 -> week2Schedule.removeAll { it.course.contains(searchTerm, ignoreCase = true) }
        }
        
        Log.d("MainActivity", "Cours supprimés, nouvelle taille totale : ${schedule.size}")
        saveData()
        Log.d("MainActivity", "Données sauvegardées")
        updateTodayScheduleDisplay()
        Log.d("MainActivity", "Affichage mis à jour")
        Log.d("MainActivity", "=== FIN deleteCoursesByDay : $deletedCount cours supprimés de la semaine $currentWeek ===")
    }
}
