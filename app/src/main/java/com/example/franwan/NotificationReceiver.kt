package com.example.franwan

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Gérer le cas où l'appareil a redémarré
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("NotificationReceiver", "ACTION_BOOT_COMPLETED reçu. Re-planification de toutes les notifications.")
            NotificationHelper.scheduleNextClassNotification(context) // Ceci va replanifier le prochain cours et le rappel quotidien invisible
            return // Ne pas traiter comme une notification normale
        }

        val courseName = intent.getStringExtra(EXTRA_COURSE_NAME) ?: "Cours inconnu"
        val courseTime = intent.getStringExtra(EXTRA_COURSE_TIME) ?: "heure inconnue"
        val courseRoom = intent.getStringExtra(EXTRA_COURSE_ROOM) ?: "salle inconnue"
        val courseDay = intent.getStringExtra(EXTRA_COURSE_DAY) ?: "jour inconnu"
        val openDailyChanges = intent.getBooleanExtra(EXTRA_OPEN_DAILY_CHANGES, false) // Vérifier l'extra

        if (courseName == INTERNAL_DAILY_REMINDER_COURSE_NAME || openDailyChanges) { // Si c'est le cours invisible ou l'extra est présent
            Log.d("NotificationReceiver", "Réception de l'alarme pour la modification quotidienne (via cours invisible).")
            NotificationHelper.showNotification(
                context,
                DAILY_MOD_NOTIFICATION_ID, // Utiliser l'ID spécifique pour la notification quotidienne
                DAILY_MOD_CHANNEL_ID, // Utiliser le canal dédié pour le daily mod
                "Mises à jour de l'emploi du temps ?",
                "Cliquez ici pour vérifier et modifier votre emploi du temps du jour.",
                "", ""
            )
        } else {
            // C'est un rappel de cours normal
            Log.d("NotificationReceiver", "Réception de l'alarme pour rappel de cours: $courseName à $courseTime le $courseDay")
            NotificationHelper.showNotification(
                context,
                CLASS_REMINDER_NOTIFICATION_ID,
                CLASS_REMINDER_CHANNEL_ID,
                "Rappel de Cours : $courseName",
                "Votre cours de $courseName en salle $courseRoom commence bientôt ($courseTime) !",
                courseName,
                courseRoom
            )
        }

        // Toujours replanifier la prochaine notification après en avoir envoyé une
        // Cela gérera le prochain cours OU le prochain rappel quotidien invisible
        NotificationHelper.scheduleNextClassNotification(context)
    }
}
