package com.polar.polarsdkecghrdemo

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.androidplot.xy.XYPlot
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.model.PolarHrData
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable


class hasil : AppCompatActivity() {
    companion object {
        private const val TAG = "hasil"
    }

    private lateinit var api: PolarBleApi
    private lateinit var textViewAverage: TextView
    private lateinit var textViewResult: TextView
    private lateinit var plot: XYPlot
    private var hrDisposable: Disposable? = null
    private var averageHeartRate: Int = 0
    private lateinit var deviceId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.meditateresult)
        textViewAverage = findViewById(R.id.textViewAverage)
        textViewResult = findViewById(R.id.textViewResult)

        // Retrieve averageHR from intent extras
        averageHeartRate = intent.getIntExtra("averageHR", 0)

        // Update text views with averageHR
        updateTextViews(averageHeartRate)

        // ... other initialization code ...

        val homeButton: Button = findViewById(R.id.buttonHome)
        homeButton.setOnClickListener { onClickHome(it) }
    }


    // ... other methods ...

    fun updateTextViews(hrValue: Int) {
        // Update the HR TextView
        textViewAverage.text = hrValue.toString()

        // Check the heart rate and display a message
        when {
            hrValue < 70 -> {
                textViewResult.text = "Exercise a bit more"
            }
            hrValue > 120 -> {
                textViewResult.text = "You need to relax"
            }
            else -> {
                textViewResult.text = "You're doing great!"
            }
        }
    }
    private fun onClickHome(view: View) {
        // Create an Intent for MainActivity
        val intent = Intent(this, MainActivity::class.java)

        // Start MainActivity
        startActivity(intent)

        // Finish the current activity (hasil)
        finish()

    }
}
