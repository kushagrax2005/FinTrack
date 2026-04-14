package com.example.fintrack

data class Reward(
    val id: String,
    val title: String,
    val description: String,
    val baseCost: Int,
    val icon: String,
    val type: String, // "SUBSCRIPTION", "GIFT_CARD", "VOUCHER"
    val cost: Int = 0
) {
    fun getDynamicCost(monthlyBudget: Double): Int {
        // Base budget set at 15,000. If budget is higher, price increases proportionally.
        // This prevents users from setting a 10 Lakh budget to easily earn efficiency points.
        val factor = (monthlyBudget / 15000.0).coerceAtLeast(1.0)
        return (baseCost * factor).toInt()
    }
}

data class RewardPointHistory(
    val date: Long,
    val pointsEarned: Int,
    val dailyBudget: Double,
    val actualSpent: Double
)
