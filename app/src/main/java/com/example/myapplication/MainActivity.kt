package com.example.myapplication

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class MainActivity : ComponentActivity() {

    private val PERMISSION_REQUEST_CODE = 1000

    private lateinit var tvSyncInterval: TextView
    private lateinit var tvMeasurementInterval: TextView
    private lateinit var tvTestInterval: TextView

    private lateinit var ivPollingStatus: ImageView
    private lateinit var ivServerSyncStatus: ImageView
    private lateinit var ivCellMeasurementStatus: ImageView
    private lateinit var ivTestStatus: ImageView

    private lateinit var startButton: Button
    private lateinit var stopButton: Button

    private val uiUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ForegroundService.ACTION_UPDATE_UI) {
                val syncInterval = intent.getLongExtra("syncInterval", 0)
                val measurementInterval = intent.getLongExtra("measurementInterval", 0)
                val testInterval = intent.getLongExtra("testInterval", 0)

                val pollingSuccess = intent.getBooleanExtra("pollingSuccess", false)
                val serverSyncSuccess = intent.getBooleanExtra("serverSyncSuccess", false)
                val cellMeasurementSuccess = intent.getBooleanExtra("cellMeasurementSuccess", false)
                val testSuccess = intent.getBooleanExtra("testSuccess", false)

                tvSyncInterval.text = "$syncInterval ms"
                tvMeasurementInterval.text = "$measurementInterval ms"
                tvTestInterval.text = "$testInterval ms"

                // Update ImageViews based on boolean status
                updateStatusIcon(ivPollingStatus, pollingSuccess)
                updateStatusIcon(ivServerSyncStatus, serverSyncSuccess)
                updateStatusIcon(ivCellMeasurementStatus, cellMeasurementSuccess)
                updateStatusIcon(ivTestStatus, testSuccess)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize UI components
        tvSyncInterval = findViewById(R.id.tvSyncInterval)
        tvMeasurementInterval = findViewById(R.id.tvMeasurementInterval)
        tvTestInterval = findViewById(R.id.tvTestInterval)

        ivPollingStatus = findViewById(R.id.ivPollingStatus)
        ivServerSyncStatus = findViewById(R.id.ivServerSyncStatus)
        ivCellMeasurementStatus = findViewById(R.id.ivCellMeasurementStatus)
        ivTestStatus = findViewById(R.id.ivTestStatus)

        startButton = findViewById(R.id.btnStartService)
        stopButton = findViewById(R.id.btnStopService)

        // Start with the running state
        startButton.isEnabled = false
        stopButton.isEnabled = true

        // Request permissions and start service on app launch
        checkAndRequestPermissions()

        startButton.setOnClickListener {
            checkAndRequestPermissions()
        }

        stopButton.setOnClickListener {
            val stopIntent = Intent(this, ForegroundService::class.java).apply {
                action = ForegroundService.ACTION_STOP_SERVICE
            }
            startService(stopIntent)
            Toast.makeText(this, "Stopping service...", Toast.LENGTH_SHORT).show()

            // Update button states after stopping the service
            startButton.isEnabled = true
            stopButton.isEnabled = false
        }
    }

    override fun onStart() {
        super.onStart()
        LocalBroadcastManager.getInstance(this).registerReceiver(
            uiUpdateReceiver,
            IntentFilter(ForegroundService.ACTION_UPDATE_UI)
        )
    }

    override fun onStop() {
        super.onStop()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(uiUpdateReceiver)
    }

    private fun updateStatusIcon(imageView: ImageView, isSuccessful: Boolean) {
        if (isSuccessful) {
            imageView.setImageResource(R.drawable.ic_checkmark)
            imageView.setColorFilter(ContextCompat.getColor(this, R.color.green))
        } else {
            imageView.setImageResource(R.drawable.ic_close)
            imageView.setColorFilter(ContextCompat.getColor(this, R.color.red))
        }
    }

    private fun startLocationService() {
        if (!isGpsEnabled()) {
            Toast.makeText(this, "Please enable GPS to start monitoring.", Toast.LENGTH_LONG).show()
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
            return
        }

        val serviceIntent = Intent(this, ForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        Toast.makeText(this, "Location monitoring service started.", Toast.LENGTH_SHORT).show()

        // Update button states after successfully starting the service
        startButton.isEnabled = false
        stopButton.isEnabled = true
    }

    private fun isGpsEnabled(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }

    private fun hasRequiredPermissions(): Boolean {
        val fineLocationGranted = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val backgroundLocationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        val smsGranted = ActivityCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
        return fineLocationGranted && backgroundLocationGranted && smsGranted
    }

    private fun checkAndRequestPermissions() {
        if (hasRequiredPermissions()) {
            startLocationService()
        } else {
            val permissionsToRequest = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.SEND_SMS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                permissionsToRequest.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (hasRequiredPermissions()) {
                startLocationService()
            } else {
                Toast.makeText(this, "Required permissions denied. Exiting app.", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }
}