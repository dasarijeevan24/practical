package com.smartattendance.app

data class AttendanceRecord(
    val id: String,
    val date: String, // yyyy-MM-dd
    val timestamp: String, // yyyy-MM-dd HH:mm:ss
    val address: String,
    val distanceMeters: Float,
    val status: String,
    val userEmail: String
)
