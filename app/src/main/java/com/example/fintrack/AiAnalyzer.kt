package com.example.fintrack

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

class AiAnalyzer {

    // IMPORTANT: Replace with your actual API key from https://aistudio.google.com/
    private val apiKey = "AIzaSyD_p-14kGUFGmzrHuiIc84Me5Kw01Jz8s4"
    
    private val model = GenerativeModel(
        modelName = "gemini-1.5-flash",
        apiKey = apiKey,
        generationConfig = generationConfig {
            responseMimeType = "application/json"
        }
    )

    data class Insight(
        val title: String,
        val message: String,
        val impact: String // "High", "Medium", "Low"
    )

    data class AiTransaction(
        val isTransaction: Boolean,
        val type: String = "DEBIT",
        val amount: Double = 0.0,
        val merchant: String = "Unknown",
        val category: String = "Others"
    )

    suspend fun getAiInsights(transactions: List<TransactionEntity>, monthlyBudget: Double): List<Insight> = withContext(Dispatchers.IO) {
        val prompt = buildPrompt(transactions, monthlyBudget)
        try {
            val response = model.generateContent(prompt)
            val text = response.text ?: run {
                android.util.Log.e("AiAnalyzer", "Insights AI returned empty. Reason: ${response.candidates.firstOrNull()?.finishReason}")
                return@withContext analyzeLocally(transactions, monthlyBudget)
            }
            parseAiResponse(text)
        } catch (e: Exception) {
            android.util.Log.e("AiAnalyzer", "Insights Error: ${e.message}")
            analyzeLocally(transactions, monthlyBudget)
        }
    }

    suspend fun searchTransactions(query: String, allTransactions: List<TransactionEntity>): List<TransactionEntity> = withContext(Dispatchers.IO) {
        val prompt = """
            User Query: "$query"
            Available Categories: [Food & Dining, Shopping, Travel & Transport, Bills & Utilities, Groceries, Entertainment, Health & Wellness, Investment & Savings, Salary & Income, Self Transfer, Others]
            
            Task: Translate the user's natural language search query into a JSON filter object.
            
            Response Format (Strict JSON):
            {
              "category": "category_name_or_null",
              "merchant": "merchant_name_or_null",
              "minAmount": number_or_0,
              "maxAmount": number_or_0,
              "type": "DEBIT_or_CREDIT_or_null"
            }
        """.trimIndent()

        try {
            val response = model.generateContent(prompt)
            val jsonStr = response.text?.trim()?.removeSurrounding("```json", "```")?.trim() ?: return@withContext emptyList()
            
            val merchant = Regex("\"merchant\":\\s*\"(.*?)\"").find(jsonStr)?.groupValues?.get(1)?.takeIf { it != "null" }
            val category = Regex("\"category\":\\s*\"(.*?)\"").find(jsonStr)?.groupValues?.get(1)?.takeIf { it != "null" }
            val type = Regex("\"type\":\\s*\"(.*?)\"").find(jsonStr)?.groupValues?.get(1)?.takeIf { it != "null" }
            val minAmount = Regex("\"minAmount\":\\s*([\\d.]+)").find(jsonStr)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
            val maxAmount = Regex("\"maxAmount\":\\s*([\\d.]+)").find(jsonStr)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0

            allTransactions.filter { t ->
                val merchantMatch = merchant == null || t.merchant.contains(merchant, ignoreCase = true)
                val categoryMatch = category == null || t.category.equals(category, ignoreCase = true)
                val typeMatch = type == null || (if (type == "DEBIT") t.isDebit else !t.isDebit)
                val amountMatch = (minAmount == 0.0 || t.amount >= minAmount) && (maxAmount == 0.0 || t.amount <= maxAmount)
                
                merchantMatch && categoryMatch && typeMatch && amountMatch
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun categorizeBatchWithAi(smsMessages: List<String>): List<AiTransaction> = withContext(Dispatchers.IO) {
        if (smsMessages.isEmpty()) return@withContext emptyList()

        val prompt = """
            Analyze these SMS messages and extract transaction details.
            Return ONLY a JSON array of objects with schema: [{"isTransaction":bool,"type":"DEBIT"|"CREDIT","amount":num,"merchant":"str","category":"str"}]
            
            Guidelines:
            - Categories: Food & Dining, Shopping, Travel & Transport, Bills & Utilities, Groceries, Entertainment, Health & Wellness, Investment & Savings, Salary & Income, Self Transfer, Others.
            - If merchant is not explicitly mentioned, infer from context or use the Category name as the merchant. Never return "Unknown" if you can avoid it.
            
            Messages:
            ${smsMessages.mapIndexed { i, msg -> "$i: $msg" }.joinToString("\n")}
        """.trimIndent()

        try {
            val response = model.generateContent(prompt)
            var jsonText = response.text ?: return@withContext smsMessages.map { analyzeSmsLocally(it) }
            
            val startIdx = jsonText.indexOf('[')
            val endIdx = jsonText.lastIndexOf(']')
            if (startIdx != -1 && endIdx != -1 && endIdx > startIdx) {
                jsonText = jsonText.substring(startIdx, endIdx + 1)
            }
            
            val results = mutableListOf<AiTransaction>()
            try {
                val jsonArray = org.json.JSONArray(jsonText)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val aiTxn = AiTransaction(
                        isTransaction = obj.optBoolean("isTransaction", false),
                        type = obj.optString("type", "DEBIT").uppercase(),
                        amount = obj.optDouble("amount", 0.0),
                        merchant = obj.optString("merchant", "Unknown").trim(),
                        category = obj.optString("category", "Others").trim()
                    )
                    
                    if (aiTxn.merchant.lowercase() == "unknown" || aiTxn.merchant.isEmpty() || !aiTxn.isTransaction) {
                        results.add(analyzeSmsLocally(smsMessages[i]))
                    } else {
                        results.add(aiTxn)
                    }
                }
            } catch (e: Exception) {
                val objectRegex = Regex("""\{[^{}]+\}""")
                val matches = objectRegex.findAll(jsonText).toList()
                for (i in smsMessages.indices) {
                    if (i < matches.size) {
                        val objStr = matches[i].value
                        val merchant = Regex(""""merchant":\s*"(.*?)" """, RegexOption.IGNORE_CASE).find(objStr)?.groupValues?.get(1) 
                            ?: Regex(""""merchant":\s*"(.*?)" """, RegexOption.IGNORE_CASE).find(objStr)?.groupValues?.get(1) 
                            ?: "Unknown"
                        val category = Regex(""""category":\s*"(.*?)" """, RegexOption.IGNORE_CASE).find(objStr)?.groupValues?.get(1) ?: "Others"
                        val amount = Regex(""""amount":\s*([\d.]+)""").find(objStr)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
                        
                        if (merchant.lowercase() == "unknown" || merchant.isBlank()) {
                            results.add(analyzeSmsLocally(smsMessages[i]))
                        } else {
                            results.add(AiTransaction(true, if (objStr.contains("CREDIT")) "CREDIT" else "DEBIT", amount, merchant.trim(), category.trim()))
                        }
                    } else {
                        results.add(analyzeSmsLocally(smsMessages[i]))
                    }
                }
            }
            
            while (results.size < smsMessages.size) {
                results.add(analyzeSmsLocally(smsMessages[results.size]))
            }
            results.take(smsMessages.size)
        } catch (e: Exception) {
            smsMessages.map { analyzeSmsLocally(it) }
        }
    }

    fun analyzeSmsLocally(smsBody: String): AiTransaction {
        val bodyLower = smsBody.lowercase()
        
        // 1. Amount Extraction
        val amountRegex = Regex("(?:RS|INR|AMT|AMOUNT)\\.?\\s*([\\d,.]+)", RegexOption.IGNORE_CASE)
        val amountMatch = amountRegex.find(smsBody)
        val amount = amountMatch?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull() ?: 0.0

        // 2. Transaction Type
        val isDebit = bodyLower.contains("debited") || bodyLower.contains("paid") || 
                      bodyLower.contains("sent") || bodyLower.contains("spent") || 
                      bodyLower.contains("purchase") || bodyLower.contains("withdrawal")
        
        val isCredit = bodyLower.contains("credited") || bodyLower.contains("received") || 
                       bodyLower.contains("salary") || bodyLower.contains("refund")

        if (amount <= 0 || (!isDebit && !isCredit)) return AiTransaction(false)

        // 3. Merchant Extraction - Multi-step logic
        var merchant = "Unknown"
        
        // Pattern 1: Delimiter-based (Most accurate for standard bank SMS)
        val structuredPatterns = listOf(
            Regex("paid to (.*?)(?:\\s+on|\\s+using|\\s+ref|\\s+at|$)", RegexOption.IGNORE_CASE),
            Regex("spent at (.*?)(?:\\s+on|\\s+using|\\s+ref|\\s+at|$)", RegexOption.IGNORE_CASE),
            Regex("at (.*?)(?:\\s+on|\\s+using|\\s+ref|\\s+at|$)", RegexOption.IGNORE_CASE),
            Regex("towards (.*?)(?:\\s+on|\\s+using|\\s+ref|\\s+at|$)", RegexOption.IGNORE_CASE),
            Regex("info:\\s*(.*?)(?:\\s+on|\\s+using|\\s+ref|\\s+at|$)", RegexOption.IGNORE_CASE),
            Regex("VPA\\s+(.*?)(?:\\s+on|\\s+using|\\s+ref|\\s+at|$)", RegexOption.IGNORE_CASE),
            Regex("Ref\\s+\\d+\\s+to\\s+(.*?)(?:\\s+on|\\s+at|$)", RegexOption.IGNORE_CASE)
        )

        for (pattern in structuredPatterns) {
            val m = pattern.find(smsBody)
            if (m != null) {
                val candidate = m.groupValues[1].trim().removeSuffix(".").removeSuffix(",")
                if (candidate.length in 2..30 && !candidate.equals("rs", true) && !candidate.contains("balance", true)) {
                    merchant = candidate
                    break
                }
            }
        }

        // Deep Scan: Keyword search for 50+ common brands if pattern matching failed
        if (merchant == "Unknown" || merchant.length < 2) {
            val brands = mapOf(
                "Zomato" to listOf("zomato"), "Swiggy" to listOf("swiggy", "instamart"),
                "Amazon" to listOf("amazon", "amzn"), "Flipkart" to listOf("flipkart"),
                "Uber" to listOf("uber"), "Ola" to listOf("ola cab", "olacabs"),
                "Paytm" to listOf("paytm"), "PhonePe" to listOf("phonepe"),
                "Airtel" to listOf("airtel"), "Jio" to listOf("jio", "reliance jio"),
                "Netflix" to listOf("netflix"), "Spotify" to listOf("spotify"),
                "BigBasket" to listOf("bigbasket"), "Blinkit" to listOf("blinkit"),
                "Google Play" to listOf("google play", "gpay"), "Apple" to listOf("apple", "itunes"),
                "LIC" to listOf("lic india"), "Bescom" to listOf("bescom"),
                "Indane" to listOf("indane", "gas"), "Shell" to listOf("shell petrol"),
                "Starbucks" to listOf("starbucks"), "McDonalds" to listOf("mcdonald"),
                "KFC" to listOf("kfc"), "Dominos" to listOf("domino"),
                "DMart" to listOf("dmart", "avenue supermarts"), "Myntra" to listOf("myntra"),
                "Apollo" to listOf("apollo pharmacy", "apollo"),
                "Self Account" to listOf("self transfer", "to self", "self a/c", "own account", "transfer to self"),
                "Paytm" to listOf("paytm"),
                "Zomato" to listOf("zomato"),
                "Amazon" to listOf("amazon", "amzn"),
                "Swiggy" to listOf("swiggy", "instamart"),
                "Bank Transfer" to listOf("trf", "transfer", "neft", "rtgs", "imps")
            )
            for ((name, keywords) in brands) {
                if (keywords.any { bodyLower.contains(it) }) {
                    merchant = name
                    break
                }
            }
        }

        // Final Cleanup for UPI IDs
        if (merchant.contains("@")) {
            merchant = merchant.split("@").first().replace(Regex("[^A-Za-z0-9]"), " ").trim().uppercase()
        }
        
        // 4. Category Mapping (Done before fallback so we can use it)
        val category = when {
            matches(bodyLower, "zomato", "swiggy", "restaurant", "cafe", "food", "dine", "kfc", "starbucks", "mcdonald", "eats") -> "Food & Dining"
            matches(bodyLower, "amazon", "flipkart", "myntra", "ajio", "shopping", "lifestyle", "nykaa", "fashion", "reliance") -> "Shopping"
            matches(bodyLower, "uber", "ola", "petrol", "fuel", "railway", "irctc", "indigo", "makemytrip", "metro", "shell") -> "Travel & Transport"
            matches(bodyLower, "recharge", "bill", "electricity", "bescom", "gas", "water", "broadband", "jio", "airtel", "vi ", "bsnl") -> "Bills & Utilities"
            matches(bodyLower, "blinkit", "zepto", "bigbasket", "instamart", "grocery", "supermarket", "mart", "dmart", "reliance fresh") -> "Groceries"
            matches(bodyLower, "netflix", "hotstar", "prime", "spotify", "bookmyshow", "pvr", "cinema", "theatre", "gaming", "steam") -> "Entertainment"
            matches(bodyLower, "pharmacy", "apollo", "hospital", "doctor", "health", "gym", "cult", "medical", "clinic", "pharmeasy") -> "Health & Wellness"
            matches(bodyLower, "mutual fund", "stocks", "zerodha", "groww", "investment", "sip", "deposit", "savings", "gold", "lic") -> "Investment & Savings"
            matches(bodyLower, "salary", "stipend", "bonus", "dividend", "income", "interest") -> "Salary & Income"
            matches(bodyLower, "self transfer", "to self", "self a/c", "own account", "transfer to self") -> "Self Transfer"
            else -> "Others"
        }

        // Fallback for merchant: if unknown, use category or find a name
        if (merchant == "Unknown" || merchant.length < 2 || merchant.all { it.isDigit() }) {
            val words = smsBody.split(Regex("[\\s/\\-,]")).filter { it.length > 2 }
            val commonBankWords = setOf("RS", "INR", "AMT", "VPA", "REF", "A/C", "AC", "DEBITED", "CREDITED", "SMS", "BANK", "INFO", "PAYMENT", "PAID", "SENT", "SPENT", "FOR")
            val candidate = words.firstOrNull { word -> 
                word.all { it.isUpperCase() } && !commonBankWords.contains(word.uppercase()) 
            } ?: words.firstOrNull { word ->
                word[0].isUpperCase() && !commonBankWords.contains(word.uppercase()) && !commonBankWords.contains(word.uppercase())
            }
            
            merchant = candidate ?: if (category != "Others") category else "Transaction"
        }

        return AiTransaction(true, if (isCredit && !isDebit) "CREDIT" else "DEBIT", amount, merchant.trim(), category)
    }

    private fun matches(text: String, vararg keywords: String): Boolean {
        return keywords.any { text.contains(it, ignoreCase = true) }
    }

    fun analyzeLocally(transactions: List<TransactionEntity>, monthlyBudget: Double): List<Insight> {
        val insights = mutableListOf<Insight>()
        val debits = transactions.filter { it.isDebit }
        if (debits.isEmpty()) return insights

        val totalSpent = debits.sumOf { it.amount }
        val categoryTotals = debits.groupBy { it.category }.mapValues { it.value.sumOf { t -> t.amount } }
        
        val now = Calendar.getInstance()
        val currentDay = now.get(Calendar.DAY_OF_MONTH).coerceAtLeast(1)
        val totalDays = now.getActualMaximum(Calendar.DAY_OF_MONTH).coerceAtLeast(30)
        
        val dailyAvg = totalSpent / currentDay
        val projected = dailyAvg * totalDays
        if (projected > monthlyBudget) {
            val overBy = projected - monthlyBudget
            insights.add(Insight("Burn Rate Alert", "At this pace, you'll exceed your budget by ₹${overBy.toInt()}. Consider cutting back on non-essentials.", "High"))
        } else {
            insights.add(Insight("Budget On Track", "You are spending ₹${dailyAvg.toInt()} per day. Keep it up to stay under ₹${monthlyBudget.toInt()}.", "Low"))
        }

        val smallSpends = debits.filter { it.amount in 10.0..300.0 }
        if (smallSpends.size >= 5) {
            val smallTotal = smallSpends.sumOf { it.amount }
            insights.add(Insight("Latte Factor", "You had ${smallSpends.size} small purchases totaling ₹${smallTotal.toInt()}. These 'micro-spends' add up fast!", "Medium"))
        }

        val topCategory = categoryTotals.maxByOrNull { it.value }
        if (topCategory != null && topCategory.value > totalSpent * 0.5) {
            insights.add(Insight("Spending Heavy", "Over 50% of your budget is going to ${topCategory.key}. Is there room to optimize here?", "Medium"))
        }

        val weekendSpend = debits.filter {
            val cal = Calendar.getInstance().apply { timeInMillis = it.date }
            val day = cal.get(Calendar.DAY_OF_WEEK)
            day == Calendar.SATURDAY || day == Calendar.SUNDAY
        }.sumOf { it.amount }
        if (weekendSpend > totalSpent * 0.4) {
            insights.add(Insight("Weekend Peak", "Your weekend spending is high (₹${weekendSpend.toInt()}). Try a 'No-Spend' Sunday next week.", "Medium"))
        }

        return insights.shuffled().take(3)
    }

    private fun buildPrompt(transactions: List<TransactionEntity>, monthlyBudget: Double): String {
        val debits = transactions.filter { it.isDebit }.sortedByDescending { it.date }
        val totalSpent = debits.sumOf { it.amount }
        
        val categoryData = debits.groupBy { it.category }
            .mapValues { it.value.sumOf { t -> t.amount } }
            .entries.sortedByDescending { it.value }
            .joinToString { "${it.key}: ₹${it.value.toInt()}" }
        
        val merchantData = debits.groupBy { it.merchant }
            .mapValues { it.value.sumOf { t -> t.amount } }
            .toList().sortedByDescending { it.second }.take(5)
            .joinToString { "${it.first} (₹${it.second.toInt()})" }

        val now = Calendar.getInstance()
        val day = now.get(Calendar.DAY_OF_MONTH)
        val dailyAvg = if (day > 0) totalSpent / day else 0.0

        return """
            You are "FinTrack AI", a world-class wealth coach. 
            User Context:
            - Monthly Budget: ₹$monthlyBudget
            - Current Spending: ₹$totalSpent ($day days elapsed)
            - Daily Average: ₹${dailyAvg.toInt()}
            - Category Breakdown: $categoryData
            - Top Merchants: $merchantData

            Task: Provide 3 HYPER-SPECIFIC insights. 
            Format:
            TITLE: [3-5 words]
            MESSAGE: [1-2 sentences]
            IMPACT: [High/Medium/Low]
            ---
        """.trimIndent()
    }

    private fun parseAiResponse(response: String): List<Insight> {
        val insights = mutableListOf<Insight>()
        val blocks = response.split("---")
        
        for (block in blocks) {
            if (block.isBlank()) continue
            
            var title = ""
            var message = ""
            var impact = "Medium"
            
            block.trim().lines().forEach { line ->
                when {
                    line.startsWith("TITLE:", ignoreCase = true) -> title = line.substringAfter(":").trim()
                    line.startsWith("MESSAGE:", ignoreCase = true) -> message = line.substringAfter(":").trim()
                    line.startsWith("IMPACT:", ignoreCase = true) -> impact = line.substringAfter(":").trim()
                }
            }
            
            if (title.isNotEmpty() && message.isNotEmpty()) {
                insights.add(Insight(title.removeSurrounding("[", "]"), message, impact.removeSurrounding("[", "]")))
            }
        }
        return insights.take(3)
    }
}
