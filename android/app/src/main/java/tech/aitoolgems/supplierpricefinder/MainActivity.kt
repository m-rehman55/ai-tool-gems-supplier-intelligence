package tech.aitoolgems.supplierpricefinder

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch
import tech.aitoolgems.supplierpricefinder.data.local.LocalDatabase
import tech.aitoolgems.supplierpricefinder.data.local.SecureStorage
import tech.aitoolgems.supplierpricefinder.data.model.*
import tech.aitoolgems.supplierpricefinder.databinding.ActivityMainBinding
import tech.aitoolgems.supplierpricefinder.engine.WinnerEngine
import tech.aitoolgems.supplierpricefinder.telegram.ScanProgressListener
import tech.aitoolgems.supplierpricefinder.telegram.TelegramScanner
import tech.aitoolgems.supplierpricefinder.telegram.TelegramClientManager
import tech.aitoolgems.supplierpricefinder.telegram.TelegramAuthState
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var database: LocalDatabase
    private lateinit var secureStorage: SecureStorage
    private lateinit var scanner: TelegramScanner

    private val observationsList = mutableListOf<PriceObservation>()
    private val allToolsCatalogList = mutableListOf<PriceObservation>()
    private val suppliersList = mutableListOf<Supplier>()
    private var currentFilterAccess: AccessType? = null
    private var currentFilterSource: SupplierSource? = null
    private var currentActiveTab = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        database = LocalDatabase(this)
        database.ensureInitialData()
        secureStorage = SecureStorage(this)
        scanner = TelegramScanner(this, database, secureStorage)

        setupUI()
        loadInitialData()
    }

    private fun setupUI() {
        binding.recyclerViewResults.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewResults.adapter = ResultsAdapter(observationsList)

        // Telegram status chip click -> open config dialog
        binding.chipTelegramStatus.setOnClickListener {
            showTelegramConfigDialog()
        }
        updateTelegramStatusChip()

        // Sync Telegram Bots Action
        binding.btnSyncTelegramBots.setOnClickListener {
            syncTelegramBots()
        }

        // Add Supplier Button in Action Bar
        binding.btnAddSupplierAction.setOnClickListener {
            showAddSupplierDialog()
        }

        // Search button
        binding.btnSearchSuppliers.setOnClickListener {
            val query = binding.etSearchTool.text.toString().trim()
            if (query.isNotEmpty()) {
                performLiveScan(query)
            } else {
                Toast.makeText(this, "Please enter a tool name to search", Toast.LENGTH_SHORT).show()
            }
        }

        // Full Catalog Scan button
        binding.btnFullCatalogScan.setOnClickListener {
            performFullCatalogCrawl()
        }

        // In-progress scan controls
        binding.btnPauseScan.setOnClickListener {
            scanner.pauseScan()
            binding.btnPauseScan.visibility = View.GONE
            binding.btnResumeScan.visibility = View.VISIBLE
            binding.tvScanProgressStatus.text = "⏸ Scan paused by user."
        }

        binding.btnResumeScan.setOnClickListener {
            scanner.resumeScan()
            binding.btnResumeScan.visibility = View.GONE
            binding.btnPauseScan.visibility = View.VISIBLE
            binding.tvScanProgressStatus.text = "▶ Resuming crawler..."
        }

        binding.btnCancelScan.setOnClickListener {
            scanner.cancelScan()
            binding.progressBarScan.visibility = View.GONE
            binding.layoutScanControls.visibility = View.GONE
            binding.tvScanProgressStatus.text = "Scan cancelled by user."
            Toast.makeText(this, "Scan cancelled.", Toast.LENGTH_SHORT).show()
        }

        // Filter chips
        binding.chipGroupFilters.setOnCheckedStateChangeListener { _, checkedIds ->
            currentFilterAccess = when {
                checkedIds.contains(R.id.chipFilterPrivate) -> AccessType.PRIVATE
                checkedIds.contains(R.id.chipFilterShared) -> AccessType.SHARED
                checkedIds.contains(R.id.chipFilterEdu) -> AccessType.EDU
                else -> null
            }
            currentFilterSource = when {
                checkedIds.contains(R.id.chipFilterTgOnly) -> SupplierSource.TELEGRAM_DISCOVERY
                checkedIds.contains(R.id.chipFilterSeedOnly) -> SupplierSource.SEED_DATA
                else -> null
            }
            if (currentActiveTab == 1) {
                filterAndDisplayAllTools()
            } else if (currentActiveTab == 2) {
                loadSuppliersTab()
            } else {
                filterAndDisplayResults()
            }
        }

        // 4 Modern Tabs
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentActiveTab = tab?.position ?: 0
                when (currentActiveTab) {
                    0 -> { // Price Comparison
                        binding.cardSearchInput.visibility = View.VISIBLE
                        binding.scrollFilters.visibility = View.VISIBLE
                        binding.cardCheapestVerified.visibility = View.VISIBLE
                        binding.layoutCatalogActions.visibility = View.GONE
                        binding.recyclerViewResults.adapter = ResultsAdapter(observationsList)
                        loadInitialData()
                    }
                    1 -> { // All Bot Tools Master Catalog
                        binding.cardSearchInput.visibility = View.VISIBLE
                        binding.scrollFilters.visibility = View.VISIBLE
                        binding.cardCheapestVerified.visibility = View.GONE
                        binding.layoutCatalogActions.visibility = View.VISIBLE
                        loadAllToolsTab()
                    }
                    2 -> { // Supplier Bots Directory
                        binding.cardSearchInput.visibility = View.GONE
                        binding.scrollFilters.visibility = View.GONE
                        binding.cardCheapestVerified.visibility = View.GONE
                        binding.layoutCatalogActions.visibility = View.VISIBLE
                        loadSuppliersTab()
                    }
                    3 -> { // Scan History & Logs
                        binding.cardSearchInput.visibility = View.GONE
                        binding.scrollFilters.visibility = View.GONE
                        binding.cardCheapestVerified.visibility = View.GONE
                        binding.layoutCatalogActions.visibility = View.GONE
                        loadHistoryTab()
                    }
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun updateTelegramStatusChip() {
        val tgManager = TelegramClientManager.getInstance(this)
        if (tgManager.currentAuthState == TelegramAuthState.AUTHENTICATED) {
            val user = tgManager.authenticatedUserName ?: secureStorage.phoneNumber ?: "Connected"
            binding.chipTelegramStatus.text = "Telegram: Connected ($user)"
            binding.chipTelegramStatus.setChipBackgroundColorResource(R.color.badge_verified)
            binding.chipTelegramStatus.setTextColor(getColor(R.color.badge_verified_text))
        } else if (secureStorage.isConfigured) {
            val rawPhone = secureStorage.phoneNumber ?: ""
            val maskedPhone = if (rawPhone.length > 6) {
                rawPhone.take(3) + "****" + rawPhone.takeLast(4)
            } else {
                "Configured"
            }
            binding.chipTelegramStatus.text = "Telegram: Credentials Saved ($maskedPhone)"
            binding.chipTelegramStatus.setChipBackgroundColorResource(R.color.badge_unverified)
            binding.chipTelegramStatus.setTextColor(getColor(R.color.badge_unverified_text))
        } else {
            binding.chipTelegramStatus.text = getString(R.string.telegram_status_disconnected)
            binding.chipTelegramStatus.setChipBackgroundColorResource(R.color.card_background)
            binding.chipTelegramStatus.setTextColor(getColor(R.color.primary))
        }
    }

    private fun loadInitialData() {
        val query = binding.etSearchTool.text.toString().trim()
        val cached = database.getObservationsForQuery(query)
        observationsList.clear()
        observationsList.addAll(cached)
        filterAndDisplayResults()
    }

    private fun loadAllToolsTab() {
        val all = database.getAllWholesaleProducts()
        allToolsCatalogList.clear()
        allToolsCatalogList.addAll(all)
        binding.tvCatalogCountSummary.text = "Wholesale Catalog: ${all.size} Active Products Across 16 Bots"
        filterAndDisplayAllTools()
    }

    private fun filterAndDisplayAllTools() {
        val filtered = if (currentFilterAccess != null) {
            allToolsCatalogList.filter { it.accessType == currentFilterAccess }
        } else {
            allToolsCatalogList
        }
        binding.recyclerViewResults.adapter = AllToolsAdapter(filtered)
    }

    private fun getSelectedSearchPolicy(): SearchPolicy {
        return if (binding.chipPolicyExhaustive.isChecked) {
            SearchPolicy.EXHAUSTIVE_SCAN
        } else {
            SearchPolicy.FAST_LOOKUP
        }
    }

    private fun performLiveScan(toolName: String) {
        val policy = getSelectedSearchPolicy()
        binding.progressBarScan.visibility = View.VISIBLE
        binding.layoutScanControls.visibility = View.VISIBLE
        binding.btnPauseScan.visibility = View.VISIBLE
        binding.btnResumeScan.visibility = View.GONE
        binding.tvScanProgressStatus.text = "Starting live scanner (${policy.label}) for '$toolName' across configured bots..."

        val initialCached = database.getObservationsForQuery(toolName)
        if (initialCached.isNotEmpty()) {
            observationsList.clear()
            observationsList.addAll(initialCached)
            filterAndDisplayResults()
        }

        lifecycleScope.launch {
            val newlyExtracted = scanner.scanSuppliersForTool(
                targetTool = toolName,
                targetSupplierId = null,
                searchPolicy = policy,
                listener = createScanListener()
            )

            binding.progressBarScan.visibility = View.GONE
            binding.layoutScanControls.visibility = View.GONE
            val finalMatches = database.getObservationsForQuery(toolName)
            if (finalMatches.isNotEmpty()) {
                observationsList.clear()
                observationsList.addAll(finalMatches)
                binding.tvScanProgressStatus.text = "Scan complete. Found ${finalMatches.size} quote(s) for '$toolName'."
            } else {
                binding.tvScanProgressStatus.text = "No direct stock for '$toolName'. Showing popular wholesale tools below."
                observationsList.clear()
                observationsList.addAll(database.getObservationsForQuery("").take(15))
            }
            filterAndDisplayResults()
        }
    }

    private fun performFullCatalogCrawl() {
        val policy = getSelectedSearchPolicy()
        binding.progressBarScan.visibility = View.VISIBLE
        binding.layoutScanControls.visibility = View.VISIBLE
        binding.btnPauseScan.visibility = View.VISIBLE
        binding.btnResumeScan.visibility = View.GONE
        binding.tvScanProgressStatus.text = "Starting full catalog crawl (${policy.label}) across all enabled supplier bots..."

        lifecycleScope.launch {
            val newlyExtracted = scanner.scanSuppliersForTool(
                targetTool = "",
                targetSupplierId = null,
                searchPolicy = policy,
                listener = createScanListener()
            )

            binding.progressBarScan.visibility = View.GONE
            binding.layoutScanControls.visibility = View.GONE
            binding.tvScanProgressStatus.text = "Full crawl complete! Extracted ${newlyExtracted.size} total offers & variants across bots."
            Toast.makeText(this@MainActivity, "Full catalog crawl complete (${newlyExtracted.size} offers)", Toast.LENGTH_LONG).show()

            if (currentActiveTab == 1) {
                loadAllToolsTab()
            } else {
                loadInitialData()
            }
        }
    }

    private fun createScanListener(): ScanProgressListener {
        return object : ScanProgressListener {
            override fun onSupplierScanning(supplierHandle: String, message: String) {
                runOnUiThread {
                    binding.tvScanProgressStatus.text = "[$supplierHandle] $message"
                }
            }

            override fun onProductExtracted(observation: PriceObservation) {
                runOnUiThread {
                    if (observationsList.none { it.id == observation.id || (it.matchingKey == observation.matchingKey && it.supplierId == observation.supplierId) }) {
                        observationsList.add(0, observation)
                        filterAndDisplayResults()
                    }
                }
            }

            override fun onSupplierBlocked(supplierHandle: String, reason: String) {
                runOnUiThread {
                    binding.tvScanProgressStatus.text = "⚠️ [$supplierHandle] $reason"
                }
            }

            override fun onApprovalRequired(item: ManualReviewItem, onDecision: (Boolean) -> Unit) {
                runOnUiThread {
                    showApprovalDialog(item, onDecision)
                }
            }
        }
    }

    private fun showApprovalDialog(item: ManualReviewItem, onDecision: (Boolean) -> Unit) {
        val title = if (item.itemType == "MEMBERSHIP_GATE") {
            "Telegram Channel Membership Required"
        } else {
            "Confirm Bot Navigation Button"
        }

        val msg = if (item.itemType == "MEMBERSHIP_GATE") {
            "Bot ${item.supplierHandle} requires joining ${item.targetHandleOrUrl ?: "a channel"} to unlock its catalog.\n\n" +
            "Safety Policy: Joining will be recorded on your Telegram account. Do you authorize this channel join?"
        } else {
            "${item.promptMessage}\n\nEvidence: ${item.rawEvidence}\n\n" +
            "Safety Policy: If this button opens a catalog list, tap 'Approve'. If it is an immediate purchase, tap 'Deny'."
        }

        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(msg)
            .setCancelable(false)
            .setPositiveButton(if (item.itemType == "MEMBERSHIP_GATE") "Approve & Join" else "Yes, Opens Catalog") { _, _ ->
                database.updateReviewItemStatus(item.id, "APPROVED")
                onDecision(true)
            }
            .setNegativeButton("Deny / Skip") { _, _ ->
                database.updateReviewItemStatus(item.id, "REJECTED")
                onDecision(false)
            }
            .show()
    }

    private fun filterAndDisplayResults() {
        val filtered = if (currentFilterAccess != null) {
            observationsList.filter { it.accessType == currentFilterAccess }
        } else {
            observationsList
        }

        // Evaluate Winner strictly under Rule 9
        val query = binding.etSearchTool.text.toString().trim()
        val eval = WinnerEngine.evaluatePlan(query, filtered)

        if (eval.hasVerifiedWinner && eval.lowestVerified != null) {
            val winner = eval.lowestVerified
            binding.tvWinnerTitle.text = getString(R.string.cheapest_verified_title)
            binding.tvWinnerTitle.setTextColor(getColor(R.color.primary))
            binding.tvWinnerSupplier.text = "${winner.supplierName} (${winner.supplierHandle})"
            binding.tvWinnerPrice.text = "$${String.format(Locale.ROOT, "%.2f", winner.amount)} ${winner.currency} (~Rs. ${String.format(Locale.ROOT, "%,.0f", winner.amountPkr)} PKR)"
            binding.tvWinnerWarranty.text = "🛡 ${winner.warranty}"
            binding.tvWinnerSpecs.text = "Duration: ${winner.duration} | Access: ${winner.accessType.name} | Stock: ${winner.stockStatus}"
            binding.cardCheapestVerified.visibility = View.VISIBLE
        } else {
            binding.tvWinnerTitle.text = getString(R.string.no_verified_winner)
            binding.tvWinnerTitle.setTextColor(getColor(R.color.badge_unverified_text))
            if (eval.lowestAdvertised != null) {
                val low = eval.lowestAdvertised
                binding.tvWinnerSupplier.text = "Lowest Advertised: ${low.supplierName} (${low.supplierHandle})"
                binding.tvWinnerPrice.text = "$${String.format(Locale.ROOT, "%.2f", low.amount)} ${low.currency} (~Rs. ${String.format(Locale.ROOT, "%,.0f", low.amountPkr)} PKR)"
                binding.tvWinnerWarranty.text = "Status: ${low.verificationStatus.name}"
                binding.tvWinnerSpecs.text = "Pending proof. Automated verified winner not awarded yet."
                binding.cardCheapestVerified.visibility = View.VISIBLE
            } else {
                binding.cardCheapestVerified.visibility = View.GONE
            }
        }

        binding.recyclerViewResults.adapter = ResultsAdapter(filtered)
    }

    private fun syncTelegramBots() {
        val tgManager = TelegramClientManager.getInstance(this)
        if (tgManager.currentAuthState != TelegramAuthState.AUTHENTICATED) {
            Toast.makeText(this, "Please authenticate Telegram first to sync account bots.", Toast.LENGTH_LONG).show()
            showTelegramConfigDialog()
            return
        }

        binding.tvScanProgressStatus.text = "Discovering bots from your Telegram account dialogs..."
        binding.progressBarScan.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val discovered = tgManager.discoverAccountBots()
                val syncedCount = database.syncDiscoveredTelegramBots(discovered)
                binding.progressBarScan.visibility = View.GONE
                binding.tvScanProgressStatus.text = "Sync complete: Discovered ${discovered.size} bots ($syncedCount synced)."
                Toast.makeText(this@MainActivity, "Synced ${discovered.size} bots from your Telegram account!", Toast.LENGTH_SHORT).show()
                if (currentActiveTab == 2) {
                    loadSuppliersTab()
                }
            } catch (e: Exception) {
                binding.progressBarScan.visibility = View.GONE
                binding.tvScanProgressStatus.text = "Failed to sync Telegram bots: ${e.message}"
                Toast.makeText(this@MainActivity, "Sync error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun loadSuppliersTab() {
        suppliersList.clear()
        val all = database.getAllSuppliers(currentFilterSource)
        suppliersList.addAll(all)
        val counts = database.getSuppliersCountBySource()
        val tgCount = counts[SupplierSource.TELEGRAM_DISCOVERY] ?: 0
        val seedCount = counts[SupplierSource.SEED_DATA] ?: 0
        binding.tvCatalogCountSummary.text = "Suppliers: ${suppliersList.size} ($tgCount Telegram, $seedCount Seed)"
        binding.recyclerViewResults.adapter = SuppliersAdapter(suppliersList,
            onToggle = { supplier, isEnabled ->
                database.setSupplierEnabled(supplier.id, isEnabled)
                Toast.makeText(this, "${supplier.canonicalName}: " + if (isEnabled) "Enabled" else "Disabled", Toast.LENGTH_SHORT).show()
            },
            onCrawl = { supplier ->
                Toast.makeText(this, "Crawling ${supplier.primaryHandle}...", Toast.LENGTH_SHORT).show()
                performSupplierSpecificScan(supplier)
            }
        )
    }

    private fun performSupplierSpecificScan(supplier: Supplier) {
        val policy = getSelectedSearchPolicy()
        binding.progressBarScan.visibility = View.VISIBLE
        binding.tvScanProgressStatus.text = "Targeted scan (${policy.label}): ${supplier.primaryHandle}..."

        lifecycleScope.launch {
            val newlyExtracted = scanner.scanSuppliersForTool(
                targetTool = "",
                targetSupplierId = supplier.id,
                searchPolicy = policy,
                listener = object : ScanProgressListener {
                    override fun onSupplierScanning(supplierHandle: String, message: String) {
                        runOnUiThread {
                            binding.tvScanProgressStatus.text = "[$supplierHandle] $message"
                        }
                    }

                    override fun onProductExtracted(observation: PriceObservation) {
                        runOnUiThread {
                            binding.tvScanProgressStatus.text = "Extracted: ${observation.productName} (${observation.amount} ${observation.currency})"
                        }
                    }

                    override fun onSupplierBlocked(supplierHandle: String, reason: String) {
                        runOnUiThread {
                            binding.tvScanProgressStatus.text = "⚠️ [$supplierHandle] $reason"
                        }
                    }
                }
            )

            binding.progressBarScan.visibility = View.GONE
            Toast.makeText(this@MainActivity, "Scan complete for ${supplier.primaryHandle}. Found ${newlyExtracted.size} offer(s).", Toast.LENGTH_LONG).show()
            loadSuppliersTab()
        }
    }

    private fun loadHistoryTab() {
        val runs = database.getAllScanRuns()
        if (runs.isNotEmpty()) {
            binding.recyclerViewResults.adapter = ScanRunsAdapter(runs)
        } else {
            val allHistory = database.getObservationsForQuery("")
            binding.recyclerViewResults.adapter = ResultsAdapter(allHistory)
        }
    }

    private fun showObservationDetailsDialog(obs: PriceObservation) {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
        val dateStr = sdf.format(Date(obs.observedAt))
        val msg = """
            📦 Product: ${obs.productName}
            🏷 Plan Name: ${obs.planName}
            ⏱ Duration: ${obs.duration}
            👤 Access Model: ${obs.accessType.name}
            💵 Wholesale Cost: $${String.format(Locale.ROOT, "%.2f", obs.amount)} ${obs.currency}
            🇵🇰 Estimated PKR: Rs. ${String.format(Locale.ROOT, "%,.0f", obs.amountPkr)} PKR
            🛡 Stated Warranty: ${obs.warranty}
            📦 Stock Status: ${obs.stockStatus}
            🔍 Verification: ${obs.verificationStatus.name}
            🤖 Supplier: ${obs.supplierName} (${obs.supplierHandle})
            📡 Capture Method: ${obs.captureMethod}
            🕒 Observed At: $dateStr
            🔗 Source: ${obs.sourceUrl ?: "Private MTProto Bot"}

            📝 Raw Source Evidence:
            "${obs.originalRawText}"
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("Wholesale Evidence Record")
            .setMessage(msg)
            .setPositiveButton("Copy for Store") { _, _ ->
                copyToClipboard(obs)
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showRunTimelineDialog(run: ScanRun) {
        val events = database.getTimelineForRun(run.id)
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.ROOT)
        val lines = if (events.isEmpty()) {
            "No granular timeline events logged for this scan run."
        } else {
            events.joinToString("\n\n") { ev ->
                "⏱ [${sdf.format(Date(ev.timestamp))}] ${ev.supplierHandle} | ${ev.state.name}\n" +
                "Action: ${ev.actionLabel} (${ev.actionType.name}) → ${ev.outcomeStatus}\n" +
                "Reason: ${ev.decisionReason}"
            }
        }

        AlertDialog.Builder(this)
            .setTitle("Scan Run Audit Timeline (${run.trigger})")
            .setMessage("Status: ${run.status} | Found: ${run.totalOffersFound} offers\n\n$lines")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun copyToClipboard(observation: PriceObservation) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = """
🔥 Tool: ${observation.productName}
🏷 Plan: ${observation.planName}
⏱ Duration: ${observation.duration}
👤 Access: ${observation.accessType.name}
💵 Wholesale Cost: $${String.format(Locale.ROOT, "%.2f", observation.amount)} ${observation.currency} (~Rs. ${String.format(Locale.ROOT, "%,.0f", observation.amountPkr)} PKR)
🛡 Warranty: ${observation.warranty}
📦 Stock Status: ${observation.stockStatus}
🤖 Supplier Bot: ${observation.supplierHandle}
        """.trimIndent()
        val clip = ClipData.newPlainText("Wholesale Tool Specs", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "✓ Copied to clipboard! Ready to paste into aitoolgems.tech", Toast.LENGTH_LONG).show()
    }

    private fun showAddSupplierDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 30, 50, 30)
            setBackgroundColor(getColor(R.color.card_background))
        }

        val etHandle = EditText(this).apply {
            hint = "Bot Username (e.g. @ptstorebd_bot)"
            setTextColor(getColor(R.color.text_primary))
            setHintTextColor(getColor(R.color.text_secondary))
        }
        val etName = EditText(this).apply {
            hint = "Bot Name (e.g. PT Store BD)"
            setTextColor(getColor(R.color.text_primary))
            setHintTextColor(getColor(R.color.text_secondary))
        }
        val etUrl = EditText(this).apply {
            hint = "Public Channel (e.g. https://t.me/s/ptsbd26)"
            setTextColor(getColor(R.color.text_primary))
            setHintTextColor(getColor(R.color.text_secondary))
        }

        layout.addView(etHandle)
        layout.addView(etName)
        layout.addView(etUrl)

        AlertDialog.Builder(this)
            .setTitle("Add New Telegram Supplier Bot")
            .setMessage("Enter the bot handle to monitor its shop products and prices:")
            .setView(layout)
            .setPositiveButton("Add & Crawl") { _, _ ->
                val handle = etHandle.text.toString().trim()
                val name = etName.text.toString().trim()
                val url = etUrl.text.toString().trim()
                if (handle.isNotEmpty() && name.isNotEmpty()) {
                    val cleanHandle = if (handle.startsWith("@")) handle else "@$handle"
                    val newSupplier = Supplier(
                        id = 0,
                        canonicalName = name,
                        primaryHandle = cleanHandle,
                        allHandles = cleanHandle,
                        entityType = "BOT",
                        publicUrl = if (url.isNotEmpty()) url else null,
                        supportedCurrencies = "USD,USDT",
                        reliabilityScore = 75f,
                        notes = "Custom added supplier bot."
                    )
                    database.addSupplier(newSupplier)
                    Toast.makeText(this, "Bot '$cleanHandle' added! Crawling catalog...", Toast.LENGTH_SHORT).show()
                    loadSuppliersTab()
                    performLiveScan("")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showTelegramConfigDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_telegram_config, null)
        val etApiId = view.findViewById<EditText>(R.id.etApiId)
        val etApiHash = view.findViewById<EditText>(R.id.etApiHash)
        val etPhoneNumber = view.findViewById<EditText>(R.id.etPhoneNumber)
        val btnRequestOtp = view.findViewById<View>(R.id.btnRequestOtp)
        val layoutOtpVerification = view.findViewById<View>(R.id.layoutOtpVerification)
        val etOtpCode = view.findViewById<EditText>(R.id.etOtpCode)
        val etTwoStepPassword = view.findViewById<EditText>(R.id.etTwoStepPassword)
        val tvTelegramStatusMsg = view.findViewById<TextView>(R.id.tvTelegramStatusMsg)

        etApiId.setText(secureStorage.apiId ?: "")
        etApiHash.setText(secureStorage.apiHash ?: "")
        etPhoneNumber.setText(secureStorage.phoneNumber ?: "")

        if (!secureStorage.isEncryptionAvailable) {
            tvTelegramStatusMsg.text = "⚠️ Hardware Keystore encryption unavailable on this device. For account security, API credentials will not be stored in unencrypted plaintext."
            tvTelegramStatusMsg.setTextColor(getColor(R.color.badge_risk_text))
            btnRequestOtp.isEnabled = false
        }

        btnRequestOtp.setOnClickListener {
            if (!secureStorage.isEncryptionAvailable) {
                Toast.makeText(this, "Secure hardware storage unavailable.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val id = etApiId.text.toString().trim()
            val hash = etApiHash.text.toString().trim()
            var phone = etPhoneNumber.text.toString().trim()

            if (id.isEmpty() || hash.isEmpty()) {
                Toast.makeText(this, "Please enter both Telegram API ID and API Hash", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!phone.startsWith("+")) {
                phone = "+$phone"
                etPhoneNumber.setText(phone)
            }

            // Accept any worldwide international format with 7 to 15 digits (e.g. +81... for Japan, +1... for USA, +92... for Pakistan)
            val internationalPhoneRegex = "^\\+[1-9]\\d{6,14}$".toRegex()
            if (!internationalPhoneRegex.matches(phone)) {
                Toast.makeText(this, "Please enter a valid international phone number with country code (e.g. +81... for Japan, +1... for USA, +92... for Pakistan)", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            // Save credentials to Keystore
            secureStorage.apiId = id
            secureStorage.apiHash = hash
            secureStorage.phoneNumber = phone

            // Trigger genuine MTProto authentication flow via TDLib
            val tgManager = TelegramClientManager.getInstance(this)
            tgManager.onStateChangedListener = { state, msg ->
                runOnUiThread {
                    tvTelegramStatusMsg.text = msg
                    when (state) {
                        TelegramAuthState.WAIT_CODE -> {
                            layoutOtpVerification.visibility = View.VISIBLE
                            btnRequestOtp.visibility = View.GONE
                            tvTelegramStatusMsg.setTextColor(getColor(R.color.badge_verified_text))
                            Toast.makeText(this@MainActivity, "Code dispatched! Check your Telegram app.", Toast.LENGTH_LONG).show()
                        }
                        TelegramAuthState.WAIT_PASSWORD -> {
                            layoutOtpVerification.visibility = View.VISIBLE
                            etTwoStepPassword.visibility = View.VISIBLE
                            tvTelegramStatusMsg.setTextColor(getColor(R.color.primary))
                        }
                        TelegramAuthState.AUTHENTICATED -> {
                            secureStorage.sessionString = "TDLIB_SESSION_ACTIVE"
                            updateTelegramStatusChip()
                            Toast.makeText(this@MainActivity, "✓ Connected to Telegram: ${tgManager.authenticatedUserName ?: phone}", Toast.LENGTH_LONG).show()
                        }
                        TelegramAuthState.ERROR -> {
                            tvTelegramStatusMsg.setTextColor(getColor(R.color.badge_risk_text))
                        }
                        else -> {}
                    }
                }
            }
            tgManager.startAuthentication(id.toIntOrNull() ?: 0, hash, phone)
        }

        AlertDialog.Builder(this)
            .setView(view)
            .setPositiveButton("Verify & Connect") { _, _ ->
                val code = etOtpCode.text.toString().trim()
                val password = etTwoStepPassword.text.toString().trim()
                val phone = etPhoneNumber.text.toString().trim()
                val tgManager = TelegramClientManager.getInstance(this)

                if (code.isNotEmpty()) {
                    tvTelegramStatusMsg.text = "Verifying code with Telegram DC servers..."
                    tgManager.submitVerificationCode(code) { success, msg ->
                        if (success) {
                            if (password.isNotEmpty()) {
                                tgManager.submitPassword(password) { passSuccess, passMsg ->
                                    if (passSuccess) {
                                        secureStorage.sessionString = "TDLIB_SESSION_ACTIVE"
                                        updateTelegramStatusChip()
                                        Toast.makeText(this@MainActivity, "✓ Connected to Telegram!", Toast.LENGTH_SHORT).show()
                                        loadInitialData()
                                    } else {
                                        Toast.makeText(this@MainActivity, "2FA Error: $passMsg", Toast.LENGTH_LONG).show()
                                    }
                                }
                            } else {
                                secureStorage.sessionString = "TDLIB_SESSION_ACTIVE"
                                updateTelegramStatusChip()
                                Toast.makeText(this@MainActivity, "✓ Connected to Telegram!", Toast.LENGTH_SHORT).show()
                                loadInitialData()
                            }
                        } else {
                            Toast.makeText(this@MainActivity, "Verification failed: $msg", Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    updateTelegramStatusChip()
                }
            }
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Disconnect") { _, _ ->
                TelegramClientManager.getInstance(this).logOut {
                    secureStorage.clearCredentials()
                    updateTelegramStatusChip()
                    Toast.makeText(this, "Disconnected from Telegram.", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    // Results RecyclerView Adapter (Tab 0: Price Comparison)
    inner class ResultsAdapter(private val list: List<PriceObservation>) : RecyclerView.Adapter<ResultsAdapter.ViewHolder>() {
        inner class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
            val tvRank: TextView = view.findViewById(R.id.tvItemRank)
            val tvSupplier: TextView = view.findViewById(R.id.tvItemSupplier)
            val tvPrice: TextView = view.findViewById(R.id.tvItemPrice)
            val tvDetails: TextView = view.findViewById(R.id.tvItemDetails)
            val tvPkr: TextView = view.findViewById(R.id.tvItemPkr)
            val tvBadgeVerification: TextView = view.findViewById(R.id.tvItemBadgeVerification)
            val tvTimestamp: TextView = view.findViewById(R.id.tvItemTimestamp)
            val tvEvidence: TextView = view.findViewById(R.id.tvItemEvidence)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_supplier_result, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = list[position]
            holder.tvRank.text = "#${position + 1}"
            holder.tvSupplier.text = "${item.supplierName} (${item.supplierHandle})"
            holder.tvPrice.text = "$${String.format(Locale.ROOT, "%.2f", item.amount)} ${item.currency}"
            holder.tvPkr.text = "~Rs. ${String.format(Locale.ROOT, "%,.0f", item.amountPkr)} PKR"
            holder.tvDetails.text = "${item.duration} | ${item.accessType.name} | ${item.warranty}"
            when (item.verificationStatus) {
                VerificationStatus.LIVE_OBSERVED_UNVERIFIED -> {
                    holder.tvBadgeVerification.text = "LIVE NOW"
                    holder.tvBadgeVerification.setBackgroundColor(getColor(R.color.badge_verified))
                    holder.tvBadgeVerification.setTextColor(getColor(R.color.badge_verified_text))
                }
                VerificationStatus.VERIFIED -> {
                    holder.tvBadgeVerification.text = "VERIFIED"
                    holder.tvBadgeVerification.setBackgroundColor(getColor(R.color.badge_verified))
                    holder.tvBadgeVerification.setTextColor(getColor(R.color.badge_verified_text))
                }
                VerificationStatus.CACHED -> {
                    holder.tvBadgeVerification.text = "CACHED"
                    holder.tvBadgeVerification.setBackgroundColor(getColor(R.color.card_elevated))
                    holder.tvBadgeVerification.setTextColor(getColor(R.color.text_secondary))
                }
                VerificationStatus.SEED_CATALOG -> {
                    holder.tvBadgeVerification.text = "SEED DATA"
                    holder.tvBadgeVerification.setBackgroundColor(getColor(R.color.card_elevated))
                    holder.tvBadgeVerification.setTextColor(getColor(R.color.accent))
                }
                else -> {
                    holder.tvBadgeVerification.text = "PREVIEW"
                    holder.tvBadgeVerification.setBackgroundColor(getColor(R.color.badge_unverified))
                    holder.tvBadgeVerification.setTextColor(getColor(R.color.badge_unverified_text))
                }
            }

            holder.tvEvidence.text = "📋 Copy"
            holder.tvEvidence.setOnClickListener {
                copyToClipboard(item)
            }
            holder.view.setOnClickListener {
                showObservationDetailsDialog(item)
            }
        }

        override fun getItemCount() = list.size
    }

    // Scan Runs Adapter (Tab 3: Scan Run History & Audit Timeline)
    inner class ScanRunsAdapter(private val runs: List<ScanRun>) : RecyclerView.Adapter<ScanRunsAdapter.ViewHolder>() {
        inner class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
            val tvRank: TextView = view.findViewById(R.id.tvItemRank)
            val tvSupplier: TextView = view.findViewById(R.id.tvItemSupplier)
            val tvPrice: TextView = view.findViewById(R.id.tvItemPrice)
            val tvDetails: TextView = view.findViewById(R.id.tvItemDetails)
            val tvPkr: TextView = view.findViewById(R.id.tvItemPkr)
            val tvBadgeVerification: TextView = view.findViewById(R.id.tvItemBadgeVerification)
            val tvTimestamp: TextView = view.findViewById(R.id.tvItemTimestamp)
            val tvEvidence: TextView = view.findViewById(R.id.tvItemEvidence)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_supplier_result, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val run = runs[position]
            holder.tvRank.text = "#${position + 1}"
            holder.tvSupplier.text = "${run.trigger} (${run.status})"
            holder.tvPrice.text = "${run.totalOffersFound} Offers"
            holder.tvPkr.text = "${run.totalSuppliers} Bots"
            holder.tvDetails.text = "Completed: ${run.completedSuppliers} | Partial: ${run.partialSuppliers} | Blocked: ${run.blockedSuppliers}"
            holder.tvBadgeVerification.text = run.status
            if (run.status == "COMPLETED") {
                holder.tvBadgeVerification.setBackgroundColor(getColor(R.color.badge_verified))
                holder.tvBadgeVerification.setTextColor(getColor(R.color.badge_verified_text))
            } else {
                holder.tvBadgeVerification.setBackgroundColor(getColor(R.color.badge_unverified))
                holder.tvBadgeVerification.setTextColor(getColor(R.color.badge_unverified_text))
            }

            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT)
            holder.tvTimestamp.text = "Started: ${sdf.format(Date(run.startedAt))}"
            holder.tvEvidence.text = "📜 Timeline"
            holder.tvEvidence.setOnClickListener {
                showRunTimelineDialog(run)
            }
            holder.view.setOnClickListener {
                showRunTimelineDialog(run)
            }
        }

        override fun getItemCount() = runs.size
    }

    // All Tools Master Catalog Adapter (Tab 1: All Wholesale Products)
    inner class AllToolsAdapter(private val list: List<PriceObservation>) : RecyclerView.Adapter<AllToolsAdapter.ViewHolder>() {
        inner class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
            val tvRank: TextView = view.findViewById(R.id.tvItemRank)
            val tvSupplier: TextView = view.findViewById(R.id.tvItemSupplier)
            val tvPrice: TextView = view.findViewById(R.id.tvItemPrice)
            val tvDetails: TextView = view.findViewById(R.id.tvItemDetails)
            val tvPkr: TextView = view.findViewById(R.id.tvItemPkr)
            val tvBadgeVerification: TextView = view.findViewById(R.id.tvItemBadgeVerification)
            val tvTimestamp: TextView = view.findViewById(R.id.tvItemTimestamp)
            val tvEvidence: TextView = view.findViewById(R.id.tvItemEvidence)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_supplier_result, parent, false)
            return ViewHolder(v)
        }

        override fun getItemCount(): Int = list.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = list[position]
            holder.tvRank.text = "#${position + 1}"
            holder.tvSupplier.text = "${item.productName} • ${item.supplierHandle}"
            holder.tvPrice.text = "$${String.format(Locale.ROOT, "%.2f", item.amount)} ${item.currency}"
            holder.tvDetails.text = "${item.duration} | ${item.accessType.name} | ${item.warranty}"
            holder.tvPkr.text = "~Rs. ${String.format(Locale.ROOT, "%,.0f", item.amountPkr)} PKR"
            holder.tvBadgeVerification.text = item.stockStatus
            holder.tvBadgeVerification.setBackgroundColor(getColor(R.color.card_elevated))
            holder.tvBadgeVerification.setTextColor(getColor(R.color.primary))
            holder.tvTimestamp.text = "Bot: ${item.supplierName}"
            holder.tvEvidence.text = "📋 Copy for Store"
            holder.tvEvidence.setOnClickListener {
                copyToClipboard(item)
            }
            holder.view.setOnClickListener {
                copyToClipboard(item)
            }
        }
    }

    // Suppliers Directory Adapter (Tab 2: Bots Management)
    inner class SuppliersAdapter(
        private val list: List<Supplier>,
        private val onToggle: (Supplier, Boolean) -> Unit,
        private val onCrawl: (Supplier) -> Unit
    ) : RecyclerView.Adapter<SuppliersAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tvSupplierName)
            val tvHandle: TextView = view.findViewById(R.id.tvSupplierHandle)
            val tvSource: TextView = view.findViewById(R.id.tvSupplierSource)
            val tvStatus: TextView = view.findViewById(R.id.tvSupplierStatus)
            val btnTest: View = view.findViewById(R.id.btnTestSupplier)
            val switchActive: com.google.android.material.switchmaterial.SwitchMaterial = view.findViewById(R.id.switchSupplierActive)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_supplier_management, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val supplier = list[position]
            holder.tvName.text = supplier.canonicalName
            holder.tvHandle.text = supplier.primaryHandle
            val sourceLabel = when (supplier.source) {
                SupplierSource.TELEGRAM_DISCOVERY -> "🤖 Telegram Discovered"
                SupplierSource.SEED_DATA -> "📦 Seed Catalog"
                SupplierSource.IMPORTED -> "📥 Imported Catalog"
            }
            holder.tvSource.text = sourceLabel
            holder.tvSource.setTextColor(
                if (supplier.source == SupplierSource.TELEGRAM_DISCOVERY)
                    getColor(R.color.badge_verified_text)
                else
                    getColor(R.color.accent)
            )
            holder.tvStatus.text = "Reliability: ${supplier.reliabilityScore.toInt()}% | ${supplier.notes.take(45)}"
            holder.switchActive.isChecked = supplier.isEnabled

            holder.switchActive.setOnCheckedChangeListener { _, isChecked ->
                onToggle(supplier, isChecked)
            }

            holder.btnTest.setOnClickListener {
                onCrawl(supplier)
            }
        }

        override fun getItemCount() = list.size
    }
}
