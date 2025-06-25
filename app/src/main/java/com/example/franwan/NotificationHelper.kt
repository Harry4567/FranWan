package com.example.franwan // Assurez-vous que ce package correspond à votre projet

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
import com.example.franwan.NotificationReceiver // Assurez-vous que cet import est correct

// Constantes pour les notifications
const val CHANNEL_ID = "class_reminder_channel"
const val CHANNEL_NAME = "Rappels de Cours"
const val CHANNEL_DESCRIPTION = "Notifications pour vos prochains cours"
const val NOTIFICATION_ID = 101

// Clés pour l'Intent du BroadcastReceiver
const val EXTRA_COURSE_NAME = "extra_course_name"
const val EXTRA_COURSE_TIME = "extra_course_time"
const val EXTRA_COURSE_ROOM = "extra_course_room"
const val EXTRA_COURSE_DAY = "extra_course_day"


object NotificationHelper {

    private val gson = Gson()
    private val daysOfWeek = listOf(
        "Dimanche", "Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi"
    )

    // Fonction pour créer le canal de notification (à appeler une seule fois, par exemple dans MainActivity.onCreate)
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESCRIPTION
                enableLights(true)
                lightColor = Color.BLUE
                enableVibration(true)
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    // Fonction pour afficher une notification (appelée par le BroadcastReceiver)
    fun showNotification(context: Context, title: String, message: String, courseName: String, roomName: String) {
        // Cette vérification est essentielle pour Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                // Permission non accordée, ne peut pas notifier.
                return
            }
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Remplacez par votre icône d'application si vous en avez une
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))

        with(NotificationManagerCompat.from(context)) {
            notify(NOTIFICATION_ID, builder.build())
        }
    }

    // Fonction principale pour planifier la prochaine notification
    fun scheduleNextClassNotification(context: Context) {
        val sharedPreferences = context.getSharedPreferences("ClassSchedulerApp", Context.MODE_PRIVATE)
        val scheduleJson = sharedPreferences.getString("classSchedule", null)
        val dailyChangesJson = sharedPreferences.getString("dailyClassChanges", null)

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

        val now = Calendar.getInstance()
        val currentDayIndex = now.get(Calendar.DAY_OF_WEEK) - 1

        var upcomingClasses = mutableListOf<ClassItem>()

        for (i in 0..7) { // Regarder aujourd'hui et les 6 prochains jours
            val targetCalendar = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, i)
            }
            val targetDayIndex = targetCalendar.get(Calendar.DAY_OF_WEEK) - 1
            val targetDayName = daysOfWeek[targetDayIndex]

            var dailyScheduleForTargetDay = schedule.filter { it.day == targetDayName }.toMutableList()

            if (i == 0) { // Uniquement pour aujourd'hui, appliquer les changements quotidiens
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

                if (classCalendar.timeInMillis <= now.timeInMillis && i == 0) {
                    // C'est un cours passé pour aujourd'hui, on l'ignore pour la planification future
                } else {
                    upcomingClasses.add(classItem.copy(dateTime = classCalendar.timeInMillis))
                }
            }
        }

        upcomingClasses.sortBy { it.dateTime }

        val nextClassToNotify = upcomingClasses.firstOrNull {
            val timeDiff = it.dateTime - now.timeInMillis
            timeDiff > TimeUnit.SECONDS.toMillis(10) && timeDiff <= TimeUnit.MINUTES.toMillis(5) // Cours dans 10s à 5min
        } ?: upcomingClasses.firstOrNull { // Si aucun dans les 5min, trouver le prochain tout court pour le planifier
            it.dateTime > now.timeInMillis
        }

        cancelExistingNotificationAlarm(context) // Annuler toute alarme précédente pour éviter les duplications

        if (nextClassToNotify != null) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            // Calculer l'heure de la notification (5 minutes avant le cours)
            val notificationTime = nextClassToNotify.dateTime - TimeUnit.MINUTES.toMillis(5)

            val triggerAtMillis = if (notificationTime <= now.timeInMillis) {
                // Si la notification aurait dû se déclencher il y a peu et que le cours est à venir, la déclencher bientôt
                if (nextClassToNotify.dateTime > now.timeInMillis && (nextClassToNotify.dateTime - now.timeInMillis) < TimeUnit.MINUTES.toMillis(10) ) {
                    now.timeInMillis + TimeUnit.SECONDS.toMillis(5) // Déclencher dans 5 secondes
                } else {
                    // Trop tard pour notifier ce cours ou cours très lointain
                    return
                }
            } else {
                notificationTime
            }

            val intent = Intent(context, NotificationReceiver::class.java).apply {
                putExtra(EXTRA_COURSE_NAME, nextClassToNotify.course)
                putExtra(EXTRA_COURSE_TIME, nextClassToNotify.time)
                putExtra(EXTRA_COURSE_ROOM, nextClassToNotify.room)
                putExtra(EXTRA_COURSE_DAY, nextClassToNotify.day)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                NOTIFICATION_ID,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        } else {
            cancelExistingNotificationAlarm(context)
        }
    }

    // Annuler une alarme de notification existante
    fun cancelExistingNotificationAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, NotificationReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pendingIntent?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
    }
}
