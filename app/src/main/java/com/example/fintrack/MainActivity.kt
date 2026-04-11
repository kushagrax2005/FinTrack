package com.example.fintrack

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.utils.ColorTemplate
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*
import com.github.mikephil.charting.components.XAxis

class MainActivity : AppCompatActivity() {

    private lateinit var totalText: TextView
    private lateinit var categoryText: TextView
    private lateinit var pieChart: PieChart
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var repository: TransactionRepository
    private lateinit var spinnerMonth: Spinner
    private lateinit var etSearch: EditText
    private lateinit var btnSearch: ImageButton
    private lateinit var rvTransactions: RecyclerView
    private lateinit var adapter: TransactionAdapter
    
    private lateinit var dashboardView: View
    private lateinit var transactionsView: View
    private lateinit var analyticsView: View

    private lateinit var barChart: BarChart
    private lateinit var txtTotalIncome: TextView
    private lateinit var txtTotalExpense: TextView
    private lateinit var txtTopMerchants: TextView

    private lateinit var budgetProgressBar: ProgressBar
    private lateinit var txtBudgetLabel: TextView
    private lateinit var txtBudgetPercent: TextView
    private lateinit var txtBudgetRemaining: TextView

    private lateinit var aiInsightsContainer: LinearLayout
    private lateinit var txtAiStatus: TextView
    private val aiAnalyzer = AiAnalyzer()
    private var isUnlocked = false
    
    private var monthlyBudget = 20000.0
    private val PREFS_NAME = "FinTrackPrefs"

    private val selectedDate = kotlinx.coroutines.flow.MutableStateFlow(Calendar.getInstance())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Load Prefs
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val registeredIncome = prefs.getFloat("monthly_income", 0f).toDouble()
        
        // If income was set during registration and no manual budget is set, use it as budget
        monthlyBudget = if (registeredIncome > 0 && !prefs.contains("budget")) {
            registeredIncome
        } else {
            prefs.getFloat("budget", 20000f).toDouble()
        }

        val userName = prefs.getString("user_name", "User")
        Toast.makeText(this, "Welcome back, $userName!", Toast.LENGTH_SHORT).show()

        // Initialize UI
        totalText = findViewById(R.id.txtTotal)
        categoryText = findViewById(R.id.txtCategories)
        pieChart = findViewById(R.id.pieChart)
        bottomNav = findViewById(R.id.bottom_navigation)
        spinnerMonth = findViewById(R.id.spinnerMonth)
        etSearch = findViewById(R.id.etSearch)
        btnSearch = findViewById(R.id.btnSearch)
        rvTransactions = findViewById(R.id.rvTransactions)
        
        dashboardView = findViewById(R.id.dashboardView)
        transactionsView = findViewById(R.id.transactionsView)
        analyticsView = findViewById(R.id.analyticsView)

        barChart = findViewById(R.id.barChart)
        txtTotalIncome = findViewById(R.id.txtTotalIncome)
        txtTotalExpense = findViewById(R.id.txtTotalExpense)
        txtTopMerchants = findViewById(R.id.txtTopMerchants)

        budgetProgressBar = findViewById(R.id.budgetProgressBar)
        txtBudgetLabel = findViewById(R.id.txtBudgetLabel)
        txtBudgetPercent = findViewById(R.id.txtBudgetPercent)
        txtBudgetRemaining = findViewById(R.id.txtBudgetRemaining)

        aiInsightsContainer = findViewById(R.id.aiInsightsContainer)
        txtAiStatus = findViewById(R.id.txtAiStatus)

        val refreshBtn: Button = findViewById(R.id.btnReadSMS)
        val clearBtn: Button = findViewById(R.id.btnClearData)

        // Initialize Database & Repository
        val db = AppDatabase.getDatabase(this)
        repository = TransactionRepository(db.transactionDao())

        if (SecurityUtils.canAuthenticate(this) && !isUnlocked) {
            dashboardView.visibility = View.GONE
            transactionsView.visibility = View.GONE
            analyticsView.visibility = View.GONE
            bottomNav.visibility = View.GONE
            
            SecurityUtils.showBiometricPrompt(
                activity = this,
                onSuccess = {
                    isUnlocked = true
                    dashboardView.visibility = View.VISIBLE
                    bottomNav.visibility = View.VISIBLE
                    setupUI()
                },
                onError = { error ->
                    Toast.makeText(this, "Authentication required: $error", Toast.LENGTH_LONG).show()
                    // Optionally close app or show a "Retry" button
                }
            )
        } else {
            isUnlocked = true
            setupUI()
        }

        refreshBtn.setOnClickListener {
            checkAndRequestPermissions()
        }

        clearBtn.setOnClickListener {
            lifecycleScope.launch {
                repository.deleteAll()
                Toast.makeText(this@MainActivity, "All data cleared. Please sync again.", Toast.LENGTH_SHORT).show()
            }
        }

        btnSearch.setOnClickListener {
            val query = etSearch.text.toString()
            if (query.isNotEmpty()) {
                performSearch(query)
            } else {
                // Reset to show current month's transactions
                observeTransactions()
            }
        }

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> {
                    dashboardView.visibility = View.VISIBLE
                    transactionsView.visibility = View.GONE
                    analyticsView.visibility = View.GONE
                    true
                }
                R.id.nav_transactions -> {
                    dashboardView.visibility = View.GONE
                    transactionsView.visibility = View.VISIBLE
                    analyticsView.visibility = View.GONE
                    true
                }
                R.id.nav_analytics -> {
                    dashboardView.visibility = View.GONE
                    transactionsView.visibility = View.GONE
                    analyticsView.visibility = View.VISIBLE
                    true
                }
                else -> false
            }
        }
    }

    private fun setupUI() {
        setupMonthSpinner()
        setupRecyclerView()
        setupPieChart()
        observeTransactions()
    }

    private fun setupMonthSpinner() {
        val months = mutableListOf<String>()
        val cal = Calendar.getInstance()
        val sdf = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        
        // Add last 12 months
        for (i in 0..11) {
            months.add(sdf.format(cal.time))
            cal.add(Calendar.MONTH, -1)
        }
        
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, months)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerMonth.adapter = adapter
        
        spinnerMonth.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedStr = months[position]
                val date = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).parse(selectedStr)
                val cal = Calendar.getInstance().apply { time = date ?: Date() }
                selectedDate.value = cal
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupRecyclerView() {
        adapter = TransactionAdapter(onLongClick = { transaction ->
            showTransactionOptions(transaction)
        })
        rvTransactions.layoutManager = LinearLayoutManager(this)
        rvTransactions.adapter = adapter
    }

    private val CATEGORIES = arrayOf(
        "Food & Dining", "Shopping", "Travel & Transport", "Bills & Utilities",
        "Groceries", "Entertainment", "Health & Wellness", "Investment & Savings",
        "Salary & Income", "Self Transfer", "Others"
    )

    private fun showTransactionOptions(transaction: TransactionEntity) {
        val options = mutableListOf<String>()
        options.add("Change Category")
        options.add("Delete")

        AlertDialog.Builder(this)
            .setTitle("Transaction Options")
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    "Change Category" -> showCategoryPicker(transaction)
                    "Delete" -> deleteTransaction(transaction)
                }
            }
            .show()
    }

    private fun showCategoryPicker(transaction: TransactionEntity) {
        AlertDialog.Builder(this)
            .setTitle("Select Category")
            .setItems(CATEGORIES) { _, which ->
                val newCategory = CATEGORIES[which]
                updateTransactionCategory(transaction, newCategory)
            }
            .show()
    }

    private fun updateTransactionCategory(transaction: TransactionEntity, newCategory: String) {
        lifecycleScope.launch {
            val updated = transaction.copy(category = newCategory)
            repository.insert(updated)
            Toast.makeText(this@MainActivity, "Category updated to $newCategory", Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteTransaction(transaction: TransactionEntity) {
        AlertDialog.Builder(this)
            .setTitle("Delete Transaction")
            .setMessage("Are you sure you want to delete this transaction?")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    repository.delete(transaction)
                    Toast.makeText(this@MainActivity, "Deleted", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = permissions.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        
        if (missing.isNotEmpty()) {
            requestPermissions(missing.toTypedArray(), 101)
        } else {
            syncSms()
        }
    }

    private fun syncSms() {
        val statusToast = Toast.makeText(this, "AI Syncing started (History Mode)...", Toast.LENGTH_SHORT)
        statusToast.show()
        
        lifecycleScope.launch {
            val oneYearAgo = System.currentTimeMillis() - (365L * 24 * 60 * 60 * 1000)
            val allMessages = readSmsInbox()
            
            // 1. Get existing transaction bodies to avoid duplicate AI calls
            val existingBodies = repository.allTransactions.first().map { it.body }.toSet()
            
            // 2. Filter for LIKELY transactions that are NOT yet in the database
            val transactionKeywords = listOf("rs", "inr", "debited", "credited", "sent", "paid", "received", "spent", "upi", "vpa", "amt", "spent", "withdrawal", "transfer")
            val likelyTransactions = allMessages.filter { msg ->
                msg.date > oneYearAgo && 
                !existingBodies.contains(msg.body) &&
                transactionKeywords.any { msg.body.contains(it, ignoreCase = true) }
            }.take(500) // Increased to 500 to cover more historical data
            
            if (likelyTransactions.isEmpty()) {
                txtAiStatus.text = "No new transaction-like SMS found."
                Toast.makeText(this@MainActivity, "Database is already up to date.", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val transactions = mutableListOf<TransactionEntity>()
            val batchSize = 15 // Increased batch size for speed
            val totalBatches = Math.ceil(likelyTransactions.size.toDouble() / batchSize).toInt()
            
            for (i in likelyTransactions.indices step batchSize) {
                val end = (i + batchSize).coerceAtMost(likelyTransactions.size)
                val batch = likelyTransactions.subList(i, end)
                
                txtAiStatus.text = "Analyzing History: Batch ${i/batchSize + 1} of $totalBatches..."
                
                try {
                    val batchResults = aiAnalyzer.categorizeBatchWithAi(batch.map { it.body })
                    
                    // Map results back to original messages safely
                    for (index in batch.indices) {
                        val aiResult = if (index < batchResults.size) batchResults[index] else aiAnalyzer.analyzeSmsLocally(batch[index].body)
                        
                        if (aiResult.isTransaction) {
                            val msg = batch[index]
                            val entity = TransactionEntity(
                                amount = aiResult.amount,
                                merchant = aiResult.merchant,
                                category = aiResult.category,
                                date = msg.date,
                                body = msg.body,
                                isDebit = aiResult.type == "DEBIT"
                            )
                            
                            // 🛑 De-duplicate during history sync (especially useful if multiple banks report same spent amount)
                            val isDup = transactions.any { it.amount == entity.amount && it.isDebit == entity.isDebit && Math.abs(it.date - entity.date) < 300_000 }
                            if (!isDup) {
                                transactions.add(entity)
                            }
                        }
                    }
                    
                    // Periodically save to DB so user sees progress
                    if (transactions.size >= 30) {
                        repository.insertAll(transactions.toList())
                        transactions.clear()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("FinTrack", "Batch failed, skipping: ${e.message}")
                }
            }
            
            if (transactions.isNotEmpty()) {
                repository.insertAll(transactions)
            }
            
            txtAiStatus.text = "AI Sync Complete!"
            Toast.makeText(this@MainActivity, "History synced successfully!", Toast.LENGTH_LONG).show()
        }
    }

    private fun readSmsInbox(): List<SmsData> {
        val smsList = mutableListOf<SmsData>()
        val cursor = contentResolver.query(
            android.net.Uri.parse("content://sms/inbox"),
            null, null, null, "date DESC"
        )

        cursor?.use {
            val bodyIdx = it.getColumnIndex("body")
            val dateIdx = it.getColumnIndex("date")
            while (it.moveToNext()) {
                val body = it.getString(bodyIdx)
                val date = it.getLong(dateIdx)
                smsList.add(SmsData(body, date))
            }
        }
        return smsList
    }

    private fun performSearch(query: String) {
        lifecycleScope.launch {
            Toast.makeText(this@MainActivity, "AI searching...", Toast.LENGTH_SHORT).show()
            
            // Get ALL transactions from repository for searching across history
            repository.allTransactions.first().let { all ->
                val filtered = aiAnalyzer.searchTransactions(query, all)
                
                if (filtered.isNotEmpty()) {
                    adapter.setTransactions(filtered)
                    Toast.makeText(this@MainActivity, "Found ${filtered.size} matches", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, "No matching transactions found.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun observeTransactions() {
        lifecycleScope.launch {
            kotlinx.coroutines.flow.combine(
                repository.allTransactions,
                selectedDate
            ) { transactions, date ->
                Pair(transactions, date)
            }.collectLatest { (transactions, date) ->
                updateUI(transactions, date)
            }
        }
    }

    private fun updateUI(transactions: List<TransactionEntity>, date: Calendar) {
        val targetMonth = date.get(Calendar.MONTH)
        val targetYear = date.get(Calendar.YEAR)
        
        // Optimized filtering
        val tempCal = Calendar.getInstance()
        val monthTransactions = transactions.filter { t ->
            tempCal.timeInMillis = t.date
            tempCal.get(Calendar.MONTH) == targetMonth &&
            tempCal.get(Calendar.YEAR) == targetYear
        }

        updateDashboard(monthTransactions)
        adapter.setTransactions(monthTransactions)
        updateAnalytics(transactions, date)
        updateAiInsights(monthTransactions)
    }

    private fun updateAiInsights(monthTransactions: List<TransactionEntity>) {
        lifecycleScope.launch {
            val insights = aiAnalyzer.getAiInsights(monthTransactions, monthlyBudget)
            
            // Clear previous insights except status text
            val childCount = aiInsightsContainer.childCount
            if (childCount > 1) {
                aiInsightsContainer.removeViews(1, childCount - 1)
            }

            if (insights.isEmpty()) {
                if (monthTransactions.isEmpty()) {
                    txtAiStatus.text = "Sync transactions to get AI insights!"
                } else {
                    txtAiStatus.text = "Analyzing your spending patterns..."
                }
                return@launch
            }

            txtAiStatus.text = "FinTrack AI Suggestions:"
            
            insights.forEach { insight ->
                val view = layoutInflater.inflate(R.layout.item_ai_insight, aiInsightsContainer, false)
                val textTitle = view.findViewById<TextView>(R.id.txtInsightTitle)
                val textMessage = view.findViewById<TextView>(R.id.txtInsightMessage)
                val textImpact = view.findViewById<TextView>(R.id.txtImpactLabel)
                val indicator = view.findViewById<View>(R.id.impactIndicator)
                
                textTitle.text = "💡 ${insight.title}"
                textMessage.text = insight.message
                textImpact.text = insight.impact.uppercase()

                // Dynamic Color based on impact
                val color = when (insight.impact.lowercase()) {
                    "high" -> "#FF5252" // Red
                    "medium" -> "#FFAB40" // Orange
                    else -> "#6C63FF" // Theme Purple for Low
                }
                
                indicator.setBackgroundColor(android.graphics.Color.parseColor(color))
                textImpact.setTextColor(android.graphics.Color.parseColor(color))
                
                aiInsightsContainer.addView(view)
            }
        }
    }

    private fun updateDashboard(monthTransactions: List<TransactionEntity>) {
        var totalSpent = 0.0
        val debitTotals = mutableMapOf<String, Double>()
        val creditTotals = mutableMapOf<String, Double>()

        for (t in monthTransactions) {
            if (t.category == "Self Transfer") continue
            if (t.isDebit) {
                totalSpent += t.amount
                debitTotals[t.category] = (debitTotals[t.category] ?: 0.0) + t.amount
            } else {
                creditTotals[t.category] = (creditTotals[t.category] ?: 0.0) + t.amount
            }
        }

        totalText.text = "₹%.2f".format(totalSpent)
        totalText.setTextColor(android.graphics.Color.WHITE)
        
        updateBudgetUI(totalSpent)
        
        val summaryLines = mutableListOf<String>()
        if (creditTotals.isNotEmpty()) {
            summaryLines.add("Income:")
            creditTotals.forEach { (cat, amt) -> summaryLines.add("  🔺 $cat: ₹%.2f".format(amt)) }
            summaryLines.add("")
        }
        if (debitTotals.isNotEmpty()) {
            summaryLines.add("Expenses:")
            debitTotals.forEach { (cat, amt) -> summaryLines.add("  🔻 $cat: ₹%.2f".format(amt)) }
        }

        categoryText.text = if (summaryLines.isEmpty()) "No data this month" else summaryLines.joinToString("\n")

        updatePieChart(debitTotals)
    }

    private fun updateSavingsUI(monthTransactions: List<TransactionEntity>) {
        // Savings feature removed as requested
    }

    private fun updateBudgetUI(totalSpent: Double) {
        val remaining = monthlyBudget - totalSpent
        val percent = if (monthlyBudget > 0) ((totalSpent / monthlyBudget) * 100).toInt().coerceIn(0, 100) else 0

        txtBudgetLabel.text = "Monthly Budget: ₹%.0f".format(monthlyBudget)
        txtBudgetPercent.text = "$percent%"
        budgetProgressBar.progress = percent

        if (remaining >= 0) {
            txtBudgetRemaining.text = "₹%.2f remaining".format(remaining)
            txtBudgetRemaining.setTextColor(android.graphics.Color.parseColor("#00C853"))
        } else {
            txtBudgetRemaining.text = "₹%.2f over budget!".format(Math.abs(remaining))
            txtBudgetRemaining.setTextColor(android.graphics.Color.parseColor("#FF5252"))
        }

        findViewById<View>(R.id.budgetProgressBar).parent.let { 
            if (it is View) {
                it.setOnClickListener {
                    showEditGoalDialog("Monthly Budget", monthlyBudget) { newValue ->
                        monthlyBudget = newValue
                        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putFloat("budget", newValue.toFloat()).apply()
                        observeTransactions() // Refresh
                    }
                }
            }
        }
    }

    private fun showEditGoalDialog(title: String, currentValue: Double, onConfirm: (Double) -> Unit) {
        val input = EditText(this)
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        input.setText(currentValue.toInt().toString())
        input.setSelection(input.text.length)

        AlertDialog.Builder(this)
            .setTitle("Set $title")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newValue = input.text.toString().toDoubleOrNull() ?: currentValue
                onConfirm(newValue)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateAnalytics(transactions: List<TransactionEntity>, date: Calendar) {
        // 1. Calculate Monthly Income vs Expense
        var totalIncome = 0.0
        var totalExpense = 0.0
        val monthTransactions = transactions.filter { t ->
            val cal = Calendar.getInstance().apply { timeInMillis = t.date }
            cal.get(Calendar.MONTH) == date.get(Calendar.MONTH) &&
            cal.get(Calendar.YEAR) == date.get(Calendar.YEAR)
        }

        for (t in monthTransactions) {
            if (t.category == "Self Transfer") continue

            if (t.isDebit) totalExpense += t.amount
            else totalIncome += t.amount
        }

        txtTotalIncome.text = "₹%.2f".format(totalIncome)
        txtTotalExpense.text = "₹%.2f".format(totalExpense)

        // 2. Top Merchants (Debits only)
        val merchantTotals = monthTransactions
            .filter { it.isDebit }
            .groupBy { it.merchant }
            .mapValues { entry -> entry.value.sumOf { it.amount } }
            .toList()
            .sortedByDescending { it.second }
            .take(5)

        val merchantText = if (merchantTotals.isEmpty()) {
            "No spending data"
        } else {
            merchantTotals.joinToString("\n") { (name, amount) ->
                "$name: ₹%.2f".format(amount)
            }
        }
        txtTopMerchants.text = merchantText

        // 3. Bar Chart for daily spending trend
        setupBarChart(monthTransactions, date)
    }

    private fun setupBarChart(transactions: List<TransactionEntity>, date: Calendar) {
        val daysInMonth = date.getActualMaximum(Calendar.DAY_OF_MONTH)
        val dailySpends = DoubleArray(daysInMonth + 1)

        for (t in transactions) {
            if (t.isDebit) {
                val cal = Calendar.getInstance().apply { timeInMillis = t.date }
                val day = cal.get(Calendar.DAY_OF_MONTH)
                if (day <= daysInMonth) {
                    dailySpends[day] += t.amount
                }
            }
        }

        val entries = mutableListOf<BarEntry>()
        for (i in 1..daysInMonth) {
            entries.add(BarEntry(i.toFloat(), dailySpends[i].toFloat()))
        }

        val dataSet = BarDataSet(entries, "Daily Spending")
        dataSet.color = android.graphics.Color.parseColor("#6C63FF")
        dataSet.valueTextColor = android.graphics.Color.BLACK
        dataSet.valueTextSize = 0f // Hide values to keep it clean

        val barData = BarData(dataSet)
        barChart.data = barData
        
        barChart.apply {
            description.isEnabled = false
            setFitBars(true)
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                granularity = 1f
                labelCount = 5
            }
            axisLeft.apply {
                setDrawGridLines(true)
                axisMinimum = 0f
            }
            axisRight.isEnabled = false
            legend.isEnabled = false
            animateY(1000)
            invalidate()
        }
    }

    private fun setupPieChart() {
        pieChart.apply {
            description.isEnabled = false
            isDrawHoleEnabled = true
            setHoleColor(android.graphics.Color.TRANSPARENT)
            setTransparentCircleColor(android.graphics.Color.WHITE)
            setTransparentCircleAlpha(110)
            holeRadius = 58f
            transparentCircleRadius = 61f
            setDrawCenterText(true)
            centerText = "Month Spend"
            setCenterTextSize(16f)
            setEntryLabelColor(android.graphics.Color.BLACK)
            setEntryLabelTextSize(0f)
            animateY(1000)
        }
    }

    private fun updatePieChart(categoryTotals: Map<String, Double>) {
        if (categoryTotals.isEmpty()) {
            pieChart.clear()
            return
        }
        val entries = categoryTotals.map { PieEntry(it.value.toFloat(), it.key) }
        val dataSet = PieDataSet(entries, "")
        
        val colors = listOf(
            android.graphics.Color.parseColor("#1A73E8"),
            android.graphics.Color.parseColor("#34A853"),
            android.graphics.Color.parseColor("#FBBC04"),
            android.graphics.Color.parseColor("#EA4335"),
            android.graphics.Color.parseColor("#A142F4"),
            android.graphics.Color.parseColor("#24C1E0")
        )
        
        dataSet.colors = colors
        dataSet.sliceSpace = 3f
        dataSet.selectionShift = 5f
        
        val data = PieData(dataSet)
        data.setValueTextSize(0f)
        
        pieChart.data = data
        pieChart.invalidate()
    }

    data class SmsData(val body: String, val date: Long)
}
