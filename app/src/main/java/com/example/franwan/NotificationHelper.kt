package com.example.franwan

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import android.util.Log

// Constantes pour les notifications de rappel de cours
const val CLASS_REMINDER_CHANNEL_ID = "class_reminder_channel"
const val CLASS_REMINDER_CHANNEL_NAME = "Rappels de Cours"
const val CLASS_REMINDER_CHANNEL_DESCRIPTION = "Notifications pour vos prochains cours"
const val CLASS_REMINDER_NOTIFICATION_ID = 101 // ID unique pour la notification de rappel de cours

// Constantes pour la notification quotidienne de modification
const val DAILY_MOD_CHANNEL_ID = "daily_mod_channel"
const val DAILY_MOD_CHANNEL_NAME = "Modifications Quotidiennes"
const val DAILY_MOD_CHANNEL_DESCRIPTION = "Rappel pour les modifications d'emploi du temps du jour"
const val DAILY_MOD_NOTIFICATION_ID = 102 // ID unique pour la notification de modification quotidienne
const val EXTRA_OPEN_DAILY_CHANGES = "open_daily_changes_dialog" // Clé pour l'Intent

// Clés pour l'Intent du BroadcastReceiver (pour les rappels de cours)
const val EXTRA_COURSE_NAME = "extra_course_name"
const val EXTRA_COURSE_TIME = "extra_course_time"
const val EXTRA_COURSE_ROOM = "extra_course_room"
const val EXTRA_COURSE_DAY = "extra_course_day"

// CONSTANTE : Nom du cours invisible pour le rappel quotidien
const val INTERNAL_DAILY_REMINDER_COURSE_NAME = "INTERNAL_DAILY_REMINDER_COURSE"


object NotificationHelper {

    private val gson = Gson()
    val daysOfWeek = listOf(
        "Dimanche", "Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi"
    )

    // Fonction pour créer les canaux de notification (à appeler une seule fois, par exemple dans MainActivity.onCreate)
    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Canal pour les rappels de cours
            val classReminderChannel = NotificationChannel(CLASS_REMINDER_CHANNEL_ID, CLASS_REMINDER_CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
                description = CLASS_REMINDER_CHANNEL_DESCRIPTION
                enableLights(true)
                lightColor = Color.BLUE
                enableVibration(true)
            }

            // Canal pour la notification quotidienne de modification
            val dailyModChannel = NotificationChannel(DAILY_MOD_CHANNEL_ID, DAILY_MOD_CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = DAILY_MOD_CHANNEL_DESCRIPTION
                enableLights(true)
                lightColor = Color.GREEN
                enableVibration(true)
            }

            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(classReminderChannel)
            notificationManager.createNotificationChannel(dailyModChannel)
            Log.d("NotificationHelper", "Canaux de notification créés.")
        }
    }

    // Fonction pour afficher une notification (appelée par le BroadcastReceiver)
    fun showNotification(context: Context, notificationId: Int, channelId: String, title: String, message: String, courseName: String, roomName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                Log.w("NotificationHelper", "Permission POST_NOTIFICATIONS non accordée. Impossible d'afficher la notification.")
                return
            }
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            if (notificationId == DAILY_MOD_NOTIFICATION_ID) {
                putExtra(EXTRA_OPEN_DAILY_CHANGES, true)
            }
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context,
            notificationId, // Utiliser l'ID de notification comme request code pour l'Intent
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Remplacez par votre icône d'application si vous en avez une
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH) // Priorité élevée pour les rappels de cours, normale pour le daily mod
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))

        with(NotificationManagerCompat.from(context)) {
            notify(notificationId, builder.build())
            Log.d("NotificationHelper", "Notification (ID: $notificationId) affichée: '$title'")
        }
    }

    // Fonction principale pour planifier la prochaine notification (cours ou rappel quotidien)
    fun scheduleNextClassNotification(context: Context) {
        val sharedPreferences = context.getSharedPreferences("ClassSchedulerApp", Context.MODE_PRIVATE)
        val scheduleJson = sharedPreferences.getString("classSchedule", null)
        val dailyChangesJson = sharedPreferences.getString("dailyClassChanges", null)
        // Charger les jours de cours définis par l'utilisateur
        val courseDays = sharedPreferences.getStringSet("courseDays", null)?.toSet() ?: daysOfWeek.toSet() // Par défaut, tous les jours sont des jours de cours

        // NOUVEAU : Vérifier si la pause vacances est active
        val isVacationModeActive = sharedPreferences.getBoolean("isVacationModeActive", false)
        val vacationEndDateMillis = sharedPreferences.getLong("vacationEndDateMillis", 0L)
        val now = Calendar.getInstance()

        if (isVacationModeActive) {
            if (now.timeInMillis < vacationEndDateMillis) {
                // Pause vacances active et non expirée, annuler toutes les alarmes et ne rien planifier
                cancelExistingNotificationAlarm(context, CLASS_REMINDER_NOTIFICATION_ID)
                cancelExistingNotificationAlarm(context, DAILY_MOD_NOTIFICATION_ID)
                Log.d("NotificationHelper", "Pause vacances active jusqu'au ${SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(vacationEndDateMillis))}. Aucune notification planifiée.")
                return
            } else {
                // Pause vacances expirée, désactiver le mode et continuer la planification normale
                sharedPreferences.edit().putBoolean("isVacationModeActive", false).apply()
                Log.d("NotificationHelper", "Pause vacances expirée. Désactivation du mode vacances et reprise de la planification normale.")
            }
        }

        val schedule: MutableList<ClassItem> = if (scheduleJson != null) {
            gson.fromJson(scheduleJson, object : TypeToken<MutableList<ClassItem>>() {}.type)
        } else {
            mutableListOf()
        }

        val dailyChanges: MutableMap<String, MutableList<DailyChange>> = if (dailyChangesJson != null) {
            gson.fromJson(dailyChangesJson, object : TypeToken<MutableMap<String, MutableList<DailyChange>>>() {}.type)
        } else {
            mutableMapOf()
        }


        var upcomingEvents = mutableListOf<ClassItem>() // Renommé pour inclure les rappels quotidiens

        // 1. Ajouter les cours de l'emploi du temps principal et les changements quotidiens
        for (i in 0..7) { // Parcourir les 7 prochains jours
            val targetCalendar = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, i)
            }
            val targetDayIndex = targetCalendar.get(Calendar.DAY_OF_WEEK) - 1
            val targetDayName = daysOfWeek[targetDayIndex]

            // Filtrer les jours qui ne sont pas des jours de cours définis
            if (!courseDays.contains(targetDayName)) {
                Log.d("NotificationHelper", "Jour '$targetDayName' non défini comme jour de cours. Ignoré.")
                continue // Passer au jour suivant
            }

            var dailyScheduleForTargetDay = schedule.filter { it.day == targetDayName }.toMutableList()

            // Appliquer les changements quotidiens seulement pour le jour actuel (i == 0)
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

                if (classCalendar.timeInMillis > now.timeInMillis) { // Seulement les cours futurs
                    upcomingEvents.add(classItem.copy(dateTime = classCalendar.timeInMillis))
                }
            }
        }

        // 2. Ajouter l'alarme de rappel quotidien comme un "cours invisible"
        val dailyReminderHour = sharedPreferences.getInt("dailyReminderHour", -1) // -1 pour indiquer non défini
        val dailyReminderMinute = sharedPreferences.getInt("dailyReminderMinute", -1)

        if (dailyReminderHour != -1 && dailyReminderMinute != -1) {
            var dailyReminderCalendar = Calendar.getInstance()
            var foundNextCourseDay = false
            for (i in 0..7) { // Chercher le prochain jour de cours valide
                val tempCalendar = Calendar.getInstance().apply {
                    timeInMillis = now.timeInMillis
                    add(Calendar.DAY_OF_YEAR, i)
                    set(Calendar.HOUR_OF_DAY, dailyReminderHour)
                    set(Calendar.MINUTE, dailyReminderMinute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                val tempDayIndex = tempCalendar.get(Calendar.DAY_OF_WEEK) - 1
                val tempDayName = daysOfWeek[tempDayIndex]

                if (courseDays.contains(tempDayName)) {
                    // Si c'est le jour actuel et l'heure est passée, passer au jour de cours suivant
                    if (i == 0 && tempCalendar.timeInMillis <= now.timeInMillis) {
                        continue
                    }
                    dailyReminderCalendar = tempCalendar
                    foundNextCourseDay = true
                    break // Trouvé le prochain jour de cours valide
                }
            }

            if (foundNextCourseDay) {
                upcomingEvents.add(ClassItem(
                    day = daysOfWeek[dailyReminderCalendar.get(Calendar.DAY_OF_WEEK) - 1], // Jour réel du rappel
                    time = String.format("%02d:%02d", dailyReminderHour, dailyReminderMinute),
                    course = INTERNAL_DAILY_REMINDER_COURSE_NAME, // Nom spécial pour le reconnaître
                    room = "", // Pas de salle
                    dateTime = dailyReminderCalendar.timeInMillis
                ))
                Log.d("NotificationHelper", "Rappel quotidien interne ajouté à la liste des événements à venir pour: ${SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault()).format(Date(dailyReminderCalendar.timeInMillis))}")
            } else {
                Log.d("NotificationHelper", "Aucun jour de cours défini pour planifier le rappel quotidien.")
            }
        }

        upcomingEvents.sortBy { it.dateTime } // Trier tous les événements par date/heure

        val nextEventToNotify = upcomingEvents.firstOrNull() // Le premier est le plus proche

        // Annuler TOUTES les alarmes précédentes (cours et quotidien)
        cancelExistingNotificationAlarm(context, CLASS_REMINDER_NOTIFICATION_ID)
        cancelExistingNotificationAlarm(context, DAILY_MOD_NOTIFICATION_ID)


        if (nextEventToNotify != null) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            var triggerAtMillis = nextEventToNotify.dateTime
            var notificationId = CLASS_REMINDER_NOTIFICATION_ID
            var channelId = CLASS_REMINDER_CHANNEL_ID

            // Ajustement pour les rappels de cours (délai configurable)
            if (nextEventToNotify.course != INTERNAL_DAILY_REMINDER_COURSE_NAME) {
                val reminderDelayMinutes = context.getSharedPreferences("ClassSchedulerApp", Context.MODE_PRIVATE)
                    .getInt("reminderDelayMinutes", 5) // Valeur par défaut : 5 minutes
                if (reminderDelayMinutes > 0) {
                    triggerAtMillis -= TimeUnit.MINUTES.toMillis(reminderDelayMinutes.toLong())
                    Log.d("NotificationHelper", "Rappel de cours: déclenchement ajusté $reminderDelayMinutes minutes avant.")
                } else {
                    // Délai immédiat (0 minutes) - notification à l'heure exacte du cours
                    Log.d("NotificationHelper", "Rappel de cours: déclenchement immédiat (à l'heure du cours).")
                }
            } else {
                // C'est le rappel quotidien invisible
                notificationId = DAILY_MOD_NOTIFICATION_ID
                channelId = DAILY_MOD_CHANNEL_ID // Utiliser le canal dédié pour le daily mod
                Log.d("NotificationHelper", "Rappel quotidien invisible: déclenchement à l'heure exacte.")
            }

            // Si l'heure de notification est déjà passée ou est trop proche (moins de 10 secondes),
            // mais que l'événement lui-même est encore dans le futur, déclencher la notification immédiatement.
            val nowMillis = now.timeInMillis
            if (triggerAtMillis <= nowMillis) {
                if (nextEventToNotify.dateTime > nowMillis + TimeUnit.SECONDS.toMillis(5)) {
                    triggerAtMillis = nowMillis + TimeUnit.SECONDS.toMillis(5) // Déclencher dans 5 secondes
                    Log.d("NotificationHelper", "Notification pour '${nextEventToNotify.course}' ajustée à 5 secondes (manquée ou trop proche).")
                } else {
                    Log.d("NotificationHelper", "Événement '${nextEventToNotify.course}' déjà passé ou trop proche pour notification. Ignoré la planification.")
                    return // Ne pas planifier si l'événement est déjà passé ou sur le point de passer
                }
            }

            val intent = Intent(context, NotificationReceiver::class.java).apply {
                putExtra(EXTRA_COURSE_NAME, nextEventToNotify.course)
                putExtra(EXTRA_COURSE_TIME, nextEventToNotify.time)
                putExtra(EXTRA_COURSE_ROOM, nextEventToNotify.room)
                putExtra(EXTRA_COURSE_DAY, nextEventToNotify.day)
                // Si c'est la notification quotidienne, ajouter l'extra pour ouvrir le dialogue
                if (nextEventToNotify.course == INTERNAL_DAILY_REMINDER_COURSE_NAME) {
                    putExtra(EXTRA_OPEN_DAILY_CHANGES, true)
                }
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId, // Utiliser l'ID correct pour le PendingIntent
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
            Log.d("NotificationHelper", "Alarme planifiée pour '${nextEventToNotify.course}' (ID: $notificationId) à ${SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault()).format(Date(triggerAtMillis))}")
        } else {
            Log.d("NotificationHelper", "Aucun événement à venir pour la planification de notification. Annulation des alarmes existantes.")
            cancelExistingNotificationAlarm(context, CLASS_REMINDER_NOTIFICATION_ID)
            cancelExistingNotificationAlarm(context, DAILY_MOD_NOTIFICATION_ID)
        }
    }

    // Annuler une alarme de notification existante (maintenant avec un ID pour spécifier laquelle)
    fun cancelExistingNotificationAlarm(context: Context, notificationId: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, NotificationReceiver::class.java)

        if (notificationId == DAILY_MOD_NOTIFICATION_ID) {
            intent.putExtra(EXTRA_OPEN_DAILY_CHANGES, true)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pendingIntent?.let {
            alarmManager.cancel(it)
            it.cancel()
            Log.d("NotificationHelper", "Alarme de notification (ID: $notificationId) existante annulée.")
        } ?: Log.d("NotificationHelper", "Aucune alarme (ID: $notificationId) existante à annuler ou PendingIntent non trouvé.")
    }
}
