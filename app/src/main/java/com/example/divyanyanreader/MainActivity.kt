package com.example.divyanyanreader

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import com.example.divyanyanreader.databinding.ActivityMainBinding
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.btnDrawer) { view, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.setPadding(
                view.paddingLeft,
                statusBars.top + 8,   // push below status bar
                view.paddingRight,
                view.paddingBottom
            )
            insets
        }

        if (savedInstanceState == null) {
            openFragment(ScanFragment())
        }
        binding.btnDrawer.setOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }

        binding.navigationDrawer.setNavigationItemSelectedListener { item ->
            val message = when (item.itemId) {
                R.id.nav_device_wifi -> "Device WiFi"
                R.id.nav_language -> "Language"
                R.id.nav_ocr_engine -> "OCR Engine"
                else -> null
            }

            message?.let {
                Toast.makeText(this, "$it selected", Toast.LENGTH_SHORT).show()
                binding.drawerLayout.closeDrawer(GravityCompat.START)
                true
            } ?: false
        }

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_scan -> {
                    openFragment(ScanFragment())
                    true
                }

                R.id.nav_receive -> {
                    openFragment(ReceiveFragment())
                    true
                }

                else -> false
            }
        }
    }

    private fun openFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}