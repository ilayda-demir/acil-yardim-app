package com.example.drone_arayz

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.tasks.CancellationTokenSource
import java.util.Locale

class MapActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.uiSettings.isZoomControlsEnabled = true


        if (hasLocationPermission()) {
            mMap.isMyLocationEnabled = true
        }

        // 1) Harita ilk acildiginda kullanicinin anlik konumunu varsayilan olarak aliyor
        focusToFreshLocation()

        // 2) Haritada kullanici istedigi yere tiklayarak konumu degistirebiliyor
        mMap.setOnMapClickListener { latLng ->
            mMap.clear()
            mMap.addMarker(MarkerOptions().position(latLng).title("Seçilen Konum"))
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))

            Toast.makeText(this, "Konum seçimi yapıldı ✅", Toast.LENGTH_SHORT).show()

            val resultIntent = intent
            resultIntent.putExtra("lat", latLng.latitude)
            resultIntent.putExtra("lon", latLng.longitude) // alıcı tarafta lon okunuyor
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        }
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ActivityCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ActivityCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private fun focusToFreshLocation() {
        if (!hasLocationPermission()) {
            Toast.makeText(this, "Konum izni verilmedi", Toast.LENGTH_SHORT).show()
            return
        }


        val cts = CancellationTokenSource()
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
            .addOnSuccessListener { loc ->
                if (loc != null) {
                    moveCameraAndMark(loc)
                } else {

                    startOneShotUpdates()
                }
            }
            .addOnFailureListener {
                startOneShotUpdates()
            }
    }

    private fun startOneShotUpdates() {
        val req = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            1000L
        )
            .setMinUpdateIntervalMillis(500L)
            .setMaxUpdateDelayMillis(1500L)
            .setMinUpdateDistanceMeters(2f)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                fusedLocationClient.removeLocationUpdates(this)
                locationCallback = null
                moveCameraAndMark(loc)
            }
        }
        fusedLocationClient.requestLocationUpdates(req, locationCallback!!, Looper.getMainLooper())
    }

    private fun moveCameraAndMark(location: Location) {
        val current = LatLng(location.latitude, location.longitude)
        mMap.clear()
        mMap.addMarker(MarkerOptions().position(current).title("Mevcut Konumunuz"))
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(current, 16f))
    }

    override fun onStop() {
        super.onStop()
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
        locationCallback = null
    }
}
