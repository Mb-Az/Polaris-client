package com.example.myapplication

import LocationDatabaseHelper
import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.*
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

    // Constants for permission request codes
    private val LOCATION_PERMISSION_REQUEST_CODE = 1000
    private val BACKGROUND_LOCATION_PERMISSION_REQUEST_CODE = 1001

    // Coroutine scope for managing background tasks
    private val activityScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize FusedLocationProviderClient and DatabaseHelper
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        dbHelper = LocationDatabaseHelper(this)

        // Setup the location callback
        setupLocationCallback()

        // Set up button click listeners
        // The original code had btnActivateMonitoring commented out for its updateLocationUI call.
        // I've removed the explicit call here, as location updates start automatically if permissions are granted.
        findViewById<Button>(R.id.btnActivateMonitoring).setOnClickListener {
            // If you want to perform an immediate action here, add it.
            // For now, the main functionality is covered by continuous updates.
            Toast.makeText(this, "Monitoring activated (continuous updates)", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnUpdateWithServer).setOnClickListener {
            uploadLocationData()
        }

        // Check and request permissions on app start
        checkAndRequestPermissions()
    }

    // Function to set up the location request and callback
    private fun setupLocationCallback() {
        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L) // Desired interval
            .setMinUpdateIntervalMillis(3000L) // Fastest interval
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return

                // Update UI with latest location
                val gpsLocationLatText: TextView = findViewById(R.id.tvLatitude)
                val gpsLocationLongText: TextView = findViewById(R.id.tvLongitude)
                gpsLocationLatText.text = "${location.latitude}"
                gpsLocationLongText.text = "${location.longitude}"

                // Save location to database every 10 updates (as per original commented logic)
//                locationUpdateCount++
//                if (locationUpdateCount % 10 == 0) {
                    val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                    // Perform database insert on a background thread
                    activityScope.launch(Dispatchers.IO) {
                        dbHelper.insertLocation(location.latitude, location.longitude, timestamp)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(applicationContext, "Saved location #$locationUpdateCount", Toast.LENGTH_SHORT).show()
                        }
                    }
//                }
            }
        }
    }

    // Function to start receiving location updates
    private fun startLocationUpdates() {
        // Ensure permissions are granted before requesting updates
        if (checkForegroundLocationPermission()) {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                mainLooper // Use the main Looper
            )
            Toast.makeText(this, "Location updates started.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Location permission not granted, cannot start updates.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Stop location updates to conserve battery and resources
        fusedLocationClient.removeLocationUpdates(locationCallback)
        // Cancel all coroutines in the scope when the activity is destroyed
        activityScope.cancel()
    }

    // Permission check functions
    private fun checkForegroundLocationPermission() = ActivityCompat.checkSelfPermission(
        this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun checkBackgroundLocationPermission() = ActivityCompat.checkSelfPermission(
        this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

    // Function to handle permission requests initially
    private fun checkAndRequestPermissions() {
        if (checkForegroundLocationPermission()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !checkBackgroundLocationPermission()) {
                requestBackgroundLocationPermission()
            } else {
                startLocationUpdates()
            }
        } else {
            requestForegroundLocationPermission()
        }
    }

    // Permission request functions
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

    // Function to upload location data to the server
    private fun uploadLocationData() {
        // Launch a coroutine on the IO dispatcher for network and database operations
        activityScope.launch(Dispatchers.IO) {
            val jsonArray = JSONArray()
            val db = dbHelper.readableDatabase
            var recordCount = 0 // To keep track of records for the toast message

            // Query database in a background thread
            val cursor = db.rawQuery("SELECT * FROM ${LocationDatabaseHelper.TABLE_NAME}", null)
            try {
                val idColumnIndex = cursor.getColumnIndexOrThrow(LocationDatabaseHelper.COLUMN_ID)
                val latitudeColumnIndex = cursor.getColumnIndexOrThrow(LocationDatabaseHelper.COLUMN_LATITUDE)
                val longitudeColumnIndex = cursor.getColumnIndexOrThrow(LocationDatabaseHelper.COLUMN_LONGITUDE)
                val timestampColumnIndex = cursor.getColumnIndexOrThrow(LocationDatabaseHelper.COLUMN_TIMESTAMP)

                while (cursor.moveToNext()) {
                    val jsonObject = JSONObject().apply {
                        put("id", cursor.getInt(idColumnIndex))
                        put("latitude", cursor.getDouble(latitudeColumnIndex))
                        put("longitude", cursor.getDouble(longitudeColumnIndex))
                        put("timestamp", cursor.getString(timestampColumnIndex))
                    }
                    jsonArray.put(jsonObject)
                    recordCount++
                }
            } finally {
                cursor.close()
                db.close() // Close the database after use
            }

            // If no data to upload, show a toast and exit
            if (recordCount == 0) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "No location data to upload.", Toast.LENGTH_SHORT).show()
                }
                return@launch // Exit the coroutine
            }

            val urlString = "https://your-server.com/api/upload" // IMPORTANT: Replace with your actual server URL!

            try {
                val url = URL(urlString)
                (url.openConnection() as HttpURLConnection).run {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    doOutput = true
                    connectTimeout = 10000 // 10 seconds
                    readTimeout = 10000 // 10 seconds

                    OutputStreamWriter(outputStream).use { it.write(jsonArray.toString()) }

                    val responseCode = responseCode
                    val responseMessage = responseMessage // Get response message for more detail

                    withContext(Dispatchers.Main) {
                        if (responseCode == HttpURLConnection.HTTP_OK) {
                            Toast.makeText(this@MainActivity, "Upload successful! Sent $recordCount records.", Toast.LENGTH_SHORT).show()
                            // Optional: Clear the database after successful upload
                            // dbHelper.writableDatabase.delete(LocationDatabaseHelper.TABLE_NAME, null, null)
                        } else {
                            Toast.makeText(this@MainActivity, "Upload failed: $responseCode - $responseMessage", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } catch (e: Exception) {
                // Catch any network or IO errors
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error during upload: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Callback for permission request results
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        // Check if any permission was granted
        val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED

        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (granted) {
                    // Foreground permission granted, now check/request background permission if needed
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !checkBackgroundLocationPermission()) {
                        requestBackgroundLocationPermission()
                    } else {
                        startLocationUpdates()
                    }
                } else {
                    Toast.makeText(this, "Foreground location permission denied. App functionality limited.", Toast.LENGTH_LONG).show()
                }
            }
            BACKGROUND_LOCATION_PERMISSION_REQUEST_CODE -> {
                if (granted) {
                    startLocationUpdates()
                } else {
                    Toast.makeText(this, "Background location permission denied. Location updates may stop when app is in background.", Toast.LENGTH_LONG).show()
                }
            }
            else -> {
                Toast.makeText(this, "Unknown permission request code.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
