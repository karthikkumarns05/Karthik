package com.callshare.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import androidx.core.app.NotificationCompat
import java.io.File

class CallRecordingService : Service() {

    companion object {

        const val ACTION_START_MONITORING =
            "com.callshare.app.START_MONITORING"

        const val ACTION_STOP_MONITORING =
            "com.callshare.app.STOP_MONITORING"

        private const val CHANNEL_ID =
            "shine_recording"

        private const val NOTIFICATION_ID =
            1001
    }

    private lateinit var telephonyManager:
            TelephonyManager

    private var phoneStateListener:
            PhoneStateListener? = null

    private var mediaRecorder:
            MediaRecorder? = null

    private var isRecording =
        false

    private var recordingFile:
            File? = null

    override fun onCreate() {

        super.onCreate()

        createNotificationChannel()

        startForeground(
            NOTIFICATION_ID,
            createNotification(
                "Shine is ready"
            )
        )

        telephonyManager =
            getSystemService(
                TELEPHONY_SERVICE
            ) as TelephonyManager

        phoneStateListener =
            object : PhoneStateListener() {

                override fun onCallStateChanged(
                    state: Int,
                    phoneNumber: String?
                ) {

                    super.onCallStateChanged(
                        state,
                        phoneNumber
                    )

                    when (state) {

                        TelephonyManager
                            .CALL_STATE_OFFHOOK -> {

                            startRecording()
                        }

                        TelephonyManager
                            .CALL_STATE_IDLE -> {

                            stopRecording()
                        }
                    }
                }
            }

        try {

            telephonyManager.listen(
                phoneStateListener,
                PhoneStateListener.LISTEN_CALL_STATE
            )

        } catch (
            _: SecurityException
        ) {

            updateNotification(
                "Phone permission required"
            )
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        when (
            intent?.action
        ) {

            ACTION_START_MONITORING -> {

                updateNotification(
                    "Waiting for a call..."
                )
            }

            ACTION_STOP_MONITORING -> {

                stopRecording()

                stopSelf()
            }
        }

        return START_STICKY
    }

    private fun startRecording() {

        if (isRecording) {
            return
        }

        val directory =
            getExternalFilesDir(
                "recordings"
            )

        if (directory == null) {

            updateNotification(
                "Recording storage unavailable"
            )

            return
        }

        if (!directory.exists()) {
            directory.mkdirs()
        }

        recordingFile =
            File(
                directory,
                "shine_${System.currentTimeMillis()}.m4a"
            )

        try {

            val recorder =
                if (
                    Build.VERSION.SDK_INT >= 31
                ) {

                    MediaRecorder(this)

                } else {

                    @Suppress("DEPRECATION")
                    MediaRecorder()
                }

            /*
             * MIC is intentionally used here.
             *
             * Android does not guarantee that a normal
             * third-party application can capture the
             * remote side of a cellular call.
             */
            recorder.setAudioSource(
                MediaRecorder.AudioSource.MIC
            )

            recorder.setOutputFormat(
                MediaRecorder.OutputFormat.MPEG_4
            )

            recorder.setAudioEncoder(
                MediaRecorder.AudioEncoder.AAC
            )

            recorder.setAudioEncodingBitRate(
                128000
            )

            recorder.setAudioSamplingRate(
                44100
            )

            recorder.setOutputFile(
                recordingFile!!.absolutePath
            )

            recorder.prepare()

            recorder.start()

            mediaRecorder =
                recorder

            isRecording = true

            updateNotification(
                "🔴 Recording call..."
            )

        } catch (
            _: Exception
        ) {

            try {
                mediaRecorder?.release()
            } catch (
                _: Exception
            ) {
            }

            mediaRecorder = null

            isRecording = false

            updateNotification(
                "Unable to record call"
            )
        }
    }

    private fun stopRecording() {

        if (!isRecording) {
            return
        }

        try {

            mediaRecorder?.stop()

        } catch (
            _: Exception
        ) {
        }

        try {

            mediaRecorder?.release()

        } catch (
            _: Exception
        ) {
        }

        mediaRecorder = null

        isRecording = false

        val finishedFile =
            recordingFile

        recordingFile = null

        if (
            finishedFile != null &&
            finishedFile.exists() &&
            finishedFile.length() > 0
        ) {

            updateNotification(
                "Recording saved"
            )

        } else {

            updateNotification(
                "No recording captured"
            )
        }
    }

    private fun createNotification(
        text: String
    ): Notification {

        return NotificationCompat.Builder(
            this,
            CHANNEL_ID
        )
            .setContentTitle(
                "Shine"
            )
            .setContentText(
                text
            )
            .setSmallIcon(
                android.R.drawable
                    .ic_btn_speak_now
            )
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(
        text: String
    ) {

        val manager =
            getSystemService(
                NOTIFICATION_SERVICE
            ) as NotificationManager

        manager.notify(
            NOTIFICATION_ID,
            createNotification(text)
        )
    }

    private fun createNotificationChannel() {

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O
        ) {

            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    "Shine Call Recording",
                    NotificationManager
                        .IMPORTANCE_LOW
                )

            val manager =
                getSystemService(
                    NOTIFICATION_SERVICE
                ) as NotificationManager

            manager.createNotificationChannel(
                channel
            )
        }
    }

    override fun onDestroy() {

        stopRecording()

        phoneStateListener?.let {

            try {

                telephonyManager.listen(
                    it,
                    PhoneStateListener.LISTEN_NONE
                )

            } catch (
                _: Exception
            ) {
            }
        }

        super.onDestroy()
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? = null
}
