package com.polar.polarsdkecghrdemo

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.androidplot.xy.BoundaryMode
import com.androidplot.xy.StepMode
import com.androidplot.xy.XYGraphWidget
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import com.androidplot.xy.XYPlot
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleApiCallback
import com.polar.sdk.api.PolarBleApiDefaultImpl.defaultImplementation
import com.polar.sdk.api.errors.PolarInvalidArgument
import com.polar.sdk.api.model.PolarDeviceInfo
import com.polar.sdk.api.model.PolarHrData
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import java.text.DecimalFormat
import java.util.*
import java.util.concurrent.TimeUnit

class HRActivity : AppCompatActivity(), PlotterListener {
    companion object {
        private const val TAG = "HRActivity"
    }

    private lateinit var api: PolarBleApi
    private lateinit var textViewHR: TextView
    private lateinit var textViewRR: TextView
    private lateinit var textViewDeviceId: TextView
    private lateinit var textViewBattery: TextView
    private lateinit var textViewFwVersion: TextView
    private lateinit var plot: XYPlot
    private var hrDisposable: Disposable? = null
    private var totalHeartRate = 0
    private var heartRateCount = 0

    private lateinit var deviceId: String

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.meditateongoing)
        deviceId = intent.getStringExtra("id") ?: throw Exception("HRActivity couldn't be created, no deviceId given")
        textViewHR = findViewById(R.id.hr_view_hr)

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
                        streamHR()
                    }
                    else -> {}
                }
            }

            override fun disInformationReceived(identifier: String, uuid: UUID, value: String) {
                if (uuid == UUID.fromString("00002a28-0000-1000-8000-00805f9b34fb")) {
                    val msg = "Firmware: " + value.trim { it <= ' ' }
                    Log.d(TAG, "Firmware: " + identifier + " " + value.trim { it <= ' ' })
                    textViewFwVersion.append(msg.trimIndent())
                }
            }

            override fun batteryLevelReceived(identifier: String, level: Int) {
                Log.d(TAG, "Battery level $identifier $level%")
                val batteryLevelText = "Battery level: $level%"
                textViewBattery.append(batteryLevelText)
            }

            override fun hrNotificationReceived(identifier: String, data: PolarHrData.PolarHrSample) {
                //deprecated
            }

            override fun polarFtpFeatureReady(identifier: String) {
                //deprecated
            }

            override fun streamingFeaturesReady(identifier: String, features: Set<PolarBleApi.PolarDeviceDataType>) {
                //deprecated
            }

            override fun hrFeatureReady(identifier: String) {
                //deprecated
            }
        })

        try {
            api.connectToDevice(deviceId)
        } catch (a: PolarInvalidArgument) {
            a.printStackTrace()
        }

        val deviceIdText = "ID: $deviceId"
        textViewDeviceId.text = deviceIdText

    }

    public override fun onDestroy() {
        super.onDestroy()
        api.shutDown()
    }

    override fun update() {
        runOnUiThread { plot.redraw() }
    }

    fun streamHR() {
        val isDisposed = hrDisposable?.isDisposed ?: true
        if (isDisposed) {
            val durationInMilliseconds = 60000 // Set the desired duration in milliseconds (e.g., 1 minute)
            var elapsedTime = 0L // Track elapsed time

            hrDisposable = api.startHrStreaming(deviceId)
                    .observeOn(AndroidSchedulers.mainThread())
                    .takeWhile { elapsedTime < durationInMilliseconds }
                    .doOnTerminate {
                        // Code to execute when the stream terminates
                        Log.d(TAG, "HR stream terminated")

                        // Calculate the mean heart rate
                        val meanHeartRate = if (heartRateCount > 0) totalHeartRate / heartRateCount else 0

                        // Pass the meanHeartRate to the next activity
                        val intent = Intent(this@HRActivity, ndaktau::class.java)
                        intent.putExtra("meanHeartRate", meanHeartRate)
                        startActivity(intent)

                        // Finish the current activity
                        finish()

                        hrDisposable = null
                    }
                    .subscribe(
                            { hrData: PolarHrData ->
                                for (sample in hrData.samples) {
                                    Log.d(TAG, "HR ${sample.hr}")

                                    // Update only the TextView for HR
                                    textViewHR.text = sample.hr.toString()

                                    // Update sum and count for mean calculation
                                    totalHeartRate += sample.hr
                                    heartRateCount++
                                }
                            },
                            { error: Throwable ->
                                Log.e(TAG, "HR stream failed. Reason $error")
                                hrDisposable = null
                            },
                            {
                                Log.d(TAG, "HR stream complete")

                                // Stop the streaming after the specified duration
                                hrDisposable?.dispose()
                                hrDisposable = null
                            }
                    )

            // Start a timer to track elapsed time
            val timerDisposable = io.reactivex.rxjava3.core.Observable.interval(1, TimeUnit.SECONDS)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe {
                        elapsedTime += 1000 // Increment elapsed time every second
                    }
                        // Add the timerDisposable to hrDisposable so that it gets disposed along with hrDisposable
            hrDisposable = CompositeDisposable(timerDisposable)

        } else {
            // NOTE stops streaming if it is "running"
            hrDisposable?.dispose()
            hrDisposable = null
        }
    }

}