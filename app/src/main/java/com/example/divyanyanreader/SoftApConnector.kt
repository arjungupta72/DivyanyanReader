package com.example.divyanyanreader

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi

object SoftApConnector {
    private const val PI_SOFT_AP_SSID = "PiSetup_1234"
    private const val PI_SOFT_AP_PASSWORD = "raspberry123"

    @RequiresApi(Build.VERSION_CODES.Q)
    fun connectToPiSoftAP(context: Context) {
        val specifier = WifiNetworkSpecifier.Builder()
            .setSsid(PI_SOFT_AP_SSID)
            .setWpa2Passphrase(PI_SOFT_AP_PASSWORD)
            .build()

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(specifier)
            .build()

        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d("SoftAP", "Connected to Pi AP")
                connectivityManager.bindProcessToNetwork(network)
                val intent = Intent(context, WifiProvisionActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            }

            override fun onUnavailable() {
                Toast.makeText(context, "Could not connect to Pi hotspot", Toast.LENGTH_SHORT).show()
            }
        }

        connectivityManager.requestNetwork(request, callback)
    }
}
