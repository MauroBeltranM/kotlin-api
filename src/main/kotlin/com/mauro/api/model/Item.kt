package com.mauro.api.model

import jakarta.persistence.*

@Entity
@Table(name = "items")
data class Item(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false)
    val name: String,

    val description: String? = null,

    val done: Boolean = false
) {
    constructor() : this(id = null, name = "", description = null, done = false)
}
