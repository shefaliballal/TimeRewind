package com.example.timerewind

data class TranscriptionResponse(
    val transcript: String,
    val emotion: String? = null,
    val confidence: Double? = null,
    val voice_stress: VoiceStressData? = null
)

data class VoiceStressData(
    val pitch_mean: Double,
    val energy_mean: Double,
    val energy_variance: Double,
    val spectral_centroid: Double,
    val zero_crossing_rate: Double,
    val stress_level: Double,
    val stress_category: String
)
