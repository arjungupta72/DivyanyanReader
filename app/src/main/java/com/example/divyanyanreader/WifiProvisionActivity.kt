package com.example.divyanyanreader

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket

@RequiresApi(Build.VERSION_CODES.Q)
class WifiProvisionActivity : AppCompatActivity() {

    private lateinit var etSsid: EditText
    private lateinit var etPass: EditText
    private lateinit var btnSubmit: Button

    private val requestLocationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Toast.makeText(this, "Location permission is required for Wi-Fi setup", Toast.LENGTH_LONG)
                    .show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wifi_provision)

        etSsid = findViewById(R.id.editSSID)
        etPass = findViewById(R.id.editPassword)
        btnSubmit = findViewById(R.id.btnSubmit)

        ensureLocationPermission()

        btnSubmit.setOnClickListener {
            val homeSsid = etSsid.text.toString().trim()
            val homePass = etPass.text.toString().trim()
            if (homeSsid.isEmpty() || homePass.isEmpty()) {
                Toast.makeText(this, "Enter both SSID & password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            provisionPi(homeSsid, homePass)
        }
    }

    private fun ensureLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestLocationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun provisionPi(ssid: String, pass: String) {
        Thread {
            try {
                Socket("192.168.4.1", 8888).use { socket ->
                    val out = PrintWriter(socket.getOutputStream(), true)
                    val json = "{\"ssid\":\"$ssid\",\"password\":\"$pass\"}"
                    out.println(json)

                    val response = BufferedReader(InputStreamReader(socket.getInputStream())).use { input ->
                        input.readLine()
                    }

                    if (response == "OK") {
                        runOnUiThread {
                            Toast.makeText(this, "Credentials accepted", Toast.LENGTH_SHORT).show()
                        }
                        Log.d("Provision", "Credentials accepted")
                        reconnectToHomeWifi(ssid, pass)
                    } else {
                        runOnUiThread {
                            Toast.makeText(this, "Provision failed", Toast.LENGTH_SHORT).show()
                        }
                        Log.d("Provision", "Provision failed")
                    }
                }
            } catch (e: IOException) {
                runOnUiThread {
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
                Log.e("Provision", "Error: ${e.message}")
            }
        }.start()
    }

    private fun reconnectToHomeWifi(ssid: String, pass: String) {
        val specifier = WifiNetworkSpecifier.Builder()
            .setSsid(ssid)
            .setWpa2Passphrase(pass)
            .build()

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .setNetworkSpecifier(specifier)
            .build()

        val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d("Provision", "Connected to Home WiFi")
                connectivityManager.bindProcessToNetwork(network)
                runOnUiThread {
                    Toast.makeText(this@WifiProvisionActivity, "Connected to Home WiFi!", Toast.LENGTH_SHORT)
                        .show()
                    startActivity(Intent(this@WifiProvisionActivity, MainActivity::class.java))
                    finish()
                }
            }

            override fun onUnavailable() {
                runOnUiThread {
                    Toast.makeText(
                        this@WifiProvisionActivity,
                        "Could not connect to Home WiFi",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        connectivityManager.requestNetwork(request, callback)
    }
}
