package com.example.timerewind

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class TimelineAdapter(private val memories: List<MemoryMoment>) :
    RecyclerView.Adapter<TimelineAdapter.MemoryViewHolder>() {

    inner class MemoryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val timeView: TextView = view.findViewById(R.id.timeView)
        val emotionView: TextView = view.findViewById(R.id.emotionView)
        val transcriptView: TextView = view.findViewById(R.id.transcriptView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemoryViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_memory, parent, false)
        return MemoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: MemoryViewHolder, position: Int) {
        val moment = memories[position]
        holder.timeView.text = moment.time
        holder.emotionView.text = "😄 Emotion: ${moment.emotion} | Stress: ${"%.1f".format(moment.stress * 100)}%"
        holder.transcriptView.text = moment.transcript
    }

    override fun getItemCount(): Int = memories.size
} 