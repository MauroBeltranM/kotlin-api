package com.mauro.api.model

import jakarta.validation.constraints.NotBlank

data class CreateItemRequest(
    @field:NotBlank
    val name: String,
    val description: String? = null
)

data class UpdateItemRequest(
    @field:NotBlank
    val name: String? = null,
    val description: String? = null,
    val done: Boolean? = null
)
