package com.mauro.api.model

import jakarta.persistence.*
import jakarta.validation.constraints.*

@Entity
@Table(name = "experiments")
data class Experiment(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false)
    val title: String,

    val description: String? = null,

    @Enumerated(EnumType.STRING)
    val type: ExperimentType = ExperimentType.DRONE,

    val bpm: Int = 120,

    @Column(columnDefinition = "text")
    val chainJson: String? = null, // JSON array of effects/parameters

    @Column(columnDefinition = "text")
    val waveformData: String? = null, // base64 encoded

    @Column(columnDefinition = "text")
    val audioDataUrl: String? = null, // data URI

    val favorite: Boolean = false,

    val tags: String? = null, // comma-separated
) {
    constructor() : this(id = null, title = "", type = ExperimentType.DRONE)
}

enum class ExperimentType {
    DRONE, RHYTHM, MELODY, AMBIENT, NOISE, GLITCH, GENERATIVE
}

// DTOs
data class CreateExperimentRequest(
    @field:NotBlank(message = "Title is required")
    val title: String,
    val description: String? = null,
    val type: ExperimentType = ExperimentType.DRONE,
    val bpm: Int = 120,
    val tags: String? = null,
)

data class UpdateExperimentRequest(
    val title: String? = null,
    val description: String? = null,
    val type: ExperimentType? = null,
    val bpm: Int? = null,
    val chainJson: String? = null,
    val waveformData: String? = null,
    val audioDataUrl: String? = null,
    val favorite: Boolean? = null,
    val tags: String? = null,
)

data class RenderRequest(
    val type: ExperimentType = ExperimentType.DRONE,
    val bpm: Int = 120,
    val duration: Float = 4f,
    val params: Map<String, Any> = emptyMap(),
)

data class RenderResponse(
    val audioDataUrl: String,
    val waveformData: String,
    val sampleRate: Int = 44100,
    val duration: Float,
    val type: ExperimentType,
)
