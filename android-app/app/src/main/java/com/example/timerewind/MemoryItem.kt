package com.example.timerewind

data class MemoryItem(
    val text: String,
    val timestamp: String,
    val emotion: String? = null,
    val confidence: Double? = null,
    val voice_stress: VoiceStressData? = null
)
