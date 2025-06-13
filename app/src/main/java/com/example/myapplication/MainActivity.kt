package com.example.myapplication

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.StrictMode
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private lateinit var dbHelper: LocationDatabaseHelper
    private var locationUpdateCount = 0

    private val LOCATION_PERMISSION_REQUEST_CODE = 1000
    private val BACKGROUND_LOCATION_PERMISSION_REQUEST_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        dbHelper = LocationDatabaseHelper(this)

        setupLocationCallback()

        findViewById<Button>(R.id.toggleButton).setOnClickListener {
//            updateLocationUI(saveToDb = true)
        }

        findViewById<Button>(R.id.uploadButton).setOnClickListener {
            uploadLocationData()
        }

        if (checkForegroundPermission()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !checkBackgroundPermission()) {
                requestBackgroundLocationPermission()
            } else {
//                updateLocationUI()
                startLocationUpdates()
            }
        } else {
            requestForegroundLocationPermission()
        }
    }

    private fun setupLocationCallback() {
        locationRequest = LocationRequest.create().apply {
            interval = 5000L // 5 seconds
            fastestInterval = 3000L
            priority = Priority.PRIORITY_HIGH_ACCURACY
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                val gpsLocationText: TextView = findViewById(R.id.gpsLocationText)
                gpsLocationText.text = "Lat: ${location.latitude}, Lon: ${location.longitude}"

//                locationUpdateCount++
//                if (locationUpdateCount % 10 == 0) {
                    val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                    dbHelper.insertLocation(location.latitude, location.longitude, timestamp)
                    Toast.makeText(applicationContext, "Saved location #$locationUpdateCount", Toast.LENGTH_SHORT).show()
//                }
            }
        }
    }

    private fun startLocationUpdates() {
        if (checkForegroundPermission()) {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                mainLooper
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }


    private fun checkForegroundPermission() = ActivityCompat.checkSelfPermission(
        this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun checkBackgroundPermission() = ActivityCompat.checkSelfPermission(
        this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun requestForegroundLocationPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            LOCATION_PERMISSION_REQUEST_CODE
        )
    }

    private fun requestBackgroundLocationPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
            BACKGROUND_LOCATION_PERMISSION_REQUEST_CODE
        )
    }

//    private fun getLastLocation(onLocationReceived: (Double, Double) -> Unit) {
//        if (checkForegroundPermission()) {
//            fusedLocationClient.lastLocation
//                .addOnSuccessListener { location ->
//                    if (location != null) {
//                        onLocationReceived(location.latitude, location.longitude)
//                    } else {
//                        Toast.makeText(this, "Location is null", Toast.LENGTH_SHORT).show()
//                    }
//                }
//                .addOnFailureListener { e ->
//                    Toast.makeText(this, "Failed to get location: ${e.message}", Toast.LENGTH_SHORT).show()
//                }
//        } else {
//            Toast.makeText(this, "Location permission not granted", Toast.LENGTH_SHORT).show()
//        }
//    }

    private fun saveLocationToDatabase(latitude: Double, longitude: Double) {
        val db = dbHelper.writableDatabase
        val values = android.content.ContentValues().apply {
            put("latitude", latitude)
            put("longitude", longitude)
            put("timestamp", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
        }
        db.insert("location_data", null, values)
        Toast.makeText(this, "Location saved.", Toast.LENGTH_SHORT).show()
    }

    private fun uploadLocationData() {
        val db = dbHelper.readableDatabase

        val cursor = db.rawQuery("SELECT * FROM location_data", null)
        val jsonArray = JSONArray()

        while (cursor.moveToNext()) {
            val jsonObject = JSONObject().apply {
                put("id", cursor.getInt(cursor.getColumnIndexOrThrow("id")))
                put("latitude", cursor.getDouble(cursor.getColumnIndexOrThrow("latitude")))
                put("longitude", cursor.getDouble(cursor.getColumnIndexOrThrow("longitude")))
                put("timestamp", cursor.getString(cursor.getColumnIndexOrThrow("timestamp")))
            }
            jsonArray.put(jsonObject)
        }
        cursor.close()

        val urlString = "https://your-server.com/api/upload"
        StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().permitAll().build())

        try {
            val url = URL(urlString)
            (url.openConnection() as HttpURLConnection).run {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                doOutput = true

                OutputStreamWriter(outputStream).use { it.write(jsonArray.toString()) }

                val responseCode = responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    Toast.makeText(this@MainActivity, "Upload successful!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, "Upload failed: $responseCode", Toast.LENGTH_SHORT).show()
                }

                disconnect()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this,
                when(requestCode) {
                    LOCATION_PERMISSION_REQUEST_CODE -> "Foreground location permission denied"
                    BACKGROUND_LOCATION_PERMISSION_REQUEST_CODE -> "Background location permission denied"
                    else -> "Permission denied"
                }, Toast.LENGTH_SHORT).show()
            return
        }

        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !checkBackgroundPermission()) {
                    requestBackgroundLocationPermission()
                } else {
                    startLocationUpdates()
                }
            }
            BACKGROUND_LOCATION_PERMISSION_REQUEST_CODE -> {
                startLocationUpdates()
            }
        }
    }
}
