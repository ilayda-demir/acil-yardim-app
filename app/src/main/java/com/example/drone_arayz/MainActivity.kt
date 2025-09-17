package com.example.drone_arayz

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import java.util.Locale
import kotlin.math.min

class MainActivity : AppCompatActivity() {

    private lateinit var tvTcGoster: TextView
    private lateinit var tvKonum: TextView
    private lateinit var tvDurum: TextView
    private lateinit var btnDurumX: Button
    private lateinit var btnDurumEsittir: Button
    private lateinit var btnDurumUnlem: Button
    private lateinit var btnKonumAl: Button
    private lateinit var btnGonder: Button

    private var secilenDurum: String = "x"
    private var tcKimlik: String = ""
    private var lat: Double? = null
    private var lon: Double? = null
    private var lastAcc: Float? = null
    private var veriGonderildi = false

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var auth: FirebaseAuth
    private var samplingCallback: LocationCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Firebase Auth yapiliyor guvenlik icin
        auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            auth.signInAnonymously().addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Toast.makeText(this, "Anonim oturum açılamadı", Toast.LENGTH_SHORT).show()
                }
            }
        }

        tcKimlik = intent.getStringExtra("tcKimlik") ?: ""

        tvTcGoster = findViewById(R.id.tvTcGoster)
        tvKonum = findViewById(R.id.tvKonum)
        tvDurum = findViewById(R.id.tvDurum)
        btnDurumX = findViewById(R.id.btnDurumX)
        btnDurumEsittir = findViewById(R.id.btnDurumEsittir)
        btnDurumUnlem = findViewById(R.id.btnDurumUnlem)
        btnKonumAl = findViewById(R.id.btnKonumAl)
        btnGonder = findViewById(R.id.btnGonder)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        tvTcGoster.text = "TC: $tcKimlik"

        btnDurumX.setOnClickListener {
            secilenDurum = "x"
            tvDurum.text = "Durum: Gıda paketine ihtiyacım var."
        }
        btnDurumEsittir.setOnClickListener {
            secilenDurum = "="
            tvDurum.text = "Durum: İlk yardım paketine ihtiyacım var."
        }
        btnDurumUnlem.setOnClickListener {
            secilenDurum = "!"
            tvDurum.text = "Durum: Acil Müdahale ekiplerine ihtiyacım var."
        }

        btnKonumAl.setOnClickListener { sorKonumSecimi() }

        btnGonder.setOnClickListener {
            if (veriGonderildi) {
                uyariGoster()
            } else {
                veriyiKontrolEtVeGonder()
            }
        }
    }

    private fun sorKonumSecimi() {
        val secenekler = arrayOf("Anlık Konum", "Haritadan Seç")
        AlertDialog.Builder(this).apply {
            setTitle("Konum Seç")
            setItems(secenekler) { _, which ->
                if (which == 0) {
                    konumuAl() // çoklu örnekleyerek en iyi fix'i al
                } else {
                    val intent = Intent(this@MainActivity, MapActivity::class.java)
                    startActivityForResult(intent, 1001)
                }
            }
            show()
        }
    }

    /**  kisa sure konum toplayip en dusuk accuracy olanı seciyoruz ki en yakin noktayi bulabilelim */
    private fun konumuAl(
        desiredAccMeters: Float = 3f,  // hedef doğruluk (2–3 m önerilir)
        timeoutMs: Long = 12_000L,     // maksimum bekleme
        maxSamples: Int = 10           // en çok örnek
    ) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                1
            )
            return
        }


        val cts = com.google.android.gms.tasks.CancellationTokenSource()
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
            .addOnSuccessListener { first ->

                startSampling(desiredAccMeters, timeoutMs, maxSamples, first)
            }
            .addOnFailureListener {
                startSampling(desiredAccMeters, timeoutMs, maxSamples, null)
            }
    }

    private fun startSampling(
        desiredAccMeters: Float,
        timeoutMs: Long,
        maxSamples: Int,
        first: Location?
    ) {
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateIntervalMillis(500L)
            .setWaitForAccurateLocation(true)
            .setMaxUpdates(maxSamples) // güvenlik
            .build()

        var best: Location? = null
        var count = 0
        val t0 = System.currentTimeMillis()

        fun consider(loc: Location) {
            // kaba outlier filtresi
            if (loc.accuracy > 25f) return
            best = when {
                best == null -> loc
                loc.accuracy < best!!.accuracy -> loc
                else -> best
            }
        }

        first?.let { consider(it) }

        samplingCallback = object : LocationCallback() {
            override fun onLocationResult(res: LocationResult) {
                val loc = res.lastLocation ?: return
                count++
                consider(loc)

                val doneByAcc = (best?.accuracy ?: Float.MAX_VALUE) <= desiredAccMeters
                val doneByCount = count >= maxSamples
                val doneByTime = System.currentTimeMillis() - t0 >= timeoutMs

                if (doneByAcc || doneByCount || doneByTime) {
                    fusedLocationClient.removeLocationUpdates(this)
                    samplingCallback = null

                    if (best != null) {
                        lat = best!!.latitude
                        lon = best!!.longitude
                        lastAcc = best!!.accuracy
                        val latStr = String.format(Locale.US, "%.7f", lat)
                        val lonStr = String.format(Locale.US, "%.7f", lon)
                        val accStr = String.format(Locale.US, "%.1f", lastAcc)
                        tvKonum.text = "Konum: $latStr, $lonStr  (~$accStr m)"
                        Toast.makeText(
                            applicationContext,
                            "Konum hazır (≈$accStr m)",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(applicationContext, "Konum alınamadı", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(
            req, samplingCallback as LocationCallback, Looper.getMainLooper()
        )
    }

    private fun veriyiKontrolEtVeGonder() {
        if (lat != null && lon != null && tcKimlik.length == 11) {
            firebaseyeGonder()
        } else {
            Toast.makeText(this, "Lütfen önce konumu al ve TC'yi kontrol et", Toast.LENGTH_SHORT).show()
        }
    }

    private fun uyariGoster() {
        AlertDialog.Builder(this).apply {
            setMessage("Bir önceki mesajınız başarıyla gönderildi. Lütfen acil durum olmadığı sürece birden fazla istekte bulunmayın.")
            setPositiveButton("Acil Durum") { _, _ -> firebaseyeGonder() }
            setNegativeButton("Acil Durum Yok", null)
            show()
        }
    }

    private fun firebaseyeGonder() {
        val database = FirebaseDatabase.getInstance()
        val ref = database.getReference("mobil")

        val konumMap = mapOf(
            "lat" to lat,
            "lon" to lon,
            "accuracy" to (lastAcc ?: -1f),
            "timestamp" to ServerValue.TIMESTAMP
        )
        val dataMap = mapOf(
            "tc" to tcKimlik,
            "durum" to secilenDurum,
            "konum" to konumMap
        )

        ref.setValue(dataMap).addOnSuccessListener {
            veriGonderildi = true
            Toast.makeText(this, "Veriler başarıyla gönderildi", Toast.LENGTH_SHORT).show()
        }.addOnFailureListener {
            Toast.makeText(this, "Firebase gönderim hatası", Toast.LENGTH_SHORT).show()
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001 && resultCode == Activity.RESULT_OK && data != null) {
            lat = data.getDoubleExtra("lat", 0.0)
            lon = data.getDoubleExtra("lon", 0.0)
            lastAcc = null // haritadan seçimde accuracy bilinmez
            val latStr = String.format(Locale.US, "%.7f", lat)
            val lonStr = String.format(Locale.US, "%.7f", lon)
            tvKonum.text = "Seçilen Konum: $latStr, $lonStr"
        }
    }

    override fun onStop() {
        super.onStop()
        samplingCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
        samplingCallback = null
    }
}
