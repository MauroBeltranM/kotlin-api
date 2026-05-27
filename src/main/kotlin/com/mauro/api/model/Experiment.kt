package com.mauro.api.model

import jakarta.persistence.*
import jakarta.validation.constraints.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

// === Entity ===

@Entity
@Table(name = "experiments")
@EntityListeners(AuditingEntityListener::class)
data class Experiment(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false)
    val title: String,

    val description: String? = null,

    @Enumerated(EnumType.STRING)
    val type: ExperimentType = ExperimentType.DRONE,

    val bpm: Int = DEFAULT_BPM,

    @Column(columnDefinition = "text")
    val chainJson: String? = null,

    @Column(columnDefinition = "text")
    val waveformData: String? = null,

    @Column(columnDefinition = "text")
    val audioDataUrl: String? = null,

    val favorite: Boolean = false,

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "experiment_tags", joinColumns = [JoinColumn(name = "experiment_id")])
    @Column(name = "tag")
    val tags: List<String> = emptyList(),

    @CreatedDate
    @Column(updatable = false)
    val createdAt: Instant? = null,

    @LastModifiedDate
    val updatedAt: Instant? = null,
) {
    companion object {
        const val DEFAULT_BPM = 120
    }

    constructor() : this(title = "", tags = emptyList(), createdAt = null, updatedAt = null)
}

// === Enum ===

enum class ExperimentType {
    DRONE,
    RHYTHM,
    MELODY,
    AMBIENT,
    NOISE,
    GLITCH,
    GENERATIVE,
}

// === DTOs ===

data class CreateExperimentRequest(
    @field:NotBlank(message = "Title is required")
    val title: String,
    val description: String? = null,
    val type: ExperimentType = ExperimentType.DRONE,
    val bpm: Int = Experiment.DEFAULT_BPM,
    val tags: List<String> = emptyList(),
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
    val tags: List<String>? = null,
)

data class RenderRequest(
    val type: ExperimentType = ExperimentType.DRONE,
    val bpm: Int = Experiment.DEFAULT_BPM,
    val duration: Float = 4f,
    val params: Map<String, Any> = emptyMap(),
)

data class RenderResponse(
    val audioDataUrl: String,
    val waveformData: String,
    val sampleRate: Int = SAMPLE_RATE,
    val duration: Float,
    val type: ExperimentType,
) {
    companion object {
        const val SAMPLE_RATE = 44100
    }
}
