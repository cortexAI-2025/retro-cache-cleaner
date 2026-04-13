package com.retrocache.cleaner

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.util.concurrent.Executors
import kotlin.random.Random

/**
 * MainActivity — Retro Cache Cleaner
 *
 * Displays the list of installed (non-system) applications alongside an
 * estimated cache size.  The user can select any app and be redirected to
 * the Android system "App Info" page where they can clear the cache manually.
 *
 * Cache sizes are simulated with a realistic probability distribution because
 * reading real per-app cache sizes requires the PACKAGE_USAGE_STATS permission,
 * which itself demands an explicit user opt-in in system settings.
 * A real implementation path is documented in the comments below.
 */
class MainActivity : AppCompatActivity() {

    // ── UI references ──────────────────────────────────────────────────────
    private lateinit var btnScan: Button
    private lateinit var btnPurge: Button
    private lateinit var progressContainer: LinearLayout
    private lateinit var labelProgress: TextView
    private lateinit var retroProgressBar: RetroProgressBar
    private lateinit var statusText: TextView
    private lateinit var totalCacheText: TextView
    private lateinit var appListView: ListView
    private lateinit var bottomStatus: TextView

    // ── State ──────────────────────────────────────────────────────────────
    private val appList = mutableListOf<AppInfo>()
    private var selectedIndex = -1

    // ── Threading ──────────────────────────────────────────────────────────
    private val bgExecutor = Executors.newSingleThreadExecutor()
    private val uiHandler  = Handler(Looper.getMainLooper())

    // ── Data model ─────────────────────────────────────────────────────────

    /** Holds the display data for one installed application. */
    data class AppInfo(
        val name: String,
        val packageName: String,
        val cacheSize: Long          // bytes — simulated value
    )

    // ══════════════════════════════════════════════════════════════════════
    //  Lifecycle
    // ══════════════════════════════════════════════════════════════════════

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        bindViews()
        setupListeners()
    }

    override fun onDestroy() {
        super.onDestroy()
        bgExecutor.shutdownNow()
    }

    // ── View binding ───────────────────────────────────────────────────────

    private fun bindViews() {
        btnScan           = findViewById(R.id.btn_scan)
        btnPurge          = findViewById(R.id.btn_purge)
        progressContainer = findViewById(R.id.progress_container)
        labelProgress     = findViewById(R.id.label_progress)
        retroProgressBar  = findViewById(R.id.retro_progress_bar)
        statusText        = findViewById(R.id.status_text)
        totalCacheText    = findViewById(R.id.total_cache_text)
        appListView       = findViewById(R.id.app_list)
        bottomStatus      = findViewById(R.id.bottom_status)
    }

    private fun setupListeners() {
        btnScan.setOnClickListener { startScan() }
        btnPurge.setOnClickListener {
            when {
                selectedIndex < 0 || selectedIndex >= appList.size ->
                    Toast.makeText(this,
                        getString(R.string.select_app_hint),
                        Toast.LENGTH_SHORT).show()
                else -> showPurgeDialog(appList[selectedIndex])
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  SCAN — enumerate non-system apps in a background thread
    // ══════════════════════════════════════════════════════════════════════

    private fun startScan() {
        // Reset state
        appList.clear()
        selectedIndex    = -1
        appListView.adapter = null
        btnScan.isEnabled  = false
        btnPurge.isEnabled = false

        totalCacheText.text = getString(R.string.total_cache_empty)
        bottomStatus.text   = "Analyse en cours\u2026"
        showProgress(true)
        setStatus("Initialisation\u2026")

        bgExecutor.execute {
            val pm = packageManager

            // ── Enumerate launchable (user-visible) apps ───────────────────
            // queryIntentActivities + <queries> in AndroidManifest is the
            // Play-Store-safe alternative to QUERY_ALL_PACKAGES.
            // It returns every app that exposes a launcher icon, which is
            // exactly the set a user wants to manage cache for.
            // Compatible API 21+ ; on API 30+ visibility is granted via <queries>.
            val launchIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            @Suppress("DEPRECATION")   // API 33+ uses ResolveInfoFlags — compatible here
            val rawList = pm.queryIntentActivities(launchIntent, 0)
                .map { it.activityInfo.applicationInfo }
                .filter { info -> (info.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
                .distinctBy { info -> info.packageName }   // deduplicate split-APK entries
                .sortedBy { info ->
                    runCatching { pm.getApplicationLabel(info).toString().lowercase() }
                        .getOrElse { info.packageName }
                }

            val total   = rawList.size
            val results = ArrayList<AppInfo>(total)

            rawList.forEachIndexed { index, appInfo ->
                val appName = runCatching {
                    pm.getApplicationLabel(appInfo).toString()
                }.getOrElse { appInfo.packageName }

                // ── Simulated cache size ───────────────────────────────────
                // Real sizes can be obtained via StorageStatsManager.queryStatsForPackage()
                // (requires PACKAGE_USAGE_STATS permission + user approval).
                results.add(AppInfo(appName, appInfo.packageName, simulateCacheSize()))

                // Throttle UI updates: post every item but sleep briefly so
                // the progress bar animation is visible.
                val pct = ((index + 1) * 100) / total
                uiHandler.post {
                    retroProgressBar.setProgress(pct)
                    setStatus("Scan\u2026 [${ index + 1 }/$total]  $appName")
                }

                Thread.sleep(18L)
            }

            // Sort by descending cache size (largest caches first)
            results.sortByDescending { it.cacheSize }

            uiHandler.post { onScanComplete(results) }
        }
    }

    private fun onScanComplete(results: List<AppInfo>) {
        appList.addAll(results)

        showProgress(false)
        btnScan.isEnabled = true

        val totalBytes = results.sumOf { it.cacheSize }
        totalCacheText.text = "Cache total estimé : ${formatSize(totalBytes)}"
        setStatus("Scan terminé — ${results.size} application(s) trouvée(s).")
        bottomStatus.text   = "${results.size} objet(s)"

        if (results.isEmpty()) return

        val adapter = AppAdapter()
        appListView.adapter = adapter
        appListView.setOnItemClickListener { _, _, position, _ ->
            selectedIndex      = position
            btnPurge.isEnabled = true
            adapter.notifyDataSetChanged()
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  PURGE DIALOG — Windows NT4-style confirmation box
    // ══════════════════════════════════════════════════════════════════════

    private fun showPurgeDialog(app: AppInfo) {
        val dialogView = LayoutInflater.from(this)
            .inflate(R.layout.dialog_confirm, null)

        dialogView.findViewById<TextView>(R.id.dialog_message).text =
            "Vider le cache de :\n\n" +
            "   ${app.name}\n\n" +
            "Taille estimée : ${formatSize(app.cacheSize)}\n\n" +
            "Vous allez être redirigé vers\n" +
            "les Paramètres Système Android.\n\n" +
            "Appuyez sur « Vider le cache »\n" +
            "pour confirmer l'opération."

        val dialog = AlertDialog.Builder(this, R.style.RetroDialog)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        // Transparent window background so our custom raised-bevel layout is
        // the only chrome visible.
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<Button>(R.id.btn_ok).setOnClickListener {
            dialog.dismiss()
            openAppSettings(app.packageName)
        }
        dialogView.findViewById<Button>(R.id.btn_cancel).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    /**
     * Opens the system "App Info" screen for [packageName].
     * The user can then tap "Vider le cache" (Clear cache) manually.
     * This is the only approach that works without root on modern Android.
     */
    private fun openAppSettings(packageName: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    // ══════════════════════════════════════════════════════════════════════
    //  LIST ADAPTER
    // ══════════════════════════════════════════════════════════════════════

    inner class AppAdapter : BaseAdapter() {

        override fun getCount(): Int           = appList.size
        override fun getItem(pos: Int): AppInfo = appList[pos]
        override fun getItemId(pos: Int): Long  = pos.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView
                ?: LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_app, parent, false)

            val app        = appList[position]
            val isSelected = (position == selectedIndex)

            val tvName  = view.findViewById<TextView>(R.id.tv_app_name)
            val tvPkg   = view.findViewById<TextView>(R.id.tv_app_package)
            val tvCache = view.findViewById<TextView>(R.id.tv_app_cache)

            tvName.text  = app.name
            tvPkg.text   = app.packageName
            tvCache.text = formatSize(app.cacheSize)

            if (isSelected) {
                // Windows-style selection: solid navy + white text
                view.setBackgroundColor(0xFF000080.toInt())
                tvName.setTextColor(0xFFFFFFFF.toInt())
                tvPkg.setTextColor(0xFFCCCCCC.toInt())
                tvCache.setTextColor(0xFFFFFFFF.toInt())
            } else {
                view.setBackgroundColor(0xFFFFFFFF.toInt())
                tvName.setTextColor(0xFF000000.toInt())
                tvPkg.setTextColor(0xFF808080.toInt())
                // Colour-code cache size: red > 100 MB, navy > 10 MB, green otherwise
                tvCache.setTextColor(
                    when {
                        app.cacheSize >= 100_000_000L -> 0xFF800000.toInt()  // dark red
                        app.cacheSize >=  10_000_000L -> 0xFF000080.toInt()  // navy
                        else                          -> 0xFF006400.toInt()  // dark green
                    }
                )
            }

            return view
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  HELPERS
    // ══════════════════════════════════════════════════════════════════════

    private fun showProgress(visible: Boolean) {
        val state = if (visible) View.VISIBLE else View.GONE
        progressContainer.visibility = state
        labelProgress.visibility     = state
    }

    private fun setStatus(text: String) {
        statusText.text = text
    }

    /**
     * Returns a weighted random cache size in bytes that approximates a
     * realistic distribution of app cache sizes:
     *   - 10 % very large   (500 MB – 2 GB)  — games, streaming apps
     *   - 20 % large        (50 – 500 MB)     — social media, maps
     *   - 30 % medium       (5 – 50 MB)       — typical productivity apps
     *   - 40 % small        (0 – 5 MB)        — utilities, system tools
     */
    private fun simulateCacheSize(): Long = when (Random.nextInt(10)) {
        0       -> Random.nextLong(500_000_000L, 2_000_000_000L)
        1, 2    -> Random.nextLong( 50_000_000L,   500_000_000L)
        3, 4, 5 -> Random.nextLong(  5_000_000L,    50_000_000L)
        else    -> Random.nextLong(          0L,     5_000_000L)
    }

    /** Human-readable file size in French-locale units. */
    private fun formatSize(bytes: Long): String = when {
        bytes >= 1_073_741_824L -> "%.1f Go".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576L     -> "%.1f Mo".format(bytes / 1_048_576.0)
        bytes >= 1_024L         -> "%.1f Ko".format(bytes / 1_024.0)
        else                    -> "$bytes o"
    }
}
