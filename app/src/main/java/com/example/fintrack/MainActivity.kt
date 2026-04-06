package com.example.fintrack

import android.Manifest
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.utils.ColorTemplate

class MainActivity : AppCompatActivity() {

    lateinit var resultText: TextView
    lateinit var readBtn: Button
    lateinit var totalText: TextView
    lateinit var categoryText: TextView
    lateinit var pieChart: PieChart

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        resultText = findViewById(R.id.txtResult)
        readBtn = findViewById(R.id.btnReadSMS)
        totalText = findViewById(R.id.txtTotal)
        categoryText = findViewById(R.id.txtCategories)
        pieChart = findViewById(R.id.pieChart)

        if (checkSelfPermission(Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED) {

            requestPermissions(arrayOf(Manifest.permission.READ_SMS), 1)
        }

        readBtn.setOnClickListener {
            readSMS()
        }
    }

    fun readSMS() {
        try {
            val smsList = StringBuilder()

            var total = 0.0
            var foodTotal = 0.0
            var shoppingTotal = 0.0
            var rechargeTotal = 0.0
            var travelTotal = 0.0
            var walletTotal = 0.0
            var otherTotal = 0.0

            val cursor: Cursor? = contentResolver.query(
                Uri.parse("content://sms/inbox"),
                null,
                null,
                null,
                null
            )

            if (cursor != null && cursor.moveToFirst()) {

                do {
                    val body = cursor.getString(cursor.getColumnIndexOrThrow("body"))

                    // ✅ FILTER VALID TRANSACTIONS ONLY
                    if (!isTransactionSMS(body)) continue

                    val amount = extractAmount(body) ?: continue
                    if (amount <= 1) continue   // ignore noise

                    val category = getCategory(body)
                    val merchant = extractMerchant(body)

                    total += amount

                    when (category) {
                        "Food 🍔" -> foodTotal += amount
                        "Shopping 🛍" -> shoppingTotal += amount
                        "Recharge 📱" -> rechargeTotal += amount
                        "Travel 🚗" -> travelTotal += amount
                        "Wallet 💳" -> walletTotal += amount
                        else -> otherTotal += amount
                    }

                    smsList.append("$category - ₹%.2f - $merchant\n\n".format(amount))

                } while (cursor.moveToNext())

                cursor.close()

                // 💰 TOTAL
                totalText.text = "₹%.2f".format(total)

                // 📊 CATEGORY
                val summary = """
                    Food 🍔: ₹%.2f
                    Shopping 🛍: ₹%.2f
                    Recharge 📱: ₹%.2f
                    Travel 🚗: ₹%.2f
                    Wallet 💳: ₹%.2f
                    Others 📦: ₹%.2f
                """.trimIndent().format(
                    foodTotal,
                    shoppingTotal,
                    rechargeTotal,
                    travelTotal,
                    walletTotal,
                    otherTotal
                )

                categoryText.text = summary
                resultText.text = smsList.toString()

                setupPieChart(
                    foodTotal,
                    shoppingTotal,
                    rechargeTotal,
                    travelTotal,
                    walletTotal,
                    otherTotal
                )
            }

        } catch (e: Exception) {
            resultText.text = "Error: ${e.message}"
        }
    }

    // ✅ FILTER FUNCTION
    fun isTransactionSMS(msg: String): Boolean {
        val lower = msg.lowercase()

        return (lower.contains("debited") ||
                lower.contains("credited") ||
                lower.contains("sent") ||
                lower.contains("paid") ||
                lower.contains("recharge")) &&
                (lower.contains("rs") || lower.contains("inr"))
    }

    // ✅ FIXED AMOUNT EXTRACTION
    fun extractAmount(msg: String): Double? {

        val regex = Regex("(Rs\\.?|INR)\\s?(\\d{1,6}(\\.\\d{1,2})?)")
        val match = regex.find(msg)

        return try {
            match?.groups?.get(2)?.value?.toDouble()
        } catch (e: Exception) {
            null
        }
    }

    // 🏷 CATEGORY
    fun getCategory(msg: String): String {
        val lower = msg.lowercase()

        return when {
            lower.contains("zomato") || lower.contains("swiggy") -> "Food 🍔"
            lower.contains("amazon") || lower.contains("flipkart") -> "Shopping 🛍"
            lower.contains("paytm") -> "Wallet 💳"
            lower.contains("airtel") -> "Recharge 📱"
            lower.contains("uber") || lower.contains("ola") -> "Travel 🚗"
            else -> "Others 📦"
        }
    }

    // 🏪 MERCHANT
    fun extractMerchant(msg: String): String {
        val words = msg.split(" ")

        for (word in words) {
            if (word.contains("zomato", true) ||
                word.contains("swiggy", true) ||
                word.contains("amazon", true) ||
                word.contains("flipkart", true) ||
                word.contains("paytm", true) ||
                word.contains("airtel", true) ||
                word.contains("uber", true) ||
                word.contains("ola", true)) {

                return word
            }
        }
        return "Unknown"
    }

    // 📊 PIE CHART
    fun setupPieChart(
        food: Double,
        shopping: Double,
        recharge: Double,
        travel: Double,
        wallet: Double,
        other: Double
    ) {

        val entries = ArrayList<PieEntry>()

        if (food > 0) entries.add(PieEntry(food.toFloat(), "Food"))
        if (shopping > 0) entries.add(PieEntry(shopping.toFloat(), "Shopping"))
        if (recharge > 0) entries.add(PieEntry(recharge.toFloat(), "Recharge"))
        if (travel > 0) entries.add(PieEntry(travel.toFloat(), "Travel"))
        if (wallet > 0) entries.add(PieEntry(wallet.toFloat(), "Wallet"))
        if (other > 0) entries.add(PieEntry(other.toFloat(), "Others"))

        val dataSet = PieDataSet(entries, "Expenses")
        dataSet.colors = ColorTemplate.MATERIAL_COLORS.toList()

        val data = PieData(dataSet)
        data.setValueTextSize(14f)

        pieChart.data = data
        pieChart.description.isEnabled = false
        pieChart.centerText = "Expenses"
        pieChart.animateY(1000)
        pieChart.invalidate()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 1 && grantResults.isNotEmpty()
            && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

            readSMS()
        }
    }
}