package com.polar.polarsdkecghrdemo

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleApiCallback
import com.polar.sdk.api.PolarBleApiDefaultImpl.defaultImplementation
import com.polar.sdk.api.errors.PolarInvalidArgument
import com.polar.sdk.api.model.PolarDeviceInfo
import com.polar.sdk.api.model.PolarHrData
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.Disposable
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class HRActivity : AppCompatActivity(){
    companion object {
        private const val TAG = "HRActivity"
    }
    private var averageHeartRate: Int = 0
    private lateinit var api: PolarBleApi
    private lateinit var textViewHR: TextView
    private lateinit var textViewRR: TextView
    private var hrDisposable: Disposable? = null
    private var hrList: MutableList<Int> = mutableListOf()
    private var startTime: Long = 0
    private lateinit var buttonShowResults: Button


    private lateinit var deviceId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.meditateongoing)
        deviceId = intent.getStringExtra("id") ?: throw Exception("HRActivity couldn't be created, no deviceId given")
        textViewHR = findViewById(R.id.hr_view_hr)

        hrList = mutableListOf()
        startTime = System.currentTimeMillis()

        buttonShowResults = findViewById(R.id.button)
        buttonShowResults.setOnClickListener {
            // Handle button click
            navigateToResults()
        }

        api = defaultImplementation(
            applicationContext,
            setOf(
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING,
                PolarBleApi.PolarBleSdkFeature.FEATURE_BATTERY_INFO,
                PolarBleApi.PolarBleSdkFeature.FEATURE_DEVICE_INFO
            )
        )
        api.setApiLogger { str: String -> Log.d("SDK", str) }
        api.setApiCallback(object : PolarBleApiCallback() {
            override fun blePowerStateChanged(powered: Boolean) {
                Log.d(TAG, "BluetoothStateChanged $powered")
            }

            override fun deviceConnected(polarDeviceInfo: PolarDeviceInfo) {
                Log.d(TAG, "Device connected ${polarDeviceInfo.deviceId}")
                Toast.makeText(applicationContext, R.string.connected, Toast.LENGTH_SHORT).show()
            }

            override fun deviceConnecting(polarDeviceInfo: PolarDeviceInfo) {
                Log.d(TAG, "Device connecting ${polarDeviceInfo.deviceId}")
            }

            override fun deviceDisconnected(polarDeviceInfo: PolarDeviceInfo) {
                Log.d(TAG, "Device disconnected ${polarDeviceInfo.deviceId}")
            }

            override fun bleSdkFeatureReady(identifier: String, feature: PolarBleApi.PolarBleSdkFeature) {
                Log.d(TAG, "feature ready $feature")

                when (feature) {
                    PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING -> {
                        startDummyDataStream()
                    }
                    else -> {}
                }
            }

        })

        try {
            api.connectToDevice(deviceId)
        } catch (a: PolarInvalidArgument) {
            a.printStackTrace()
        }
    }

    public override fun onDestroy() {
        super.onDestroy()
        api.shutDown()
    }

    private fun streamHR() {
        val isDisposed = hrDisposable?.isDisposed ?: true
        if (isDisposed) {
            hrDisposable = api.startHrStreaming(deviceId)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { hrData: PolarHrData ->
                        for (sample in hrData.samples) {
                            Log.d(TAG, "HR ${sample.hr} RR ${sample.rrsMs}")

                            if (sample.rrsMs.isNotEmpty()) {
                                val rrText = "(${sample.rrsMs.joinToString(separator = "ms, ")}ms)"
                                // Update textViewRR.text with RR interval data if needed
                            }

                            // Update hr_view_hr with heart rate (BPM) value
                            val bpm = sample.hr
                            val bpmText = "${sample.hr} BPM"
                            findViewById<TextView>(R.id.hr_view_hr).text = bpmText

                            // Record heart rate value
                            hrList.add(sample.hr)

                            // Add live recommendations based on heart rate value
                            handleHeartRateRecommendations(bpm)
                        }
                    },
                    { error: Throwable ->
                        Log.e(TAG, "HR stream failed. Reason $error")
                        hrDisposable = null
                    },
                    {
                        Log.d(TAG, "HR stream complete")
                        // Calculate the average heart rate
                        if (hrList.isNotEmpty()) {
                            averageHeartRate = hrList.average().toInt()
                            Log.d(TAG, "Average HR: $averageHeartRate BPM")

                            // Perform any additional actions with the average heart rate
                        }

                        // Clear the list for the next session
                        hrList.clear()

                        // NOTE: You might want to handle the completion logic here
                    }
                )
        } else {
            // NOTE stops streaming if it is "running"
            hrDisposable?.dispose()
            hrDisposable = null
        }
    }

    private fun navigateToResults() {
        // Calculate the average heart rate
        if (hrList.isNotEmpty()) {
            averageHeartRate = hrList.average().toInt()
            Log.d(TAG, "Average HR: $averageHeartRate BPM")
        }

        // Create an Intent to start the ResultsActivity
        val intent = Intent(this@HRActivity, hasil::class.java)

        // Pass the average heart rate to ResultsActivity
        intent.putExtra("averageHR", averageHeartRate)

        // Start the ResultsActivity
        startActivity(intent)

        // Finish the current activity
        finish()
    }


    // Implement this method to calculate average heart rate
    private fun calculateAverageHR(): Int {
        // Use hrList or any other data to calculate the average heart rate
        return if (hrList.isNotEmpty()) {
            hrList.average().toInt()
        } else {
            0
        }
    }

    private fun handleHeartRateRecommendations(heartRate: Int) {
        when {
            heartRate > 120 -> {
                // Display recommendation for high heart rate (e.g., "Relax more")
                displayRecommendation("Relax more for a better meditation experience.")
            }
            heartRate in 70..120 -> {
                // Display recommendation for normal heart rate (e.g., "Maintain steady breathing")
                displayRecommendation("Maintain steady breathing for optimal meditation.")
            }
            heartRate < 70 -> {
                // Display recommendation for low heart rate (e.g., "Consider light exercises")
                displayRecommendation("Consider light exercises to increase your heart rate.")
            }
            // Add more conditions as needed
        }
    }

    private fun displayRecommendation(recommendation: String) {
        // Update a TextView or any other UI element with the live recommendation
        findViewById<TextView>(R.id.textView4).text = recommendation
    }

    // Function to generate dummy heart rate data for testing
    private fun generateDummyHeartRate(): Int {
        return Random.nextInt(60, 130) // Generate random heart rate between 60 and 130
    }

    // Modify your streamHR function to use dummy data
    private fun streamHRWithDummyData() {
        hrDisposable = Observable.interval(1000, TimeUnit.MILLISECONDS) // Simulate data every second
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    val dummyHeartRate = generateDummyHeartRate()
                    Log.d(TAG, "Dummy HR $dummyHeartRate")

                    // Update hr_view_hr with dummy heart rate (BPM) value
                    val bpmText = "$dummyHeartRate BPM"
                    findViewById<TextView>(R.id.hr_view_hr).text = bpmText

                    // Record dummy heart rate value
                    hrList.add(dummyHeartRate)

                    // Add live recommendations based on dummy heart rate value
                    handleHeartRateRecommendations(dummyHeartRate)
                }
    }

    // Call this function in your onCreate or wherever appropriate
    private fun startDummyDataStream() {
        streamHRWithDummyData()
    }

    // Don't forget to handle disposal of disposable when needed (e.g., in onDestroy)
    //Added Comments

}
