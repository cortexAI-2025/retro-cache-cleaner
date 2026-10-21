package com.retrocache.cleaner

import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.storage.StorageManager
import android.provider.Settings
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import java.util.concurrent.Executors

/**
 * Liste les applications installées avec leur cache et leurs données (valeurs réelles via
 * StorageStatsManager, nécessite l'accès « statistiques d'utilisation »).
 *
 * Android interdit d'effacer le cache/les données d'une autre application sans intervention :
 * les actions ouvrent donc l'écran « Infos de l'application » du système. Pour plusieurs
 * applications, une file guidée ouvre l'écran de la suivante à chaque retour dans l'app.
 */
class MainActivity : AppCompatActivity() {

    private enum class SortMode { CACHE, DATA, NAME }
    private enum class ClearAction { CACHE, DATA, BOTH }

    private lateinit var toolbar: MaterialToolbar
    private lateinit var progress: LinearProgressIndicator
    private lateinit var btnScan: MaterialButton
    private lateinit var permissionCard: View
    private lateinit var queueCard: View
    private lateinit var queueText: TextView
    private lateinit var selectionBar: View
    private lateinit var btnBulkCache: MaterialButton
    private lateinit var btnBulkData: MaterialButton
    private lateinit var totalCache: TextView
    private lateinit var totalData: TextView
    private lateinit var statusText: TextView
    private lateinit var adapter: AppAdapter
    private lateinit var backCallback: OnBackPressedCallback

    private val apps = mutableListOf<AppEntry>()
    private var sortMode = SortMode.CACHE
    private var scanning = false
    private var awaitingPermission = false
    private var pendingPackage: String? = null

    private var selectionMode = false

    private val queue = ArrayDeque<AppEntry>()
    private var queueActive = false
    private var queueAction = ClearAction.CACHE
    private var queueTotal = 0

    private val bgExecutor = Executors.newSingleThreadExecutor()
    private val uiHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        toolbar = findViewById(R.id.toolbar)
        progress = findViewById(R.id.progress)
        btnScan = findViewById(R.id.btn_scan)
        permissionCard = findViewById(R.id.permission_card)
        queueCard = findViewById(R.id.queue_card)
        queueText = findViewById(R.id.queue_text)
        selectionBar = findViewById(R.id.selection_bar)
        btnBulkCache = findViewById(R.id.btn_bulk_cache)
        btnBulkData = findViewById(R.id.btn_bulk_data)
        totalCache = findViewById(R.id.total_cache)
        totalData = findViewById(R.id.total_data)
        statusText = findViewById(R.id.status_text)

        adapter = AppAdapter(
            onClick = { entry -> if (selectionMode) toggleSelection(entry) else showActions(entry) },
            onLongClick = { entry ->
                if (!selectionMode) enterSelection()
                toggleSelection(entry)
            }
        )
        findViewById<RecyclerView>(R.id.app_list).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
        }

        toolbar.inflateMenu(R.menu.main_menu)
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_select -> { enterSelection(); true }
                R.id.action_select_all -> { selectAll(); true }
                R.id.action_close -> { finishAndRemoveTask(); true }
                else -> false
            }
        }
        toolbar.setNavigationOnClickListener { exitSelection() }

        backCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() = exitSelection()
        }
        onBackPressedDispatcher.addCallback(this, backCallback)

        btnScan.setOnClickListener { startScan() }
        btnBulkCache.setOnClickListener { confirmBulk(ClearAction.CACHE) }
        btnBulkData.setOnClickListener { confirmBulk(ClearAction.DATA) }
        findViewById<View>(R.id.queue_next).setOnClickListener { launchNext() }
        findViewById<View>(R.id.queue_stop).setOnClickListener { endQueue(completed = false) }
        findViewById<View>(R.id.btn_permission).setOnClickListener {
            awaitingPermission = true
            openUsageAccessSettings()
        }
        findViewById<View>(R.id.btn_app_info).setOnClickListener {
            try {
                startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", packageName, null)
                    }
                )
            } catch (e: Exception) {
                Toast.makeText(this, R.string.err_open_settings, Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<ChipGroup>(R.id.chip_group).setOnCheckedStateChangeListener { _, ids ->
            sortMode = when (ids.firstOrNull()) {
                R.id.chip_sort_data -> SortMode.DATA
                R.id.chip_sort_name -> SortMode.NAME
                else -> SortMode.CACHE
            }
            applySort()
        }

        totalData.text = getString(R.string.summary_data, getString(R.string.value_unknown))
        updatePermissionUi()
        updateSelectionUi()
        startScan()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionUi()
        if (awaitingPermission && hasUsageAccess()) {
            awaitingPermission = false
            startScan()
            return
        }
        pendingPackage?.let {
            pendingPackage = null
            refreshPackage(it)
        }
        if (queueActive) renderQueueCard()
    }

    override fun onDestroy() {
        super.onDestroy()
        bgExecutor.shutdownNow()
        uiHandler.removeCallbacksAndMessages(null)
    }

    // ── Analyse ──────────────────────────────────────────────────────────────

    @Suppress("DEPRECATION")   // queryIntentActivities(Intent, Int) : seule forme valable avant API 33
    private fun startScan() {
        if (scanning) return
        scanning = true
        btnScan.isEnabled = false
        progress.visibility = View.VISIBLE
        progress.setProgressCompat(0, false)
        statusText.text = getString(R.string.status_scanning_start)
        val withStats = hasUsageAccess()

        bgExecutor.execute {
            val pm = packageManager
            val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val infos = pm.queryIntentActivities(launcher, 0)
                .map { it.activityInfo.applicationInfo }
                .filter { isUserFacing(it) }
                .distinctBy { it.packageName }

            val total = infos.size
            val results = ArrayList<AppEntry>(total)
            var lastPct = -1
            infos.forEachIndexed { index, info ->
                val entry = AppEntry(
                    label = pm.getApplicationLabel(info).toString(),
                    packageName = info.packageName,
                    icon = runCatching { pm.getApplicationIcon(info) }.getOrNull()
                )
                if (withStats) {
                    queryStats(info.packageName)?.let { (cache, data) ->
                        entry.cacheBytes = cache
                        entry.dataBytes = data
                        entry.hasStats = true
                    }
                }
                results.add(entry)

                val pct = (index + 1) * 100 / total
                if (pct != lastPct) {
                    lastPct = pct
                    uiHandler.post {
                        progress.setProgressCompat(pct, true)
                        statusText.text = getString(R.string.status_scanning, index + 1, total)
                    }
                }
            }
            uiHandler.post { onScanComplete(results) }
        }
    }

    private fun onScanComplete(results: List<AppEntry>) {
        scanning = false
        btnScan.isEnabled = true
        progress.visibility = View.INVISIBLE
        apps.clear()
        apps.addAll(results)
        val present = apps.map { it.packageName }.toSet()
        adapter.selected.retainAll(present)
        applySort()
        updateSummary()
        updatePermissionUi()
        updateSelectionUi()
    }

    /** Applications avec icône de lancement, hors système (sauf système mises à jour, ex. Chrome). */
    private fun isUserFacing(info: ApplicationInfo): Boolean {
        if (info.packageName == packageName) return false
        val system = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        val updated = (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        return !system || updated
    }

    /** Retourne (cache, données hors cache) ; dataBytes d'Android inclut le cache. */
    private fun queryStats(pkg: String): Pair<Long, Long>? = try {
        val ssm = getSystemService(StorageStatsManager::class.java)
        val s = ssm.queryStatsForPackage(StorageManager.UUID_DEFAULT, pkg, Process.myUserHandle())
        Pair(s.cacheBytes, (s.dataBytes - s.cacheBytes).coerceAtLeast(0L))
    } catch (e: Exception) {
        null
    }

    private fun applySort() {
        val sorted = when (sortMode) {
            SortMode.CACHE -> apps.sortedWith(
                compareByDescending<AppEntry> { it.cacheBytes }.thenBy { it.label.lowercase() })
            SortMode.DATA -> apps.sortedWith(
                compareByDescending<AppEntry> { it.dataBytes }.thenBy { it.label.lowercase() })
            SortMode.NAME -> apps.sortedBy { it.label.lowercase() }
        }
        adapter.submit(sorted)
    }

    private fun updateSummary() {
        if (apps.any { it.hasStats }) {
            totalCache.text = Formatter.formatShortFileSize(this, apps.sumOf { it.cacheBytes })
            totalData.text = getString(
                R.string.summary_data,
                Formatter.formatShortFileSize(this, apps.sumOf { it.dataBytes })
            )
            statusText.text = getString(R.string.status_done, apps.size)
        } else {
            totalCache.text = getString(R.string.value_unknown)
            totalData.text = getString(R.string.summary_data, getString(R.string.value_unknown))
            statusText.text = getString(R.string.status_no_permission, apps.size)
        }
    }

    // ── Sélection multiple ───────────────────────────────────────────────────

    private fun enterSelection() {
        selectionMode = true
        updateSelectionUi()
    }

    private fun exitSelection() {
        selectionMode = false
        adapter.selected.clear()
        updateSelectionUi()
    }

    private fun toggleSelection(entry: AppEntry) {
        if (!adapter.selected.add(entry.packageName)) adapter.selected.remove(entry.packageName)
        updateSelectionUi()
    }

    private fun selectAll() {
        adapter.selected.addAll(adapter.items.map { it.packageName })
        updateSelectionUi()
    }

    private fun updateSelectionUi() {
        val menu = toolbar.menu
        menu.findItem(R.id.action_select)?.isVisible = !selectionMode
        menu.findItem(R.id.action_close)?.isVisible = !selectionMode
        menu.findItem(R.id.action_select_all)?.isVisible = selectionMode

        if (selectionMode) {
            toolbar.title = getString(R.string.selected_count, adapter.selected.size)
            toolbar.setNavigationIcon(R.drawable.ic_close)
            toolbar.setNavigationContentDescription(R.string.menu_close)
        } else {
            toolbar.title = getString(R.string.app_name)
            toolbar.navigationIcon = null
        }
        backCallback.isEnabled = selectionMode

        val chosen = apps.filter { it.packageName in adapter.selected }
        val showBar = selectionMode && chosen.isNotEmpty()
        selectionBar.visibility = if (showBar) View.VISIBLE else View.GONE
        if (showBar) {
            val known = chosen.any { it.hasStats }
            btnBulkCache.text = bulkLabel(R.string.bulk_cache, known, chosen.sumOf { it.cacheBytes })
            btnBulkData.text = bulkLabel(R.string.bulk_data, known, chosen.sumOf { it.dataBytes })
        }

        adapter.selectionMode = selectionMode
        adapter.refresh()
    }

    private fun bulkLabel(base: Int, known: Boolean, bytes: Long): String =
        if (known) getString(base) + "\n" + Formatter.formatShortFileSize(this, bytes) else getString(base)

    private fun confirmBulk(action: ClearAction) {
        val targets = adapter.items.filter { entry ->
            entry.packageName in adapter.selected && when {
                !entry.hasStats -> true
                action == ClearAction.CACHE -> entry.cacheBytes > 0
                else -> entry.dataBytes > 0
            }
        }
        if (targets.isEmpty()) {
            Toast.makeText(this, R.string.bulk_nothing, Toast.LENGTH_SHORT).show()
            return
        }
        val isData = action == ClearAction.DATA
        val builder = MaterialAlertDialogBuilder(this)
            .setTitle(getString(if (isData) R.string.bulk_confirm_data_title else R.string.bulk_confirm_cache_title, targets.size))
            .setMessage(if (isData) R.string.bulk_confirm_data_msg else R.string.bulk_confirm_cache_msg)
            .setPositiveButton(R.string.btn_start) { _, _ -> startQueue(targets, action) }
            .setNegativeButton(R.string.btn_cancel, null)
        if (isData) builder.setIcon(R.drawable.ic_warning)
        builder.show()
    }

    // ── File guidée (plusieurs applications) ─────────────────────────────────

    private fun startQueue(targets: List<AppEntry>, action: ClearAction) {
        exitSelection()
        queue.clear()
        queue.addAll(targets)
        queueAction = action
        queueTotal = targets.size
        queueActive = true
        launchNext()
    }

    private fun launchNext() {
        val next = queue.removeFirstOrNull()
        if (next == null) {
            endQueue(completed = true)
            return
        }
        queueCard.visibility = View.GONE
        if (!launchSettings(next, queueAction)) endQueue(completed = false)
    }

    /** Affiche, au retour des paramètres, l'application suivante de la file. */
    private fun renderQueueCard() {
        val next = queue.firstOrNull()
        if (next == null) {
            endQueue(completed = true)
            return
        }
        queueText.text = getString(R.string.queue_next, next.label, queueTotal - queue.size + 1, queueTotal)
        queueCard.visibility = View.VISIBLE
    }

    private fun endQueue(completed: Boolean) {
        val total = queueTotal
        queue.clear()
        queueActive = false
        queueCard.visibility = View.GONE
        val message = if (completed) getString(R.string.queue_done, total) else getString(R.string.queue_stopped)
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    // ── Actions sur une application ──────────────────────────────────────────

    private fun showActions(entry: AppEntry) {
        val dialog = BottomSheetDialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.sheet_actions, null)

        view.findViewById<ImageView>(R.id.sheet_icon).setImageDrawable(entry.icon)
        view.findViewById<TextView>(R.id.sheet_name).text = entry.label
        view.findViewById<TextView>(R.id.sheet_sub).text = entry.packageName

        fun withSize(base: Int, bytes: Long): String =
            if (entry.hasStats) getString(base) + " · " + Formatter.formatShortFileSize(this, bytes)
            else getString(base)

        view.findViewById<MaterialButton>(R.id.btn_clear_cache).apply {
            text = withSize(R.string.action_cache, entry.cacheBytes)
            setOnClickListener { dialog.dismiss(); launchSettings(entry, ClearAction.CACHE) }
        }
        view.findViewById<MaterialButton>(R.id.btn_clear_data).apply {
            text = withSize(R.string.action_data, entry.dataBytes)
            setOnClickListener {
                dialog.dismiss()
                confirmThenLaunch(entry, ClearAction.DATA)
            }
        }
        view.findViewById<MaterialButton>(R.id.btn_clear_both).apply {
            text = withSize(R.string.action_both, entry.cacheBytes + entry.dataBytes)
            setOnClickListener {
                dialog.dismiss()
                confirmThenLaunch(entry, ClearAction.BOTH)
            }
        }
        view.findViewById<MaterialButton>(R.id.btn_uninstall).setOnClickListener {
            dialog.dismiss()
            uninstall(entry)
        }

        dialog.setContentView(view)
        dialog.show()
    }

    /** La suppression des données est irréversible : confirmation avant de rediriger. */
    private fun confirmThenLaunch(entry: AppEntry, action: ClearAction) {
        val title = if (action == ClearAction.DATA) R.string.confirm_data_title else R.string.confirm_both_title
        val message = if (action == ClearAction.DATA) R.string.confirm_data_msg else R.string.confirm_both_msg
        MaterialAlertDialogBuilder(this)
            .setIcon(R.drawable.ic_warning)
            .setTitle(title)
            .setMessage(getString(message, entry.label))
            .setPositiveButton(R.string.btn_continue) { _, _ -> launchSettings(entry, action) }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    /** Ouvre « Infos de l'application » ; retourne false si l'écran n'a pas pu s'ouvrir. */
    private fun launchSettings(entry: AppEntry, action: ClearAction): Boolean {
        val guide = when (action) {
            ClearAction.CACHE -> R.string.guide_cache
            ClearAction.DATA -> R.string.guide_data
            ClearAction.BOTH -> R.string.guide_both
        }
        pendingPackage = entry.packageName
        return try {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", entry.packageName, null)
                }
            )
            Toast.makeText(this, guide, Toast.LENGTH_LONG).show()
            true
        } catch (e: Exception) {
            pendingPackage = null
            Toast.makeText(this, R.string.err_open_settings, Toast.LENGTH_SHORT).show()
            false
        }
    }

    private fun uninstall(entry: AppEntry) {
        pendingPackage = entry.packageName
        try {
            startActivity(Intent(Intent.ACTION_DELETE, Uri.fromParts("package", entry.packageName, null)))
        } catch (e: Exception) {
            pendingPackage = null
            Toast.makeText(this, R.string.err_open_settings, Toast.LENGTH_SHORT).show()
        }
    }

    /** Au retour des paramètres : met à jour les tailles de l'application, ou la retire si désinstallée. */
    @Suppress("DEPRECATION")   // getApplicationInfo(String, Int) : seule forme valable avant API 33
    private fun refreshPackage(pkg: String) {
        bgExecutor.execute {
            val installed = try {
                packageManager.getApplicationInfo(pkg, 0)
                true
            } catch (e: PackageManager.NameNotFoundException) {
                false
            }
            val stats = if (installed && hasUsageAccess()) queryStats(pkg) else null
            uiHandler.post {
                if (!installed) {
                    apps.removeAll { it.packageName == pkg }
                    adapter.selected.remove(pkg)
                    queue.removeAll { it.packageName == pkg }
                } else if (stats != null) {
                    apps.firstOrNull { it.packageName == pkg }?.let {
                        it.cacheBytes = stats.first
                        it.dataBytes = stats.second
                        it.hasStats = true
                    }
                }
                applySort()
                updateSummary()
                updateSelectionUi()
            }
        }
    }

    // ── Accès aux statistiques d'utilisation ─────────────────────────────────

    private fun hasUsageAccess(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun updatePermissionUi() {
        permissionCard.visibility = if (hasUsageAccess()) View.GONE else View.VISIBLE
    }

    private fun openUsageAccessSettings() {
        try {
            startActivity(
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
            )
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            } catch (e2: Exception) {
                Toast.makeText(this, R.string.err_open_settings, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ── Liste ────────────────────────────────────────────────────────────────

    private class AppAdapter(
        private val onClick: (AppEntry) -> Unit,
        private val onLongClick: (AppEntry) -> Unit
    ) : RecyclerView.Adapter<AppAdapter.VH>() {

        var items: List<AppEntry> = emptyList()
            private set
        val selected = mutableSetOf<String>()
        var selectionMode = false

        fun submit(list: List<AppEntry>) {
            items = list
            notifyDataSetChanged()
        }

        fun refresh() = notifyDataSetChanged()

        class VH(view: View) : RecyclerView.ViewHolder(view) {
            val card: MaterialCardView = view as MaterialCardView
            val icon: ImageView = view.findViewById(R.id.iv_icon)
            val name: TextView = view.findViewById(R.id.tv_name)
            val pkg: TextView = view.findViewById(R.id.tv_package)
            val pills: View = view.findViewById(R.id.pills)
            val cache: TextView = view.findViewById(R.id.tv_cache)
            val data: TextView = view.findViewById(R.id.tv_data)
            val check: ImageView = view.findViewById(R.id.iv_check)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false))

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val entry = items[position]
            val ctx = holder.itemView.context
            val isSelected = selectionMode && entry.packageName in selected

            holder.icon.setImageDrawable(entry.icon)
            holder.name.text = entry.label
            holder.pkg.text = entry.packageName
            holder.pills.visibility = if (entry.hasStats) View.VISIBLE else View.GONE
            if (entry.hasStats) {
                holder.cache.text = ctx.getString(
                    R.string.pill_cache, Formatter.formatShortFileSize(ctx, entry.cacheBytes))
                holder.data.text = ctx.getString(
                    R.string.pill_data, Formatter.formatShortFileSize(ctx, entry.dataBytes))
            }

            holder.card.setCardBackgroundColor(
                ContextCompat.getColor(ctx, if (isSelected) R.color.brand_primary_container else R.color.card_bg))
            holder.card.strokeColor = ContextCompat.getColor(ctx, R.color.brand_primary)
            holder.card.strokeWidth = if (isSelected) (2 * ctx.resources.displayMetrics.density).toInt() else 0
            holder.check.visibility = if (isSelected) View.VISIBLE else View.GONE

            holder.itemView.setOnClickListener { onClick(entry) }
            holder.itemView.setOnLongClickListener { onLongClick(entry); true }
        }
    }
}
