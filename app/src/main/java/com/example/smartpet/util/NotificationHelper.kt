
package com.example.smartpet.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.smartpet.controller.MonitoringActivity

class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "smartpet_critical_alerts"
        const val CHANNEL_NAME = "Alertas de Seguridad SmartPet"

        const val NOTIFICATION_ID_SAFEZONE = 1001
        const val NOTIFICATION_ID_FOOD = 1002

        private const val TAG = "SmartPetNotifications"
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description =
                    "Alertas de escape de zona segura y niveles críticos de alimento"

                enableVibration(true)
                enableLights(true)
                setShowBadge(true)
            }

            val manager =
                context.getSystemService(Context.NOTIFICATION_SERVICE)
                        as NotificationManager

            manager.createNotificationChannel(channel)
        }
    }

    private fun hasNotificationPermission(): Boolean {

        return if (Build.VERSION.SDK_INT >= 33) {

            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

        } else {
            true
        }
    }

    private fun showPermissionMessage() {
        Toast.makeText(
            context,
            "Activa los permisos de notificaciones de SmartPet",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun createPendingIntent(requestCode: Int): PendingIntent {

        val intent = Intent(
            context,
            MonitoringActivity::class.java
        ).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun sendSafeZoneBreachAlert(petName: String) {

        Log.d(TAG, "Intentando enviar alerta de escape para $petName")

        if (!hasNotificationPermission()) {
            Log.e(TAG, "Permiso de notificaciones no concedido")
            showPermissionMessage()
            return
        }

        val notification = NotificationCompat.Builder(
            context,
            CHANNEL_ID
        )
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("¡ALERTA DE ESCAPE!")
            .setContentText("$petName está lejos de su zona segura.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(
                        "¡Atención! $petName ha salido del perímetro de seguridad. Revisa su ubicación inmediatamente."
                    )
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(createPendingIntent(1001))
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .build()

        try {

            NotificationManagerCompat.from(context).notify(
                NOTIFICATION_ID_SAFEZONE,
                notification
            )

            Log.d(TAG, "Notificación de escape enviada correctamente")

        } catch (error: SecurityException) {

            Log.e(TAG, "Permiso insuficiente para enviar alerta", error)

            showPermissionMessage()

        } catch (error: Exception) {

            Log.e(TAG, "Error al enviar alerta de escape", error)
        }
    }

    fun sendLowFoodAlert(
        petName: String,
        percent: Int
    ) {

        Log.d(TAG, "Intentando enviar alerta de alimento")

        if (!hasNotificationPermission()) {
            Log.e(TAG, "Permiso de notificaciones no concedido")
            showPermissionMessage()
            return
        }

        val notification = NotificationCompat.Builder(
            context,
            CHANNEL_ID
        )
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Nivel de alimento crítico")
            .setContentText(
                "El plato de $petName está al $percent%."
            )
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(
                        "El alimento de $petName está al $percent%. Es momento de rellenar el plato."
                    )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(createPendingIntent(1002))
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .build()

        try {

            NotificationManagerCompat.from(context).notify(
                NOTIFICATION_ID_FOOD,
                notification
            )

            Log.d(TAG, "Notificación de alimento enviada correctamente")

        } catch (error: SecurityException) {

            Log.e(TAG, "Permiso insuficiente para notificación de alimento", error)

            showPermissionMessage()

        } catch (error: Exception) {

            Log.e(TAG, "Error al enviar notificación de alimento", error)
        }
    }
}
