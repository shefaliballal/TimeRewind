package com.example.timerewind

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.timerewind.R
import com.example.timerewind.MemoryItem


class MemoryAdapter : RecyclerView.Adapter<MemoryAdapter.MemoryViewHolder>() {

    private var items: List<MemoryItem> = listOf()

    fun setItems(newItems: List<MemoryItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    class MemoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val text: TextView = itemView.findViewById(R.id.memoryText)
        val timestamp: TextView = itemView.findViewById(R.id.memoryTimestamp)
        val emotionStress: TextView = itemView.findViewById(R.id.memoryEmotionStress)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemoryViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_memory_list, parent, false)
        return MemoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: MemoryViewHolder, position: Int) {
        val item = items[position]
        holder.text.text = item.text
        
        // Format timestamp to readable date/time
        val formattedTime = try {
            val timestamp = item.timestamp.toLongOrNull()
            if (timestamp != null) {
                val date = java.util.Date(timestamp)
                val formatter = java.text.SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", java.util.Locale.getDefault())
                formatter.format(date)
            } else {
                item.timestamp
            }
        } catch (e: Exception) {
            item.timestamp
        }
        holder.timestamp.text = "🕒 $formattedTime"

        // Set emotion/stress info
        val info = StringBuilder()
        item.emotion?.let { emotion ->
            info.append("😊 $emotion")
            item.confidence?.let { conf ->
                // Debug logging
                android.util.Log.d("MemoryAdapter", "Emotion: $emotion, Confidence: $conf")
                
                when {
                    conf >= 0.8 -> info.append(" (${String.format("%.0f", conf * 100)}% confident)")
                    conf >= 0.6 -> info.append(" (${String.format("%.0f", conf * 100)}% likely)")
                    conf >= 0.4 -> info.append(" (${String.format("%.0f", conf * 100)}% possible)")
                    conf >= 0.3 -> info.append(" (${String.format("%.0f", conf * 100)}% detected)")
                    else -> info.append(" (detected)")
                }
            } ?: run {
                android.util.Log.d("MemoryAdapter", "Emotion: $emotion, Confidence: null")
                info.append(" (detected)")
            }
        }
        item.voice_stress?.let { stress ->
            if (info.isNotEmpty()) info.append(" | ")
            val stressEmoji = when (stress.stress_category) {
                "high" -> "😰"
                "medium" -> "😐"
                "low" -> "😌"
                else -> "😐"
            }
            info.append("$stressEmoji ${stress.stress_category} (${String.format("%.0f", stress.stress_level * 100)}%)")
        }
        holder.emotionStress.text = info.toString()
    }

    override fun getItemCount(): Int = items.size
}
