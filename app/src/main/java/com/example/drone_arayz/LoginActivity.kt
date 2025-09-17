package com.example.drone_arayz


import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class LoginActivity : AppCompatActivity() {

    private lateinit var etTcKimlik: EditText
    private lateinit var btnGirisYap: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        etTcKimlik = findViewById(R.id.etTcKimlik)
        btnGirisYap = findViewById(R.id.btnGirisYap)

        btnGirisYap.setOnClickListener {
            val tc = etTcKimlik.text.toString().trim()

            if (tc.length != 11) {
                Toast.makeText(this, "Lütfen geçerli bir TC Kimlik No girin", Toast.LENGTH_SHORT).show()
            } else {
                // gecerli bir tc sonrasi MainActivity'e geciliyor
                val intent = Intent(this, MainActivity::class.java)
                intent.putExtra("tcKimlik", tc)
                startActivity(intent)
                finish()
            }
        }
    }
}
