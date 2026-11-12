package com.smartattendance.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

class AttendanceAdapter(private val records: List<AttendanceRecord>) :
    RecyclerView.Adapter<AttendanceAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvDate: TextView = view.findViewById(R.id.tvItemDate)
        val tvStatus: TextView = view.findViewById(R.id.tvItemStatus)
        val tvDistance: TextView = view.findViewById(R.id.tvItemDistance)
        val tvAddress: TextView = view.findViewById(R.id.tvItemAddress)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_attendance, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val record = records[position]
        holder.tvDate.text = record.timestamp
        holder.tvStatus.text = record.status
        holder.tvDistance.text = String.format(Locale.getDefault(), "Distance: %.1f m", record.distanceMeters)
        holder.tvAddress.text = "Address: ${record.address}"
    }

    override fun getItemCount(): Int = records.size
}
