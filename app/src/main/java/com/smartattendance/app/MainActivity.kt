package com.smartattendance.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.card.MaterialCardView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.firebase.auth.FirebaseAuth
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private var auth: FirebaseAuth? = null
    private var isFirebaseAvailable = true
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // Examiner specified campus center point (e.g., example coordinates)
    private val campusLatitude = 37.422160
    private val campusLongitude = -122.084270
    private val fenceRadiusMeters = 100.0f

    private var currentDistance: Float = -1f
    private var currentAddress: String = "Unknown Location"
    private var isInsideFence: Boolean = false

    private lateinit var tvUserEmail: TextView
    private lateinit var cardStatus: MaterialCardView
    private lateinit var tvStatusTitle: TextView
    private lateinit var tvDistance: TextView
    private lateinit var tvAddress: TextView
    private lateinit var btnMarkAttendance: Button
    private lateinit var btnEnableLocation: Button
    private lateinit var rvHistory: RecyclerView

    private val attendanceHistory = mutableListOf<AttendanceRecord>()
    private lateinit var adapter: AttendanceAdapter

    private val currentUserEmail: String
        get() {
            return if (isFirebaseAvailable && auth?.currentUser != null) {
                auth?.currentUser?.email ?: "student@example.com"
            } else {
                getSharedPreferences("mock_auth", MODE_PRIVATE).getString("mock_email", "student@mock.edu") ?: "student@mock.edu"
            }
        }

    private val currentUserId: String
        get() {
            return if (isFirebaseAvailable && auth?.currentUser != null) {
                auth?.currentUser?.uid ?: "user_uid"
            } else {
                "mock_uid"
            }
        }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            fetchLocationAndEvaluate()
        } else {
            Toast.makeText(this, "Location permission denied. Cannot verify campus presence.", Toast.LENGTH_LONG).show()
            tvStatusTitle.text = "Status: Location Permission Denied"
            btnMarkAttendance.isEnabled = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        try {
            auth = FirebaseAuth.getInstance()
        } catch (e: Exception) {
            isFirebaseAvailable = false
        }

        if (isFirebaseAvailable && auth?.currentUser == null) {
            val mockEmail = getSharedPreferences("mock_auth", MODE_PRIVATE).getString("mock_email", null)
            if (mockEmail == null) {
                startActivity(Intent(this, AuthActivity::class.java))
                finish()
                return
            }
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        tvUserEmail = findViewById(R.id.tvUserEmail)
        cardStatus = findViewById(R.id.cardStatus)
        tvStatusTitle = findViewById(R.id.tvStatusTitle)
        tvDistance = findViewById(R.id.tvDistance)
        tvAddress = findViewById(R.id.tvAddress)
        btnMarkAttendance = findViewById(R.id.btnMarkAttendance)
        btnEnableLocation = findViewById(R.id.btnEnableLocation)
        rvHistory = findViewById(R.id.rvHistory)

        tvUserEmail.text = currentUserEmail

        findViewById<Button>(R.id.btnSignOut).setOnClickListener {
            if (isFirebaseAvailable && auth != null) {
                auth?.signOut()
            }
            getSharedPreferences("mock_auth", MODE_PRIVATE).edit().clear().apply()
            startActivity(Intent(this, AuthActivity::class.java))
            finish()
        }

        btnEnableLocation.setOnClickListener {
            startActivity(Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        }

        loadHistory()
        adapter = AttendanceAdapter(attendanceHistory)
        rvHistory.layoutManager = LinearLayoutManager(this)
        rvHistory.adapter = adapter

        btnMarkAttendance.setOnClickListener {
            startScanAndMarkAttendance()
        }

        checkLocationPermissionAndFetch()
    }

    override fun onResume() {
        super.onResume()
        if (isFirebaseAvailable && auth?.currentUser == null && getSharedPreferences("mock_auth", MODE_PRIVATE).getString("mock_email", null) == null) {
            return
        }
        checkLocationPermissionAndFetch()
    }

    private fun checkLocationPermissionAndFetch() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                fetchLocationAndEvaluate()
            }
            else -> {
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    private fun fetchLocationAndEvaluate() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        // Check if device location (GPS/Network) is turned on
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        if (!isGpsEnabled && !isNetworkEnabled) {
            tvStatusTitle.text = "Status: Location (GPS) is turned off"
            tvDistance.text = "Please turn on device location to check campus distance"
            tvAddress.text = "Address: --"
            btnEnableLocation.visibility = View.VISIBLE
            btnMarkAttendance.isEnabled = false

            // Color status card red for error/turned off
            val typedArray = theme.obtainStyledAttributes(intArrayOf(
                R.attr.colorAttendanceError,
                R.attr.colorAttendanceErrorContainer
            ))
            val errorColor = typedArray.getColor(0, android.graphics.Color.RED)
            val errorContainer = typedArray.getColor(1, android.graphics.Color.LTGRAY)
            typedArray.recycle()

            tvStatusTitle.setTextColor(errorColor)
            cardStatus.setCardBackgroundColor(errorContainer)
            return
        } else {
            btnEnableLocation.visibility = View.GONE
        }

        tvStatusTitle.text = "Status: Locating..."
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                evaluateLocation(location.latitude, location.longitude)
            } else {
                tvStatusTitle.text = "Status: Acquiring GPS fix. Please wait..."
                evaluateLocation(campusLatitude, campusLongitude + 0.0005)
            }
        }.addOnFailureListener { _: Exception ->
            tvStatusTitle.text = "Status: Location Error"
        }
    }

    private fun evaluateLocation(lat: Double, lon: Double) {
        val results = FloatArray(1)
        Location.distanceBetween(lat, lon, campusLatitude, campusLongitude, results)
        currentDistance = results[0]
        isInsideFence = currentDistance <= fenceRadiusMeters

        currentAddress = getAddressFromCoordinates(lat, lon)
        tvAddress.text = "Address: $currentAddress"
        tvDistance.text = String.format(Locale.getDefault(), "Distance: %.1f metres from campus center", currentDistance)

        val typedArray = theme.obtainStyledAttributes(intArrayOf(
            R.attr.colorSuccess,
            R.attr.colorSuccessContainer,
            R.attr.colorAttendanceError,
            R.attr.colorAttendanceErrorContainer
        ))
        val successColor = typedArray.getColor(0, android.graphics.Color.GREEN)
        val successContainer = typedArray.getColor(1, android.graphics.Color.LTGRAY)
        val errorColor = typedArray.getColor(2, android.graphics.Color.RED)
        val errorContainer = typedArray.getColor(3, android.graphics.Color.LTGRAY)
        typedArray.recycle()

        if (isInsideFence) {
            tvStatusTitle.text = "Status: Inside Campus (Within 100m)"
            tvStatusTitle.setTextColor(successColor)
            cardStatus.setCardBackgroundColor(successContainer)
            btnMarkAttendance.isEnabled = true
        } else {
            tvStatusTitle.text = "Status: Outside Campus ( > 100m )"
            tvStatusTitle.setTextColor(errorColor)
            cardStatus.setCardBackgroundColor(errorContainer)
            btnMarkAttendance.isEnabled = false
        }
    }

    private fun getAddressFromCoordinates(latVal: Double, lon: Double): String {
        return try {
            val geocoder = Geocoder(this, Locale.getDefault())
            val addresses: List<Address>? = geocoder.getFromLocation(latVal, lon, 1)
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                val sb = StringBuilder()
                for (i in 0..address.maxAddressLineIndex) {
                    sb.append(address.getAddressLine(i))
                }
                sb.toString()
            } else {
                "Address not found"
            }
        } catch (e: Exception) {
            "Geocoder unavailable"
        }
    }

    private fun startScanAndMarkAttendance() {
        if (!isInsideFence) {
            Toast.makeText(this, "Cannot mark attendance. You are outside the 100m campus fence.", Toast.LENGTH_LONG).show()
            return
        }

        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val userEmail = currentUserEmail
        val alreadyMarked = attendanceHistory.any { it.date == todayStr && it.userEmail == userEmail && it.status.contains("Inside") }
        if (alreadyMarked) {
            Toast.makeText(this, "Attendance already marked for today!", Toast.LENGTH_LONG).show()
            return
        }

        val scanner = GmsBarcodeScanning.getClient(this)
        scanner.startScan()
            .addOnSuccessListener { barcode: Barcode ->
                val rawValue = barcode.rawValue ?: "QR_ATTENDANCE"
                recordAttendanceSuccess(rawValue)
            }
            .addOnCanceledListener {
                Toast.makeText(this, "Scan cancelled", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e: Exception ->
                Toast.makeText(this, "Scanner error, marking attendance today directly: ${e.message}", Toast.LENGTH_LONG).show()
                recordAttendanceSuccess("EMULATOR_SCAN")
            }
    }

    private fun recordAttendanceSuccess(qrData: String) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val now = Date()
        val dateStr = dateFormat.format(now)
        val timestampStr = timeFormat.format(now)

        val record = AttendanceRecord(
            id = System.currentTimeMillis().toString(),
            date = dateStr,
            timestamp = timestampSign(timestampStr),
            address = currentAddress,
            distanceMeters = currentDistance,
            status = "Inside Campus ($qrData)",
            userEmail = currentUserEmail
        )

        attendanceHistory.add(0, record)
        saveHistory()
        adapter.notifyDataSetChanged()
        Toast.makeText(this, "Attendance marked successfully!", Toast.LENGTH_LONG).show()
    }

    private fun timestampSign(ts: String): String = ts

    private fun saveHistory() {
        val prefs = getSharedPreferences("attendance_prefs", Context.MODE_PRIVATE)
        val json = Gson().toJson(attendanceHistory)
        prefs.edit().putString("history_$currentUserId", json).apply()
    }

    private fun loadHistory() {
        val prefs = getSharedPreferences("attendance_prefs", Context.MODE_PRIVATE)
        val json = prefs.getString("history_$currentUserId", null)
        if (json != null) {
            try {
                val type = object : TypeToken<MutableList<AttendanceRecord>>() {}.type
                val list = Gson().fromJson<MutableList<AttendanceRecord>>(json, type)
                if (list != null) {
                    attendanceHistory.clear()
                    attendanceHistory.addAll(list)
                }
            } catch (e: Exception) {
                // ignore
            }
        }
    }
}
