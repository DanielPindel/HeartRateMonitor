package com.example.heartratemonitor.presentation

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.heartratemonitor.R
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class MainActivity : AppCompatActivity(), SensorEventListener
{
    private lateinit var heartRateTextView: TextView
    private lateinit var sensorManager: SensorManager
    private var heartRateSensor: Sensor? = null
    private var lastNonZeroHeartRate: Int = 0
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        heartRateTextView = findViewById(R.id.heartRateText)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)

        if (heartRateSensor == null)
        {
            heartRateTextView.text = "Heart Rate Sensor not available"
        }

        firestore = FirebaseFirestore.getInstance()

        startFirestoreUpdates()
        startPositionUpdates()
    }

    override fun onResume()
    {
        super.onResume()
        heartRateSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onPause()
    {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?)
    {
        if (event != null && event.sensor.type == Sensor.TYPE_HEART_RATE)
        {
            val heartRate = event.values[0].toInt()
            if (heartRate > 0)
            {
                lastNonZeroHeartRate = heartRate
                updateHeartRate(heartRate)
            }
            else
            {
                updateHeartRate(lastNonZeroHeartRate, true)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int)
    {
        // No implementation needed for heart rate accuracy changes
    }

    private fun updateHeartRate(heartRate: Int, showInRed: Boolean = false)
    {
        heartRateTextView.text = heartRate.toString()
        heartRateTextView.setTextColor(if (showInRed) getColor(android.R.color.holo_red_light) else getColor(android.R.color.white))
    }

    private fun startFirestoreUpdates()
    {
        val firebaseUpdateRunnable = object : Runnable
        {
            override fun run()
            {
                if (lastNonZeroHeartRate > 0)
                {
                    sendHeartRateToFirestore(lastNonZeroHeartRate)
                }
                handler.postDelayed(this, 10000) // 7 seconds
            }
        }
        handler.post(firebaseUpdateRunnable)
    }

    private fun sendHeartRateToFirestore(heartRate: Int)
    {
        val data = mapOf(
            "heartRate" to heartRate,
            "timestamp" to Timestamp.now()
        )

        firestore.collection("heartRates")
            .document("latestHeartRate")
            .set(data)
            .addOnSuccessListener {
                Log.d("Firestore", "Sent: $heartRate bpm")
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "Failed: ${e.message}", e)
            }
    }

    private fun startPositionUpdates() {
        val randomPositionRunnable = object : Runnable
        {
            override fun run()
            {
                moveTextView()
                handler.postDelayed(this, 10_000)
            }
        }
        handler.post(randomPositionRunnable)
    }

    private fun moveTextView()
    {
        val parentLayout = heartRateTextView.parent as? android.widget.RelativeLayout
        parentLayout?.let {
            //val diameter = it.width

            val diameter = 450.0
            val center = diameter / 2f
            val cutRadius = center * 0.8f

            val angle = Random.nextDouble(0.0, 2 * Math.PI)
            val distance = Random.nextDouble(0.0, cutRadius.toDouble())

            val randomX = (center + distance * cos(angle) - heartRateTextView.width / 2).toFloat()
            val randomY = (center + distance * sin(angle) - heartRateTextView.height / 2).toFloat()

            heartRateTextView.x = randomX
            heartRateTextView.y = randomY
        }
    }
}