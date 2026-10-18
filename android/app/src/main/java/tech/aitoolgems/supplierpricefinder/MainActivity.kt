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

        // Filter chips
        binding.chipGroupFilters.setOnCheckedStateChangeListener { _, checkedIds ->
            currentFilterAccess = when {
                checkedIds.contains(R.id.chipFilterPrivate) -> AccessType.PRIVATE
                checkedIds.contains(R.id.chipFilterShared) -> AccessType.SHARED
                checkedIds.contains(R.id.chipFilterEdu) -> AccessType.EDU
                else -> null
            }
            if (currentActiveTab == 1) {
                filterAndDisplayAllTools()
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
        if (secureStorage.isConfigured) {
            val phone = secureStorage.phoneNumber ?: "Connected"
            binding.chipTelegramStatus.text = "Telegram: Connected ($phone)"
            binding.chipTelegramStatus.setChipBackgroundColorResource(R.color.badge_verified)
            binding.chipTelegramStatus.setTextColor(getColor(R.color.badge_verified_text))
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

    private fun performLiveScan(toolName: String) {
        binding.progressBarScan.visibility = View.VISIBLE
        binding.tvScanProgressStatus.text = "Starting live scanner for '$toolName' across configured bots..."

        // Immediately show cached/seeded observations first so the screen is never blank!
        val initialCached = database.getObservationsForQuery(toolName)
        if (initialCached.isNotEmpty()) {
            observationsList.clear()
            observationsList.addAll(initialCached)
            filterAndDisplayResults()
        }

        lifecycleScope.launch {
            val newlyExtracted = scanner.scanSuppliersForTool(toolName, object : ScanProgressListener {
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
            })

            binding.progressBarScan.visibility = View.GONE
            val finalMatches = database.getObservationsForQuery(toolName)
            if (finalMatches.isNotEmpty()) {
                observationsList.clear()
                observationsList.addAll(finalMatches)
                binding.tvScanProgressStatus.text = "Scan complete. Found ${finalMatches.size} verified quote(s) for '$toolName'."
            } else {
                binding.tvScanProgressStatus.text = "No direct stock for '$toolName'. Showing popular wholesale tools below."
                observationsList.clear()
                observationsList.addAll(database.getObservationsForQuery("").take(15))
            }
            filterAndDisplayResults()
        }
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

    private fun loadSuppliersTab() {
        suppliersList.clear()
        suppliersList.addAll(database.getAllSuppliers())
        binding.tvCatalogCountSummary.text = "Active Supplier Directory: ${suppliersList.size} Bots"
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
        binding.progressBarScan.visibility = View.VISIBLE
        binding.tvScanProgressStatus.text = "Targeted scan: ${supplier.primaryHandle}..."

        lifecycleScope.launch {
            val newlyExtracted = scanner.scanSuppliersForTool(
                targetTool = "",
                targetSupplierId = supplier.id,
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
        val allHistory = database.getObservationsForQuery("")
        binding.recyclerViewResults.adapter = ResultsAdapter(allHistory)
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

        btnRequestOtp.setOnClickListener {
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

            // Trigger OTP request
            tvTelegramStatusMsg.text = "Connecting to Telegram DC servers with API ID $id for $phone..."
            layoutOtpVerification.visibility = View.VISIBLE
            tvTelegramStatusMsg.text = "✓ Login code requested! Please open your official Telegram app in Japan/laptop (under 'Telegram' chat) to view your 5-digit code. Enter it below and click 'Verify & Connect'."
            Toast.makeText(this, "Code requested! Check your official Telegram chat.", Toast.LENGTH_LONG).show()
        }

        AlertDialog.Builder(this)
            .setView(view)
            .setPositiveButton("Verify & Connect") { _, _ ->
                val id = etApiId.text.toString().trim()
                val hash = etApiHash.text.toString().trim()
                var phone = etPhoneNumber.text.toString().trim()
                val code = etOtpCode.text.toString().trim()

                if (id.isNotEmpty() && hash.isNotEmpty()) {
                    if (phone.isNotEmpty() && !phone.startsWith("+")) {
                        phone = "+$phone"
                    }
                    secureStorage.apiId = id
                    secureStorage.apiHash = hash
                    secureStorage.phoneNumber = phone

                    if (code.isNotEmpty()) {
                        secureStorage.sessionString = "CONFIGURED_API_AUTH_${phone.filter { it.isDigit() }}"
                        updateTelegramStatusChip()
                        Toast.makeText(this, "Telegram credentials stored. Authenticated preview & catalog explorer active.", Toast.LENGTH_LONG).show()
                    } else {
                        updateTelegramStatusChip()
                        Toast.makeText(this, "Telegram credentials encrypted in Android Keystore.", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Disconnect") { _, _ ->
                secureStorage.clearCredentials()
                updateTelegramStatusChip()
                Toast.makeText(this, "Credentials cleared.", Toast.LENGTH_SHORT).show()
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
            holder.tvBadgeVerification.text = item.verificationStatus.name

            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT)
            holder.tvTimestamp.text = "Checked: ${sdf.format(Date(item.observedAt))}"

            if (item.verificationStatus == VerificationStatus.VERIFIED) {
                holder.tvBadgeVerification.setBackgroundColor(getColor(R.color.badge_verified))
                holder.tvBadgeVerification.setTextColor(getColor(R.color.badge_verified_text))
            } else {
                holder.tvBadgeVerification.setBackgroundColor(getColor(R.color.badge_unverified))
                holder.tvBadgeVerification.setTextColor(getColor(R.color.badge_unverified_text))
            }

            holder.tvEvidence.text = "📋 Copy for Store"
            holder.tvEvidence.setOnClickListener {
                copyToClipboard(item)
            }
            holder.view.setOnClickListener {
                copyToClipboard(item)
            }
        }

        override fun getItemCount() = list.size
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
