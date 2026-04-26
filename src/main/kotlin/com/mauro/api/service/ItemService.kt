package com.mauro.api.service

import com.mauro.api.model.CreateItemRequest
import com.mauro.api.model.Item
import com.mauro.api.model.UpdateItemRequest
import com.mauro.api.repository.ItemRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class ItemService(private val repository: ItemRepository) {

    fun findAll(): List<Item> = repository.findAll()

    fun findById(id: Long): Item =
        repository.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Item $id not found")
        }

    fun create(request: CreateItemRequest): Item =
        repository.save(Item(name = request.name, description = request.description))

    fun update(id: Long, request: UpdateItemRequest): Item {
        val item = findById(id)
        val updated = item.copy(
            name = request.name ?: item.name,
            description = request.description ?: item.description,
            done = request.done ?: item.done
        )
        return repository.save(updated)
    }

    fun delete(id: Long) {
        if (!repository.existsById(id)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Item $id not found")
        }
        repository.deleteById(id)
    }
}
