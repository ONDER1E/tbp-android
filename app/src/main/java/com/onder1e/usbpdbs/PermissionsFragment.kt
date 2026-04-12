package com.onder1e.usbpdbs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import rikka.shizuku.Shizuku
import java.io.File

class PermissionsFragment : Fragment() {

    private lateinit var container: LinearLayout
    private val darkGreyText = 0xFF212121.toInt() // Matches main button text
    private val whiteText = 0xFFFFFFFF.toInt()
    private val disabledBg = 0xFF555555.toInt() // Dark grey background for "Granted"

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> refreshUI() }

    data class PermissionItem(
        val title: String,
        val isGranted: Boolean,
        val canBeGrantedViaRish: Boolean = false,
        val adbCommand: String = "",
        val action: () -> Unit
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        inflater.inflate(R.layout.fragment_permissions, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        container = view.findViewById(R.id.permissionContainer)
        refreshUI()
    }

    override fun onResume() {
        super.onResume()
        refreshUI()
    }

    private fun getThemeColor(attr: Int): Int {
        val typedValue = TypedValue()
        context?.theme?.resolveAttribute(attr, typedValue, true)
        return typedValue.data
    }

    private fun copyToClipboard(text: String) {
        val clipboard = context?.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("ADB Command", text)
        clipboard.setPrimaryClip(clip)
    }

    private fun refreshUI() {
        val ctx = context ?: return
        container.removeAllViews()
        val pkg = ctx.packageName
        
        val primaryPurple = getThemeColor(com.google.android.material.R.attr.colorPrimary)

        val shizukuInstalled = isPackageInstalled("moe.shizuku.privileged.api")
        val shizukuRunning = try { Shizuku.pingBinder() } catch (e: Exception) { false }
        val shizukuGranted = try { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED } catch (e: Exception) { false }
        val rishFile = File(ctx.filesDir, "rish")
        val rishReady = rishFile.exists() && File(ctx.filesDir, "rish_shizuku.dex").exists()

        // 1. Setup
        addSetupSection("Shizuku", if (shizukuGranted) "Active" else "Setup Required", shizukuGranted, primaryPurple) {
            if (!shizukuInstalled) startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=moe.shizuku.privileged.api")))
            else if (!shizukuRunning) ctx.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")?.let { startActivity(it) }
            else Shizuku.requestPermission(1001)
        }

        addSetupSection("Rish Bridge", if (rishReady) "Ready" else "Missing Assets", rishReady, primaryPurple) {
            try {
                listOf("rish", "rish_shizuku.dex").forEach { name ->
                    ctx.assets.open(name).use { input -> File(ctx.filesDir, name).outputStream().use { input.copyTo(it) } }
                }
                File(ctx.filesDir, "rish").setExecutable(true, false)
                refreshUI()
            } catch (e: Exception) { Toast.makeText(ctx, "Extraction failed", Toast.LENGTH_SHORT).show() }
        }

        // 2. Items
        val items = mutableListOf<PermissionItem>()

        val secureCmd = "adb shell pm grant $pkg android.permission.WRITE_SECURE_SETTINGS"
        items.add(PermissionItem("Write Secure Settings", 
            ctx.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED, 
            true, secureCmd) {
            if (rishReady && shizukuGranted) runRish("pm grant $pkg android.permission.WRITE_SECURE_SETTINGS")
            else { copyToClipboard(secureCmd); Toast.makeText(ctx, "Use ADB or setup rish, command copied", Toast.LENGTH_LONG).show() }
        })

        val termuxCmd = "adb shell pm grant $pkg com.termux.permission.RUN_COMMAND"
        items.add(PermissionItem("Termux Command", 
            ctx.checkSelfPermission("com.termux.permission.RUN_COMMAND") == PackageManager.PERMISSION_GRANTED, 
            true, termuxCmd) {
            if (rishReady && shizukuGranted) runRish("pm grant $pkg com.termux.permission.RUN_COMMAND")
            else { copyToClipboard(termuxCmd); Toast.makeText(ctx, "Use ADB or setup rish, command copied", Toast.LENGTH_LONG).show() }
        })

        items.add(PermissionItem("Display Over Other Apps", Settings.canDrawOverlays(ctx), true) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$pkg")))
        })

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            items.add(PermissionItem("Notifications", 
                ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED, true) {
                requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            })
        }

        val powerManager = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        items.add(PermissionItem("Battery Unrestricted", powerManager.isIgnoringBatteryOptimizations(pkg), false) {
            try {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$pkg")))
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        })

        val sortedItems = items.sortedBy { it.isGranted }

        // 3. Grant All Button
        if (rishReady && shizukuGranted && sortedItems.any { !it.isGranted && it.canBeGrantedViaRish }) {
            val btnAll = Button(ctx).apply {
                text = "Grant All via Shizuku"
                setTextColor(darkGreyText) // Matches Main UI
                backgroundTintList = ColorStateList.valueOf(primaryPurple)
                setOnClickListener {
                    val cmds = mutableListOf<String>()
                    if (ctx.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") != PackageManager.PERMISSION_GRANTED)
                        cmds.add("pm grant $pkg android.permission.WRITE_SECURE_SETTINGS")
                    if (ctx.checkSelfPermission("com.termux.permission.RUN_COMMAND") != PackageManager.PERMISSION_GRANTED)
                        cmds.add("pm grant $pkg com.termux.permission.RUN_COMMAND")
                    if (!Settings.canDrawOverlays(ctx))
                        cmds.add("cmd appops set $pkg SYSTEM_ALERT_WINDOW allow")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && 
                        ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                        cmds.add("pm grant $pkg android.permission.POST_NOTIFICATIONS")
                    
                    if (cmds.isNotEmpty()) runRish(cmds.joinToString(" && "))
                }
            }
            container.addView(btnAll)
            addSeparator()
        }

        // 4. Render Rows
        sortedItems.forEach { item ->
            val tv = TextView(ctx).apply {
                text = "${item.title}: ${if (item.isGranted) "Granted" else "Missing"}"
                setTextColor(if (item.isGranted) 0xFF00CC00.toInt() else 0xFFCC0000.toInt())
                setPadding(0, 20, 0, 5)
                typeface = Typeface.DEFAULT_BOLD
            }
            val btn = Button(ctx).apply {
                text = if (item.isGranted) "Granted" else "Fix ${item.title}"
                isEnabled = !item.isGranted
                // Logic: Dark text on Purple, White text on Grey
                setTextColor(if (item.isGranted) whiteText else darkGreyText)
                backgroundTintList = ColorStateList.valueOf(if (item.isGranted) disabledBg else primaryPurple)
                setOnClickListener { item.action() }
            }
            container.addView(tv)
            container.addView(btn)
        }
    }

    private fun addSetupSection(title: String, status: String, granted: Boolean, purple: Int, action: () -> Unit) {
        val ctx = context ?: return
        val tv = TextView(ctx).apply {
            text = "$title: $status"
            setTextColor(if (granted) 0xFF00CC00.toInt() else 0xFFCC0000.toInt())
            setPadding(0, 10, 0, 0)
        }
        val btn = Button(ctx).apply {
            text = if (granted) "$title Active" else "Setup $title"
            isEnabled = !granted
            setTextColor(if (granted) whiteText else darkGreyText)
            backgroundTintList = ColorStateList.valueOf(if (granted) disabledBg else purple)
            setOnClickListener { action() }
        }
        container.addView(tv)
        container.addView(btn)
    }

    private fun runRish(cmd: String) {
        try {
            val rish = File(requireContext().filesDir, "rish").absolutePath
            ProcessBuilder("sh", rish, "-c", cmd).start().waitFor()
            refreshUI()
        } catch (e: Exception) {
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addSeparator() {
        val v = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 3)
            setBackgroundColor(0x33000000.toInt())
        }
        (v.layoutParams as LinearLayout.LayoutParams).setMargins(0, 30, 0, 30)
        container.addView(v)
    }

    private fun isPackageInstalled(pkg: String) = try {
        requireContext().packageManager.getPackageInfo(pkg, 0); true
    } catch (e: Exception) { false }
}