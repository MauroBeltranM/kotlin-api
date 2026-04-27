package com.mauro.api.controller

import com.mauro.api.model.*
import com.mauro.api.service.ExperimentService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api")
class ExperimentController(private val service: ExperimentService) {

    // === Experiments CRUD ===

    @GetMapping("/experiments")
    fun list() = service.findAll()

    @GetMapping("/experiments/favorites")
    fun favorites() = service.findFavorites()

    @GetMapping("/experiments/type/{type}")
    fun byType(@PathVariable type: ExperimentType) = service.findByType(type)

    @GetMapping("/experiments/search")
    fun search(@RequestParam q: String) = service.search(q)

    @GetMapping("/experiments/tag/{tag}")
    fun byTag(@PathVariable tag: String) = service.findByTag(tag)

    @GetMapping("/experiments/{id}")
    fun get(@PathVariable id: Long) = service.findById(id)

    @PostMapping("/experiments")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody body: CreateExperimentRequest) = service.create(body)

    @PutMapping("/experiments/{id}")
    fun update(@PathVariable id: Long, @Valid @RequestBody body: UpdateExperimentRequest) =
        service.update(id, body)

    @PostMapping("/experiments/{id}/favorite")
    fun toggleFavorite(@PathVariable id: Long) = service.toggleFavorite(id)

    @PostMapping("/experiments/{id}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    fun duplicate(@PathVariable id: Long) = service.duplicate(id)

    @GetMapping("/experiments/random")
    fun random() = service.random()

    @DeleteMapping("/experiments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: Long) = service.delete(id)

    // === Stats & Meta ===

    @GetMapping("/stats")
    fun stats() = service.getStats()

    @GetMapping("/types")
    fun types() = service.getTypes()

    // === Audio Rendering ===

    @PostMapping("/render", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun render(@RequestBody request: RenderRequest) = service.render(request)

    // === Root ===

    @GetMapping("/")
    fun root() = mapOf(
        "name" to "Sound Lab API",
        "version" to "1.0.0",
        "endpoints" to mapOf(
            "experiments" to "/api/experiments",
            "render" to "/api/render",
            "stats" to "/api/stats",
            "types" to "/api/types",
            "search" to "/api/experiments/search?q=...",
            "docs" to "Try POST /api/render with a type!"
        )
    )
}
