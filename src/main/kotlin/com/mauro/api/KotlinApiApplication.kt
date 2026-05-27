package com.mauro.api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaAuditing

@SpringBootApplication
@EnableJpaAuditing
class KotlinApiApplication

fun main(args: Array<String>) {
    runApplication<KotlinApiApplication>(*args)
}
