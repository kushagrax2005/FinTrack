package com.example.fintrack

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.utils.ColorTemplate
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

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
    private lateinit var rewardsView: View

    private lateinit var rvRewards: RecyclerView
    private lateinit var rewardAdapter: RewardAdapter
    private lateinit var txtUserPoints: TextView
    private lateinit var txtDailyStatus: TextView

    private lateinit var barChart: BarChart
    private lateinit var txtTotalIncome: TextView
    private lateinit var txtTotalExpense: TextView
    private lateinit var txtTopMerchants: TextView

    private lateinit var budgetProgressBar: ProgressBar
    private lateinit var txtBudgetLabel: TextView
    private lateinit var txtBudgetPercent: TextView
    private lateinit var txtBudgetRemaining: TextView
    private lateinit var cardBudget: View

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
        
        monthlyBudget = prefs.getFloat("budget", 20000f).toDouble()

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
        rewardsView = findViewById(R.id.rewardsView)

        rvRewards = findViewById(R.id.rvRewards)
        txtUserPoints = findViewById(R.id.txtUserPoints)
        txtDailyStatus = findViewById(R.id.txtDailyStatus)

        barChart = findViewById(R.id.barChart)
        txtTotalIncome = findViewById(R.id.txtTotalIncome)
        txtTotalExpense = findViewById(R.id.txtTotalExpense)
        txtTopMerchants = findViewById(R.id.txtTopMerchants)

        budgetProgressBar = findViewById(R.id.budgetProgressBar)
        txtBudgetLabel = findViewById(R.id.txtBudgetLabel)
        txtBudgetPercent = findViewById(R.id.txtBudgetPercent)
        txtBudgetRemaining = findViewById(R.id.txtBudgetRemaining)
        cardBudget = findViewById(R.id.cardBudget)

        aiInsightsContainer = findViewById(R.id.aiInsightsContainer)
        txtAiStatus = findViewById(R.id.txtAiStatus)

        repository = (application as FinTrackApp).repository

        setupNavigation()
        setupMonthSpinner()
        setupTransactionsRecyclerView()
        setupRewardsRecyclerView()
        setupPieChart()
        observeTransactions()
        
        findViewById<FloatingActionButton>(R.id.fabAdd).setOnClickListener {
            showAddTransactionDialog()
        }

        btnSearch.setOnClickListener {
            val query = etSearch.text.toString()
            if (query.isNotEmpty()) performSearch(query)
            else {
                // Refresh to current month filter if search cleared
                lifecycleScope.launch {
                    val calendar = selectedDate.value
                    val transactions = repository.allTransactions.first()
                    val filtered = transactions.filter {
                        val transCal = Calendar.getInstance().apply { timeInMillis = it.date }
                        transCal.get(Calendar.MONTH) == calendar.get(Calendar.MONTH) &&
                        transCal.get(Calendar.YEAR) == calendar.get(Calendar.YEAR)
                    }
                    adapter.setTransactions(filtered)
                }
            }
        }

        val btnReadSMS = findViewById<Button>(R.id.btnReadSMS)
        val btnClearData = findViewById<Button>(R.id.btnClearData)

        btnReadSMS.setOnClickListener { checkAndRequestPermissions() }
        btnClearData.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Clear Data")
                .setMessage("Are you sure you want to delete all transactions?")
                .setPositiveButton("Delete All") { _, _ ->
                    lifecycleScope.launch { repository.deleteAll() }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        checkAndRequestPermissions()
    }

    private fun setupNavigation() {
        bottomNav.setOnItemSelectedListener { item ->
            dashboardView.visibility = View.GONE
            transactionsView.visibility = View.GONE
            analyticsView.visibility = View.GONE
            rewardsView.visibility = View.GONE

            when (item.itemId) {
                R.id.nav_dashboard -> dashboardView.visibility = View.VISIBLE
                R.id.nav_transactions -> transactionsView.visibility = View.VISIBLE
                R.id.nav_analytics -> analyticsView.visibility = View.VISIBLE
                R.id.nav_rewards -> {
                    rewardsView.visibility = View.VISIBLE
                    updateRewardsUI()
                }
            }
            true
        }
    }

    private fun setupMonthSpinner() {
        val calendar = Calendar.getInstance()
        val months = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", 
                             "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        
        val spinnerItems = mutableListOf<String>()
        val itemDates = mutableListOf<Calendar>()

        // Generate items for the last 12 months (including current)
        for (i in 0..11) {
            val cal = Calendar.getInstance()
            cal.add(Calendar.MONTH, -i)
            val monthName = months[cal.get(Calendar.MONTH)]
            val year = cal.get(Calendar.YEAR)
            spinnerItems.add("$monthName $year")
            itemDates.add(cal)
        }

        // Custom adapter for better styling
        val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, spinnerItems) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val view = super.getView(position, convertView, parent) as TextView
                view.setTextColor(android.graphics.Color.WHITE)
                view.textSize = 16f
                view.setTypeface(null, android.graphics.Typeface.BOLD)
                return view
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent) as TextView
                view.setPadding(32, 32, 32, 32)
                view.textSize = 16f
                if (position == spinnerMonth.selectedItemPosition) {
                    view.setBackgroundColor(android.graphics.Color.parseColor("#EEEEFF"))
                    view.setTextColor(android.graphics.Color.parseColor("#6C63FF"))
                } else {
                    view.setBackgroundColor(android.graphics.Color.WHITE)
                    view.setTextColor(android.graphics.Color.DKGRAY)
                }
                return view
            }
        }
        
        spinnerMonth.adapter = adapter
        spinnerMonth.setSelection(0) // Default to current month

        spinnerMonth.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedDate.value = itemDates[position]
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupTransactionsRecyclerView() {
        adapter = TransactionAdapter(
            onLongClick = { transaction ->
                showTransactionOptionsDialog(transaction)
            },
            onClick = { transaction ->
                AlertDialog.Builder(this)
                    .setTitle("Transaction Details")
                    .setMessage("Merchant: ${transaction.merchant}\nAmount: ₹${transaction.amount}\nCategory: ${transaction.category}\nDate: ${SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(transaction.date))}\n\n${transaction.body}")
                    .setPositiveButton("OK", null)
                    .show()
            }
        )
        rvTransactions.layoutManager = LinearLayoutManager(this)
        rvTransactions.adapter = adapter
    }

    private fun showTransactionOptionsDialog(transaction: TransactionEntity) {
        val options = arrayOf("Change Category", "Delete Transaction")
        AlertDialog.Builder(this)
            .setTitle("Transaction Options")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showChangeCategoryDialog(transaction)
                    1 -> confirmDeleteTransaction(transaction)
                }
            }
            .show()
    }

    private fun showChangeCategoryDialog(transaction: TransactionEntity) {
        val categories = arrayOf(
            "Food & Dining", "Shopping", "Travel & Transport", "Bills & Utilities",
            "Groceries", "Entertainment", "Health & Wellness", "Investment & Savings",
            "Salary & Income", "Self Transfer", "Others"
        )
        
        AlertDialog.Builder(this)
            .setTitle("Select Category")
            .setItems(categories) { _, which ->
                val newCategory = categories[which]
                lifecycleScope.launch {
                    val updated = transaction.copy(category = newCategory)
                    repository.insertAll(listOf(updated)) // Room handles update by primary key
                    Toast.makeText(this@MainActivity, "Category updated to $newCategory", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun confirmDeleteTransaction(transaction: TransactionEntity) {
        AlertDialog.Builder(this)
            .setTitle("Delete Transaction")
            .setMessage("Are you sure you want to delete this transaction from ${transaction.merchant}?")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    repository.delete(transaction)
                    Toast.makeText(this@MainActivity, "Transaction deleted", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddTransactionDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_add_transaction, null)
        val etMerchant = view.findViewById<EditText>(R.id.etMerchant)
        val etAmount = view.findViewById<EditText>(R.id.etAmount)
        val spinnerCategory = view.findViewById<Spinner>(R.id.spinnerCategory)

        val categories = arrayOf("Food", "Transport", "Shopping", "Entertainment", "Bills", "Health", "Other")
        val catAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categories)
        spinnerCategory.adapter = catAdapter

        AlertDialog.Builder(this)
            .setTitle("Add Manual Expense")
            .setView(view)
            .setPositiveButton("Save") { _, _ ->
                val merchant = etMerchant.text.toString()
                val amount = etAmount.text.toString().toDoubleOrNull() ?: 0.0
                val category = spinnerCategory.selectedItem.toString()
                
                if (merchant.isNotEmpty() && amount > 0) {
                    val transaction = TransactionEntity(
                        merchant = merchant,
                        amount = amount,
                        date = System.currentTimeMillis(),
                        category = category,
                        body = "Manual: $merchant",
                        isDebit = true
                    )
                    lifecycleScope.launch { repository.insert(transaction) }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun observeTransactions() {
        lifecycleScope.launch {
            selectedDate.collectLatest { calendar ->
                repository.allTransactions.collectLatest { transactions ->
                    val filtered = transactions.filter {
                        val transCal = Calendar.getInstance().apply { timeInMillis = it.date }
                        transCal.get(Calendar.MONTH) == calendar.get(Calendar.MONTH) &&
                        transCal.get(Calendar.YEAR) == calendar.get(Calendar.YEAR)
                    }
                    adapter.setTransactions(filtered)
                    updateDashboardUI(filtered)
                    updateAnalyticsUI(transactions)
                }
            }
        }
    }

    private fun performSearch(query: String) {
        lifecycleScope.launch {
            repository.allTransactions.collectLatest { transactions ->
                val searchResults = transactions.filter {
                    it.merchant.contains(query, ignoreCase = true) ||
                    it.category.contains(query, ignoreCase = true) ||
                    it.body.contains(query, ignoreCase = true)
                }
                adapter.setTransactions(searchResults)
                Toast.makeText(this@MainActivity, "Found ${searchResults.size} results", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateDashboardUI(transactions: List<TransactionEntity>) {
        val debits = transactions.filter { it.isDebit }
        // The Friend Case: Exclude friend spends from net dashboard
        val netDebits = debits.filter { 
            !it.body.contains("for friend", ignoreCase = true) && 
            !it.body.contains("friend case", ignoreCase = true)
        }
        
        val totalSpent = netDebits.sumOf { it.amount }
        totalText.text = "₹%.2f".format(totalSpent)

        val categories = netDebits.groupBy { it.category }
            .mapValues { it.value.sumOf { it.amount } }

        updatePieChart(categories)
        
        val categorySummary = categories.entries
            .sortedByDescending { it.value }
            .joinToString("\n") { "${it.key}: ₹%.2f".format(it.value) }
        categoryText.text = if (categorySummary.isEmpty()) "No data yet" else categorySummary

        updateBudgetStatus(totalSpent)
        updateAIInsights(transactions)
    }

    private fun updateBudgetStatus(totalSpent: Double) {
        val remaining = monthlyBudget - totalSpent
        val percent = ((totalSpent / monthlyBudget) * 100).toInt().coerceIn(0, 100)
        
        budgetProgressBar.progress = percent
        txtBudgetLabel.text = "Monthly Budget: ₹%.0f".format(monthlyBudget)
        txtBudgetPercent.text = "$percent%"
        
        if (remaining >= 0) {
            txtBudgetRemaining.text = "₹%.2f remaining".format(remaining)
            txtBudgetRemaining.setTextColor(android.graphics.Color.parseColor("#34A853"))
        } else {
            txtBudgetRemaining.text = "₹%.2f over budget!".format(Math.abs(remaining))
            txtBudgetRemaining.setTextColor(android.graphics.Color.parseColor("#EA4335"))
        }

        val onLongClick = View.OnLongClickListener {
            showEditBudgetDialog()
            true
        }
        cardBudget.setOnLongClickListener(onLongClick)
    }

    private fun showEditBudgetDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_add_transaction, null)
        val etAmount = view.findViewById<EditText>(R.id.etAmount)
        val etMerchant = view.findViewById<EditText>(R.id.etMerchant)
        val spinnerCategory = view.findViewById<Spinner>(R.id.spinnerCategory)
        
        // Hide unused fields for budget edit
        etMerchant.visibility = View.GONE
        spinnerCategory.visibility = View.GONE
        
        etAmount.hint = "Enter Budget (e.g. 25000)"
        etAmount.setText(monthlyBudget.toString())

        AlertDialog.Builder(this)
            .setTitle("Edit Monthly Budget")
            .setView(view)
            .setPositiveButton("Save") { _, _ ->
                val newValue = etAmount.text.toString().toDoubleOrNull()
                if (newValue != null && newValue > 0) {
                    monthlyBudget = newValue
                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putFloat("budget", newValue.toFloat()).apply()
                    // Trigger UI refresh
                    lifecycleScope.launch {
                        val transactions = repository.allTransactions.first()
                        val calendar = selectedDate.value
                        val filtered = transactions.filter {
                            val transCal = Calendar.getInstance().apply { timeInMillis = it.date }
                            transCal.get(Calendar.MONTH) == calendar.get(Calendar.MONTH) &&
                            transCal.get(Calendar.YEAR) == calendar.get(Calendar.YEAR)
                        }
                        updateDashboardUI(filtered)
                        updateRewardsUI() // Update rewards as cost depends on budget
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateAIInsights(transactions: List<TransactionEntity>) {
        val insights = aiAnalyzer.analyze(transactions, monthlyBudget)
        aiInsightsContainer.removeAllViews()
        
        if (insights.isEmpty()) {
            txtAiStatus.text = "Sync transactions to get AI insights!"
        } else {
            txtAiStatus.text = "FinTrack AI Suggestions:"
            insights.forEach { insight ->
                val view = LayoutInflater.from(this).inflate(R.layout.item_ai_insight, aiInsightsContainer, false)
                val textTitle = view.findViewById<TextView>(R.id.txtInsightTitle)
                val textDesc = view.findViewById<TextView>(R.id.txtInsightMessage)
                val textImpact = view.findViewById<TextView>(R.id.txtImpactLabel)
                val impactIndicator = view.findViewById<View>(R.id.impactIndicator)
                
                textTitle.text = "💡 ${insight.title}"
                textDesc.text = insight.description
                textImpact.text = insight.impact.uppercase()
                
                // Color based on impact
                val color = when (insight.impact.lowercase()) {
                    "high" -> android.graphics.Color.parseColor("#FF5252") // Red
                    "medium" -> android.graphics.Color.parseColor("#FFAB00") // Orange/Amber
                    else -> android.graphics.Color.parseColor("#00C853") // Green
                }
                impactIndicator.setBackgroundColor(color)
                textImpact.setTextColor(color)

                aiInsightsContainer.addView(view)
            }
        }
    }

    private fun updateAnalyticsUI(allTransactions: List<TransactionEntity>) {
        val calendar = selectedDate.value
        val currentMonthFiltered = allTransactions.filter {
            val transCal = Calendar.getInstance().apply { timeInMillis = it.date }
            transCal.get(Calendar.MONTH) == calendar.get(Calendar.MONTH) &&
            transCal.get(Calendar.YEAR) == calendar.get(Calendar.YEAR)
        }

        val debits = currentMonthFiltered.filter { it.isDebit }
        // The Friend Case: Exclude friend spends from net analytics
        val netDebits = debits.filter { 
            !it.body.contains("for friend", ignoreCase = true) && 
            !it.body.contains("friend case", ignoreCase = true)
        }
        val credits = currentMonthFiltered.filter { !it.isDebit }
        
        val totalIncome = credits.sumOf { it.amount }
        val totalExpense = netDebits.sumOf { it.amount }
        
        txtTotalIncome.text = "₹%.2f".format(totalIncome)
        txtTotalExpense.text = "₹%.2f".format(totalExpense)
        
        val topMerchants = netDebits.groupBy { it.merchant }
            .mapValues { it.value.sumOf { it.amount } }
            .entries.sortedByDescending { it.value }
            .take(5)
            .joinToString("\n") { "${it.key}: ₹%.2f".format(it.value) }
        
        txtTopMerchants.text = if (topMerchants.isEmpty()) "No data available" else topMerchants
        updateBarChart(currentMonthFiltered)
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

    private fun updatePieChart(categories: Map<String, Double>) {
        if (categories.isEmpty()) {
            pieChart.clear()
            return
        }
        val entries = categories.map { PieEntry(it.value.toFloat(), it.key) }
        val dataSet = PieDataSet(entries, "")
        dataSet.colors = ColorTemplate.MATERIAL_COLORS.toList()
        dataSet.setDrawValues(false) // Remove the numbers hovering over segments
        
        val data = PieData(dataSet)
        pieChart.data = data
        pieChart.invalidate()
    }

    private fun updateBarChart(currentMonthTransactions: List<TransactionEntity>) {
        val calendar = selectedDate.value
        val maxDay = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)

        val entries = (1..maxDay).map { day ->
            val daySum = currentMonthTransactions.filter { 
                val transCal = Calendar.getInstance().apply { timeInMillis = it.date }
                transCal.get(Calendar.DAY_OF_MONTH) == day && it.isDebit &&
                !it.body.contains("for friend", ignoreCase = true) && 
                !it.body.contains("friend case", ignoreCase = true)
            }.sumOf { it.amount }
            
            BarEntry(day.toFloat(), daySum.toFloat())
        }
        
        val dataSet = BarDataSet(entries, "Daily Spend")
        dataSet.color = android.graphics.Color.parseColor("#6C63FF")
        dataSet.setDrawValues(false)
        
        val barData = BarData(dataSet)
        barData.barWidth = 0.8f
        
        barChart.apply {
            data = barData
            description.isEnabled = false
            setFitBars(true)
            setScaleEnabled(true)
            setPinchZoom(true)
            
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                granularity = 1f
                labelCount = 5
                textColor = android.graphics.Color.DKGRAY
                valueFormatter = object : IndexAxisValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return value.toInt().toString()
                    }
                }
            }
            axisLeft.apply {
                setDrawGridLines(true)
                axisMinimum = 0f
                textColor = android.graphics.Color.DKGRAY
            }
            axisRight.isEnabled = false
            legend.isEnabled = false
            animateY(1000)
            invalidate()
        }
    }

    private fun updateRewardsUI() {
        lifecycleScope.launch {
            val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            val userPoints = prefs.getInt("user_points", 0)
            txtUserPoints.text = "⭐ $userPoints pts"

            val today = Calendar.getInstance()
            val startOfDay = today.apply { 
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            
            val transactions = repository.allTransactions.first()
            val todayTransactions = transactions.filter { it.date >= startOfDay && it.isDebit }
            
            val netTodaySpent = todayTransactions.filter { 
                !it.body.contains("for friend", ignoreCase = true) && 
                !it.body.contains("friend case", ignoreCase = true)
            }.sumOf { it.amount }
            
            val dailyBudget = monthlyBudget / 30.0
            val lastAwardedDay = prefs.getLong("last_rewarded_day", 0)
            
            val isSameDay = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date(lastAwardedDay)) == 
                            SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())

            if (!isSameDay) {
                val efficiency = if (dailyBudget > 0) (1.0 - (netTodaySpent / dailyBudget)) else 0.0
                val pointsToGain = (efficiency * 100).toInt()

                val finalPoints = when {
                    netTodaySpent == 0.0 -> 100
                    netTodaySpent > dailyBudget -> 0
                    else -> pointsToGain.coerceAtLeast(10)
                }
                
                if (finalPoints > 0) {
                    val newTotal = userPoints + finalPoints
                    prefs.edit().apply {
                        putInt("user_points", newTotal)
                        putLong("last_rewarded_day", System.currentTimeMillis())
                        apply()
                    }
                    txtUserPoints.text = "⭐ $newTotal pts"
                    Toast.makeText(this@MainActivity, "You earned $finalPoints points today!", Toast.LENGTH_LONG).show()
                } else if (netTodaySpent > dailyBudget) {
                     prefs.edit().putLong("last_rewarded_day", System.currentTimeMillis()).apply()
                }
            }
            
            txtDailyStatus.text = "Daily Budget: ₹%.0f | Net Spend: ₹%.0f".format(dailyBudget, netTodaySpent)
            rewardAdapter.updateRewards(getAvailableRewards())
        }
    }

    private fun updatePointsDisplay() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val userPoints = prefs.getInt("user_points", 0)
        txtUserPoints.text = "⭐ $userPoints pts"
    }

    private fun setupRewardsRecyclerView() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val userPoints = prefs.getInt("user_points", 0)
        
        rewardAdapter = RewardAdapter(getAvailableRewards(), userPoints) { reward ->
            redeemReward(reward)
        }
        rvRewards.layoutManager = LinearLayoutManager(this)
        rvRewards.adapter = rewardAdapter
    }

    private fun getAvailableRewards(): List<Reward> {
        val rewards = listOf(
            Reward("1", "Spotify Premium", "1 Month Individual Plan", 700, "🎧", "SUBSCRIPTION"),
            Reward("2", "Amazon Gift Card", "₹250 Voucher", 2500, "🎁", "GIFT_CARD"),
            Reward("3", "Zomato Pro", "3 Months Membership", 1500, "🍕", "SUBSCRIPTION"),
            Reward("4", "Swiggy Money", "₹100 Cashback", 1000, "🟠", "VOUCHER"),
            Reward("5", "Netflix Card", "₹500 Gift Voucher", 4500, "🎬", "GIFT_CARD"),
            Reward("6", "Starbucks Coffee", "Free Handcrafted Beverage", 800, "☕", "VOUCHER"),
            Reward("7", "BookMyShow", "₹200 Movie Discount", 1200, "🎟️", "VOUCHER"),
            Reward("8", "Uber Credits", "₹150 Trip Discount", 1300, "🚗", "VOUCHER")
        )
        return rewards.map { it.copy(cost = it.getDynamicCost(monthlyBudget)) }
    }

    private fun redeemReward(reward: Reward) {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val userPoints = prefs.getInt("user_points", 0)
        
        if (userPoints >= reward.cost) {
            val newPoints = userPoints - reward.cost
            prefs.edit().putInt("user_points", newPoints).apply()
            
            AlertDialog.Builder(this)
                .setTitle("Reward Redeemed!")
                .setMessage("You have successfully redeemed ${reward.title}. Your coupon code will be sent to your registered email.")
                .setPositiveButton("Awesome", null)
                .show()
            updateRewardsUI()
        } else {
            Toast.makeText(this, "Not enough points!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkAndRequestPermissions() {
        if (checkSelfPermission(Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.READ_SMS), 101)
        } else {
            syncSmsTransactions()
        }
    }

    private fun syncSmsTransactions() {
        lifecycleScope.launch {
            txtAiStatus.text = "Analyzing your SMS for transactions..."
            val smsReader = SmsReader(this@MainActivity)
            val transactions = smsReader.readAllTransactions()
            repository.insertAll(transactions)
            Toast.makeText(this@MainActivity, "Synced ${transactions.size} transactions!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            syncSmsTransactions()
        }
    }
}
