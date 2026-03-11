package com.example.divyanyanreader

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
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
            view.setPadding(view.paddingLeft, statusBars.top + 8, view.paddingRight, view.paddingBottom)
            insets
        }

        if (savedInstanceState == null) {
            openFragment(ScanFragment())
        }
        binding.btnDrawer.setOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }

        binding.navigationDrawer.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_language -> {
                    showLanguagePicker()
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                    true
                }

                R.id.nav_ocr_engine -> {
                    showOcrEnginePicker()
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                    true
                }

                R.id.nav_device_wifi -> {
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                    true
                }
                R.id.nav_logout -> {
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                    true
                }
                else -> false
            }
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
    private fun showOcrEnginePicker() {
        val options = arrayOf(getString(R.string.ocr_engine_ml_kit), getString(R.string.ocr_engine_tesseract))
        val current = ReaderPreferences.getOcrEngine(this)
        val selectedIndex = if (current == OcrEngine.TESSERACT) 1 else 0

        AlertDialog.Builder(this)
            .setTitle(R.string.select_ocr_engine)
            .setSingleChoiceItems(options, selectedIndex) { dialog, which ->
                ReaderPreferences.setOcrEngine(this, if (which == 1) OcrEngine.TESSERACT else OcrEngine.ML_KIT)
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showLanguagePicker() {
        val allLanguages = OcrLanguage.entries
        val names = allLanguages.map { it.displayName }.toTypedArray()
        val current = ReaderPreferences.getOcrLanguage(this)
        val selectedIndex = allLanguages.indexOf(current).coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle(R.string.select_ocr_language)
            .setSingleChoiceItems(names, selectedIndex) { dialog, which ->
                ReaderPreferences.setOcrLanguage(this, allLanguages[which])
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun openFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction().replace(R.id.fragmentContainer, fragment).commit()
    }
}