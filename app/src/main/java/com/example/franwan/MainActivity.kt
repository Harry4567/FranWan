package com.example.franwan

import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import android.util.Log

// Modèle de données pour un cours
data class ClassItem(
    val day: String,
    val time: String,
    val course: String,
    val room: String,
    var dateTime: Long = 0L
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

    // NOUVELLE CONSTANTE : Nom du cours invisible pour le rappel quotidien
    private val INTERNAL_DAILY_REMINDER_COURSE_NAME = "INTERNAL_DAILY_REMINDER_COURSE"

    // Vues de l'interface utilisateur
    private lateinit var nextCourseText: TextView
    private lateinit var nextCourseTime: TextView
    private lateinit var timeLeftText: TextView
    private lateinit var simulateNotificationButton: Button
    private lateinit var manageScheduleButton: Button
    private lateinit var dailyChangesButton: Button
    private lateinit var setDailyReminderTimeButton: Button
    private lateinit var todayScheduleTitle: TextView
    private lateinit var todayScheduleRecyclerView: RecyclerView
    private lateinit var noClassTodayText: TextView
    private lateinit var requestBatteryOptimizationButton: Button // Nouveau bouton

    // Adaptateur pour le RecyclerView de l'emploi du temps du jour
    private lateinit var todayScheduleAdapter: ClassAdapter

    // Stockage local
    private lateinit var sharedPreferences: SharedPreferences
    private val gson = Gson()

    // Données de l'application
    private var schedule: MutableList<ClassItem> = mutableListOf()
    private var dailyChanges: MutableMap<String, MutableList<DailyChange>> = mutableMapOf()

    // Gestion du temps
    private val handler = Handler(Looper.getMainLooper())
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
            Toast.makeText(this, "Permission de notification accordée !", Toast.LENGTH_SHORT).show()
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
        setContentView(R.layout.activity_main)

        nextCourseText = findViewById(R.id.nextCourseText)
        nextCourseTime = findViewById(R.id.nextCourseTime)
        timeLeftText = findViewById(R.id.timeLeftText)
        simulateNotificationButton = findViewById(R.id.simulateNotificationButton)
        manageScheduleButton = findViewById(R.id.manageScheduleButton)
        dailyChangesButton = findViewById(R.id.dailyChangesButton)
        setDailyReminderTimeButton = findViewById(R.id.setDailyReminderTimeButton)
        todayScheduleTitle = findViewById(R.id.todayScheduleTitle)
        todayScheduleRecyclerView = findViewById(R.id.todayScheduleRecyclerView)
        noClassTodayText = findViewById(R.id.noClassTodayText)
        requestBatteryOptimizationButton = findViewById(R.id.requestBatteryOptimizationButton) // Initialisation du nouveau bouton

        sharedPreferences = getSharedPreferences("ClassSchedulerApp", Context.MODE_PRIVATE)

        loadData()

        todayScheduleRecyclerView.layoutManager = LinearLayoutManager(this)
        // Passer le nom du cours invisible à l'adaptateur pour qu'il puisse le filtrer
        todayScheduleAdapter = ClassAdapter(mutableListOf(), false, INTERNAL_DAILY_REMINDER_COURSE_NAME) { _, _ -> /* Pas d'action de suppression dans la vue principale */ }
        todayScheduleRecyclerView.adapter = todayScheduleAdapter

        requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        NotificationHelper.createNotificationChannels(this)

        simulateNotificationButton.setOnClickListener {
            // Simuler une notification de cours
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

        requestBatteryOptimizationButton.setOnClickListener {
            checkAndRequestBatteryOptimization()
            Log.d("MainActivity", "Bouton Vérifier Optimisation Batterie cliqué.")
        }

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
        // Récupérer l'heure actuelle du rappel quotidien ou la valeur par défaut
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
                // Re-planifier toutes les notifications, y compris le rappel quotidien invisible
                NotificationHelper.scheduleNextClassNotification(this)
                Log.d("MainActivity", "Heure de rappel quotidien définie à ${String.format("%02d:%02d", hourOfDay, minute)}")
            },
            currentHour,
            currentMinute,
            true
        )
        timePickerDialog.show()
    }

    override fun onResume() {
        super.onResume()
        handler.post(updateTimeRunnable)
        loadData()
        updateTodayScheduleDisplay()
        // Re-planifier toutes les notifications quand l'app revient au premier plan
        NotificationHelper.scheduleNextClassNotification(this)
        Log.d("MainActivity", "MainActivity onResume appelé. Notifications re-planifiées.")
        // Mettre à jour l'état du bouton d'optimisation batterie
        updateBatteryOptimizationButtonState()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(updateTimeRunnable)
        // Re-planifier toutes les notifications quand l'app passe en arrière-plan (pour s'assurer que les alarmes sont actives)
        NotificationHelper.scheduleNextClassNotification(this)
        Log.d("MainActivity", "MainActivity onPause appelé. Notifications re-planifiées en arrière-plan.")
    }

    // --- Gestion des données ---
    private fun loadData() {
        val scheduleJson = sharedPreferences.getString("classSchedule", null)
        if (scheduleJson != null) {
            val type = object : TypeToken<MutableList<ClassItem>>() {}.type
            schedule = gson.fromJson(scheduleJson, type)
        } else {
            schedule = mutableListOf()
        }

        val dailyChangesJson = sharedPreferences.getString("dailyClassChanges", null)
        if (dailyChangesJson != null) {
            dailyChanges = gson.fromJson(dailyChangesJson, object : TypeToken<MutableMap<String, MutableList<DailyChange>>>() {}.type) as MutableMap<String, MutableList<DailyChange>>
        } else {
            dailyChanges = mutableMapOf()
        }
        Log.d("MainActivity", "Données chargées. Schedule: ${schedule.size} items, DailyChanges: ${dailyChanges.size} jours.")
    }

    private fun saveData() {
        val editor = sharedPreferences.edit()
        editor.putString("classSchedule", gson.toJson(schedule))
        editor.putString("dailyClassChanges", gson.toJson(dailyChanges))
        editor.apply()
        Log.d("MainActivity", "Données sauvegardées.")
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

        // Filtrer le cours invisible ici pour l'affichage du "prochain cours"
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
        } else {
            nextCourseText.text = "Aucun cours à venir."
            nextCourseTime.text = ""
            timeLeftText.text = ""
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

        // Filtrer le cours invisible du rappel quotidien pour qu'il ne s'affiche pas
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

        // Passer le nom du cours invisible à l'adaptateur
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
        val cancelTimeInput: EditText = dialogView.findViewById(R.id.cancelCourseInput)
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
        private val internalDailyReminderCourseName: String, // Nouveau paramètre
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
            // Filtrer le cours invisible du rappel quotidien pour qu'il ne s'affiche pas
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
                // L'application n'est pas ignorée, demander la permission
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
                requestBatteryOptimizationButton.setBackgroundColor(Color.parseColor("#2ECC71")) // Vert
            } else {
                requestBatteryOptimizationButton.text = "Optimisation Batterie: Requis ⚠️"
                requestBatteryOptimizationButton.setBackgroundColor(Color.parseColor("#E67E22")) // Orange
            }
        } else {
            requestBatteryOptimizationButton.text = "Optimisation Batterie: N/A"
            requestBatteryOptimizationButton.setBackgroundColor(Color.parseColor("#95A5A6")) // Gris
        }
    }
}
