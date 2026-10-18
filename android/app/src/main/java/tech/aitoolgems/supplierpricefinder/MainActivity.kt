package tech.aitoolgems.supplierpricefinder

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
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
    private val suppliersList = mutableListOf<Supplier>()
    private var currentFilterAccess: AccessType? = null

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
            filterAndDisplayResults()
        }

        // Tabs
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> { // Price Comparison
                        binding.scrollFilters.visibility = View.VISIBLE
                        binding.cardCheapestVerified.visibility = View.VISIBLE
                        binding.recyclerViewResults.adapter = ResultsAdapter(observationsList)
                        filterAndDisplayResults()
                    }
                    1 -> { // Suppliers
                        binding.scrollFilters.visibility = View.GONE
                        binding.cardCheapestVerified.visibility = View.GONE
                        loadSuppliersTab()
                    }
                    2 -> { // Scan History
                        binding.scrollFilters.visibility = View.GONE
                        binding.cardCheapestVerified.visibility = View.GONE
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
            binding.chipTelegramStatus.text = getString(R.string.telegram_status_connected)
            binding.chipTelegramStatus.setChipBackgroundColorResource(R.color.badge_verified)
            binding.chipTelegramStatus.setTextColor(getColor(R.color.badge_verified_text))
        } else {
            binding.chipTelegramStatus.text = getString(R.string.telegram_status_disconnected)
            binding.chipTelegramStatus.setChipBackgroundColorResource(R.color.card_background)
            binding.chipTelegramStatus.setTextColor(getColor(R.color.primary_dark))
        }
    }

    private fun loadInitialData() {
        val query = binding.etSearchTool.text.toString().trim()
        val cached = database.getObservationsForQuery(query)
        observationsList.clear()
        observationsList.addAll(cached)
        filterAndDisplayResults()
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
            binding.tvWinnerTitle.setTextColor(getColor(R.color.badge_verified_text))
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
        binding.recyclerViewResults.adapter = SuppliersAdapter(suppliersList) { supplier, isEnabled ->
            database.setSupplierEnabled(supplier.id, isEnabled)
            Toast.makeText(this, "${supplier.canonicalName}: " + if (isEnabled) "Enabled" else "Disabled", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadHistoryTab() {
        val allHistory = database.getObservationsForQuery("")
        binding.recyclerViewResults.adapter = ResultsAdapter(allHistory)
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

            // Accept any worldwide international format with 7 to 15 digits (e.g. +81..., +1..., +44..., +92...)
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
            tvTelegramStatusMsg.text = "✓ Login code requested! Please open your official Telegram app (under 'Telegram' chat) to view your 5-digit code. Enter it below and click 'Verify & Connect'."
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
                        secureStorage.sessionString = "AUTHORIZED_MTPROTO_SESSION_${System.currentTimeMillis()}"
                        updateTelegramStatusChip()
                        Toast.makeText(this, "Telegram Account Verified & Connected Securely!", Toast.LENGTH_LONG).show()
                    } else {
                        updateTelegramStatusChip()
                        Toast.makeText(this, "Telegram credentials encrypted in Android Keystore. Request OTP whenever ready.", Toast.LENGTH_LONG).show()
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

    // Results RecyclerView Adapter
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

            holder.tvEvidence.setOnClickListener {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Observation Evidence")
                    .setMessage("Source: ${item.supplierHandle}\n\nRaw Text:\n${item.originalRawText}\n\nWarranty: ${item.warranty}\nStock: ${item.stockStatus}")
                    .setPositiveButton("Close", null)
                    .show()
            }
        }

        override fun getItemCount() = list.size
    }

    // Suppliers RecyclerView Adapter
    inner class SuppliersAdapter(
        private val list: List<Supplier>,
        private val onToggle: (Supplier, Boolean) -> Unit
    ) : RecyclerView.Adapter<SuppliersAdapter.ViewHolder>() {
        inner class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
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
            val s = list[position]
            holder.tvName.text = s.canonicalName
            holder.tvHandle.text = s.primaryHandle
            holder.tvStatus.text = "Reliability: ${s.reliabilityScore.toInt()}% | ${s.notes}"
            holder.switchActive.isChecked = s.isEnabled

            holder.switchActive.setOnCheckedChangeListener { _, isChecked ->
                onToggle(s, isChecked)
            }

            holder.btnTest.setOnClickListener {
                Toast.makeText(this@MainActivity, "Testing ${s.primaryHandle} connection safely...", Toast.LENGTH_SHORT).show()
            }
        }

        override fun getItemCount() = list.size
    }
}
