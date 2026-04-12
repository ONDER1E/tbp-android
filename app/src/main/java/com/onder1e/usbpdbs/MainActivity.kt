package com.onder1e.usbpdbs

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {

    private val mainFragment = MainFragment()
    private val settingsFragment = SettingsFragment()
    private val permissionsFragment = PermissionsFragment()
    private lateinit var bottomNav: BottomNavigationView

    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { _, _ ->
        updateShizukuStatus()
        if (supportFragmentManager.findFragmentById(R.id.fragmentContainer) is PermissionsFragment) {
            permissionsFragment.onResume()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SettingsManager.init(this)
        setContentView(R.layout.activity_main)

        bottomNav = findViewById(R.id.bottomNav)
        
        // --- ADDED REDIRECT LOGIC ---
        if (!hasAllPermissions()) {
            loadFragment(permissionsFragment)
            bottomNav.selectedItemId = R.id.nav_permissions
        } else {
            loadFragment(mainFragment)
        }

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_main -> { loadFragment(mainFragment); true }
                R.id.nav_settings -> { loadFragment(settingsFragment); true }
                R.id.nav_permissions -> { loadFragment(permissionsFragment); true }
                else -> false
            }
        }

        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
        updateShizukuStatus()
        handleIntent(intent)
    }

    // Helper to determine if we need to force the permissions tab
    private fun hasAllPermissions(): Boolean {
        val secureSettings = checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED
        val termuxPerm = checkSelfPermission("com.termux.permission.RUN_COMMAND") == PackageManager.PERMISSION_GRANTED
        val overlay = Settings.canDrawOverlays(this)
        val notifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true
        val battery = (getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName)

        return secureSettings && termuxPerm && overlay && notifications && battery
    }

    override fun onNewIntent(intent: android.content.Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleIntent(it) }
    }

    private fun handleIntent(intent: android.content.Intent) {
        if (intent.getStringExtra("tab") == "permissions") {
            bottomNav.selectedItemId = R.id.nav_permissions
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    fun updateShizukuStatus() {
        try {
            val running = Shizuku.pingBinder()
            val granted = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            val status = when {
                !running -> "Shizuku: Not running"
                !granted -> "Shizuku: Permission not granted -- tap Start to request"
                else -> "Shizuku: Ready"
            }
            mainFragment.updateShizukuStatus(status)
        } catch (e: Exception) {
            mainFragment.updateShizukuStatus("Shizuku: Not available")
        }
    }

    fun requestShizukuIfNeeded() {
        try {
            if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(1001)
            }
        } catch (e: Exception) {}
    }

    override fun onResume() {
        super.onResume()
        updateShizukuStatus()
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
    }
}