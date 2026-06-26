package com.example.timerewind

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.MediaRecorder
import android.os.IBinder
import android.os.Build
import java.io.File

class BackgroundAudioService : Service() {
    private var recorder: MediaRecorder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, createNotification())

        val filename = "bg_audio_${System.currentTimeMillis()}.mp4"
        val filePath = File(getExternalFilesDir(null), filename).absolutePath

        recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setOutputFile(filePath)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            prepare()
            start()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        recorder?.apply {
            stop()
            release()
        }
    }

    private fun createNotification(): Notification {
        val channelId = "bg_record_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Audio Recording", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
                .setContentTitle("Recording in background")
                .setContentText("Time Rewind is listening...")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .build()
        } else {
            Notification.Builder(this)
                .setContentTitle("Recording in background")
                .setContentText("Time Rewind is listening...")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .build()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
} 