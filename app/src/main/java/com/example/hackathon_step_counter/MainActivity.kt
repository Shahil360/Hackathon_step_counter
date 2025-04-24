package com.example.hackathon_step_counter

import android.app.*
import android.content.Context
import android.hardware.*
import android.os.*
import android.util.Log
import android.widget.*
import android.view.Gravity
import android.view.ViewGroup.LayoutParams
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import java.util.concurrent.TimeUnit

class MainActivity : Activity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometerSensor: Sensor? = null

    private var baselineSteps = 0
    private var dist = 0.0
    private lateinit var stepTextView: TextView
    private lateinit var distanceTextView: TextView
    private lateinit var timerTextView: TextView
    private lateinit var startButton: Button

    private var lastAccelX = 0.0f
    private var lastAccelY = 0.0f
    private var lastAccelZ = 0.0f
    private val threshold = 20.0f

    private var exerciseTimer: CountDownTimer? = null
    private var selectedMinutes = 1
    private var currentWorkRequest: WorkRequest? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        stepTextView = TextView(this).apply {
            text = "Steps: 0"
            textSize = 24f
            gravity = Gravity.CENTER
        }
        distanceTextView = TextView(this).apply {
            text = "Distance (in km): 0"
            textSize = 24f
            gravity = Gravity.CENTER
        }
        timerTextView = TextView(this).apply {
            text = "Time Remaining: 00:00"
            textSize = 24f
            gravity = Gravity.CENTER
        }

        startButton = Button(this).apply {
            text = "Select Time & Start"
            setOnClickListener {
                if (exerciseTimer == null) {
                    showTimePickerDialog()
                } else {
                    stopExerciseTimer()
                }
            }
        }

        val resetButton = Button(this).apply {
            text = "Reset Timer"
            setOnClickListener {
                resetExerciseTimer()
            }
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
            )
            addView(stepTextView)
            addView(distanceTextView)
            addView(timerTextView)
            addView(startButton)
            addView(resetButton)
        }

        setContentView(layout)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        if (accelerometerSensor == null) {
            stepTextView.text = "Accelerometer sensor not available"
            Log.e("STEP_DEBUG", "Accelerometer sensor not available")
        }
    }

    override fun onResume() {
        super.onResume()
        accelerometerSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val accelX = event.values[0]
            val accelY = event.values[1]
            val accelZ = event.values[2]

            val accelMagnitude = Math.sqrt((accelX * accelX + accelY * accelY + accelZ * accelZ).toDouble())

            if (accelMagnitude - lastAccelX > threshold) {
                baselineSteps++
                dist += baselineSteps * 0.00074
                stepTextView.text = "Steps: $baselineSteps"
                distanceTextView.text = "Distance (in km): %.2f".format(dist)
            }

            lastAccelX = accelX
            lastAccelY = accelY
            lastAccelZ = accelZ
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun showTimePickerDialog() {
        val numberPicker = NumberPicker(this).apply {
            minValue = 1
            maxValue = 60
            value = selectedMinutes
        }

        AlertDialog.Builder(this)
            .setTitle("Select Exercise Time (minutes)")
            .setView(numberPicker)
            .setPositiveButton("Start") { _, _ ->
                selectedMinutes = numberPicker.value
                startExerciseTimer(selectedMinutes)
                scheduleNotificationWorker(selectedMinutes)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startExerciseTimer(minutes: Int) {
        val duration = minutes * 60 * 1000L

        exerciseTimer = object : CountDownTimer(duration, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val min = (millisUntilFinished / 1000) / 60
                val sec = (millisUntilFinished / 1000) % 60
                timerTextView.text = "Time Remaining: %02d:%02d".format(min, sec)
            }

            override fun onFinish() {
                timerTextView.text = "Time's up!"
                startButton.text = "Select Time & Start"
                exerciseTimer = null
            }
        }.start()

        startButton.text = "Stop Timer"
    }

    private fun stopExerciseTimer() {
        exerciseTimer?.cancel()
        exerciseTimer = null
        cancelNotificationWorker()
        timerTextView.text = "Timer Stopped"
        startButton.text = "Select Time & Start"
    }

    private fun resetExerciseTimer() {
        exerciseTimer?.cancel()
        exerciseTimer = null
        cancelNotificationWorker()
        timerTextView.text = "Time Remaining: 00:00"
        startButton.text = "Select Time & Start"
        scheduleNotificationWorker(selectedMinutes)
        Toast.makeText(this, "Timer reset", Toast.LENGTH_SHORT).show()
    }

    private fun scheduleNotificationWorker(minutes: Int) {
        cancelNotificationWorker()

        val workRequest = OneTimeWorkRequestBuilder<NotifyWorker>()
            .setInitialDelay(minutes.toLong(), TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(this).enqueue(workRequest)
        currentWorkRequest = workRequest

        Toast.makeText(this, "Timer started", Toast.LENGTH_SHORT).show()
    }

    private fun cancelNotificationWorker() {
        currentWorkRequest?.let {
            WorkManager.getInstance(this).cancelWorkById(it.id)
        }
    }
}
