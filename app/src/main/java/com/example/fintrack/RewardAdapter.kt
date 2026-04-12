package com.example.fintrack

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class RewardAdapter(
    private var rewards: List<Reward>,
    private val userPoints: Int,
    private val onRedeem: (Reward) -> Unit
) : RecyclerView.Adapter<RewardAdapter.RewardViewHolder>() {

    fun updateRewards(newRewards: List<Reward>) {
        this.rewards = newRewards
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RewardViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_reward, parent, false)
        return RewardViewHolder(view)
    }

    override fun onBindViewHolder(holder: RewardViewHolder, position: Int) {
        val reward = rewards[position]
        holder.bind(reward, userPoints, onRedeem)
    }

    override fun getItemCount() = rewards.size

    class RewardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val txtIcon: TextView = itemView.findViewById(R.id.txtRewardIcon)
        private val txtTitle: TextView = itemView.findViewById(R.id.txtRewardTitle)
        private val txtDescription: TextView = itemView.findViewById(R.id.txtRewardDescription)
        private val btnRedeem: Button = itemView.findViewById(R.id.btnRedeem)

        fun bind(reward: Reward, userPoints: Int, onRedeem: (Reward) -> Unit) {
            txtIcon.text = reward.icon
            txtTitle.text = reward.title
            txtDescription.text = reward.description
            btnRedeem.text = "Redeem for ${reward.cost} pts"
            
            btnRedeem.isEnabled = userPoints >= reward.cost
            btnRedeem.setOnClickListener { onRedeem(reward) }
        }
    }
}
