package com.example.franwan // Assurez-vous que ce package correspond à votre projet

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log // Importez Log pour le débogage

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Extraire les détails du cours de l'Intent
        val courseName = intent.getStringExtra(EXTRA_COURSE_NAME) ?: "Cours inconnu"
        val courseTime = intent.getStringExtra(EXTRA_COURSE_TIME) ?: "heure inconnue"
        val courseRoom = intent.getStringExtra(EXTRA_COURSE_ROOM) ?: "salle inconnue"
        val courseDay = intent.getStringExtra(EXTRA_COURSE_DAY) ?: "jour inconnu"

        Log.d("NotificationReceiver", "Réception de l'alarme pour: $courseName à $courseTime le $courseDay")

        // Afficher la notification
        NotificationHelper.showNotification(
            context,
            "Rappel de Cours : $courseName",
            "Votre cours de $courseName en salle $courseRoom commence bientôt ($courseTime) !",
            courseName,
            courseRoom
        )

        // Très important : replanifier la prochaine notification immédiatement après avoir envoyé celle-ci
        // Cela garantit que le système continue de planifier les rappels même lorsque l'application n'est pas ouverte.
        NotificationHelper.scheduleNextClassNotification(context)
    }
}
