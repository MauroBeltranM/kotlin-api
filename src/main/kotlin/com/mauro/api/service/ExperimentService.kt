package com.mauro.api.service

import com.mauro.api.model.*
import com.mauro.api.repository.ExperimentRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class ExperimentService(private val repository: ExperimentRepository) {

    // === Queries ===

    fun findAll(): List<Experiment> = repository.findAll()

    fun findById(id: Long): Experiment =
        repository.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Experiment $id not found")
        }

    fun findFavorites(): List<Experiment> = repository.findByFavoriteTrue()

    fun findByType(type: ExperimentType): List<Experiment> = repository.findByType(type)

    fun search(query: String): List<Experiment> = repository.findByTitleContainingIgnoreCase(query)

    fun findByTag(tag: String): List<Experiment> = repository.findByTag(tag)

    fun getTypes(): List<ExperimentType> = repository.findDistinctTypes()

    fun getStats(): Map<String, Any> {
        val byType = ExperimentType.entries.associate {
            it.name.lowercase() to repository.countByType(it)
        }
        return mapOf(
            "total" to repository.count(),
            "favorites" to repository.findByFavoriteTrue().size,
            "byType" to byType,
        )
    }

    fun random(): Experiment = repository.findAll().randomOrNull()
        ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No experiments yet. Create one!")

    // === Mutations ===

    fun create(request: CreateExperimentRequest): Experiment = repository.save(
        Experiment(
            title = request.title,
            description = request.description,
            type = request.type,
            bpm = request.bpm,
            tags = request.tags,
        )
    )

    fun update(id: Long, request: UpdateExperimentRequest): Experiment {
        val current = findById(id)
        return repository.save(
            current.copy(
                title = request.title ?: current.title,
                description = request.description ?: current.description,
                type = request.type ?: current.type,
                bpm = request.bpm ?: current.bpm,
                chainJson = request.chainJson ?: current.chainJson,
                waveformData = request.waveformData ?: current.waveformData,
                audioDataUrl = request.audioDataUrl ?: current.audioDataUrl,
                favorite = request.favorite ?: current.favorite,
                tags = request.tags ?: current.tags,
            )
        )
    }

    fun toggleFavorite(id: Long): Experiment {
        val current = findById(id)
        return repository.save(current.copy(favorite = !current.favorite))
    }

    fun duplicate(id: Long): Experiment {
        val current = findById(id)
        return repository.save(current.copy(id = null, title = "${current.title} (copy)"))
    }

    fun delete(id: Long) {
        if (!repository.existsById(id)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Experiment $id not found")
        }
        repository.deleteById(id)
    }

    // === Audio Rendering ===

    fun render(request: RenderRequest): RenderResponse {
        val engine = AudioEngine()
        val samples = engine.synthesize(request.type, request.bpm, request.duration, request.params)

        return RenderResponse(
            audioDataUrl = "data:audio/wav;base64,${engine.toWavBase64(samples)}",
            waveformData = "data:image/svg+xml;base64,${engine.toWaveformSvgBase64(samples)}",
            sampleRate = AudioEngine.SAMPLE_RATE,
            duration = request.duration,
            type = request.type,
        )
    }
}
