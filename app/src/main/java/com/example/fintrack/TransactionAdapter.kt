package com.example.fintrack

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class TransactionAdapter(private val onLongClick: (TransactionEntity) -> Unit = {}) : RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder>() {

    private var transactions = listOf<TransactionEntity>()

    fun setTransactions(newTransactions: List<TransactionEntity>) {
        this.transactions = newTransactions
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_transaction, parent, false)
        return TransactionViewHolder(view, onLongClick)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        val transaction = transactions[position]
        holder.bind(transaction)
    }

    override fun getItemCount() = transactions.size

    class TransactionViewHolder(itemView: View, private val onLongClick: (TransactionEntity) -> Unit) : RecyclerView.ViewHolder(itemView) {
        private val txtMerchant: TextView = itemView.findViewById(R.id.txtMerchant)
        private val txtCategoryName: TextView = itemView.findViewById(R.id.txtCategoryName)
        private val txtDate: TextView = itemView.findViewById(R.id.txtDate)
        private val txtAmount: TextView = itemView.findViewById(R.id.txtAmount)
        private val txtIcon: TextView = itemView.findViewById(R.id.txtCategoryIcon)

        fun bind(transaction: TransactionEntity) {
            itemView.setOnLongClickListener {
                onLongClick(transaction)
                true
            }
            
            txtMerchant.text = transaction.merchant
            txtCategoryName.text = transaction.category
            txtAmount.text = "${if (transaction.isDebit) "-" else "+"} ₹%.2f".format(transaction.amount)
            txtAmount.setTextColor(if (transaction.isDebit) android.graphics.Color.parseColor("#EA4335") else android.graphics.Color.parseColor("#34A853"))
            
            val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
            txtDate.text = sdf.format(Date(transaction.date))
            
            val category = transaction.category
            txtIcon.text = when {
                category.contains("Food") -> "🍕"
                category.contains("Shop") -> "🛍️"
                category.contains("Travel") -> "🚗"
                category.contains("Bills") -> "📄"
                category.contains("Grocer") -> "🛒"
                category.contains("Entertain") -> "🎬"
                category.contains("Health") -> "💊"
                category.contains("Invest") -> "📈"
                category.contains("Salary") -> "💰"
                category.contains("Self") -> "🔄"
                else -> "📦"
            }
        }
    }
}
