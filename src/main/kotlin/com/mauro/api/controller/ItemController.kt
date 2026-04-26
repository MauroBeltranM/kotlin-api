package com.mauro.api.controller

import com.mauro.api.model.CreateItemRequest
import com.mauro.api.model.UpdateItemRequest
import com.mauro.api.service.ItemService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/items")
class ItemController(private val service: ItemService) {

    @GetMapping
    fun list() = service.findAll()

    @GetMapping("/{id}")
    fun get(@PathVariable id: Long) = service.findById(id)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody body: CreateItemRequest) = service.create(body)

    @PutMapping("/{id}")
    fun update(@PathVariable id: Long, @Valid @RequestBody body: UpdateItemRequest) =
        service.update(id, body)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: Long) = service.delete(id)
}
