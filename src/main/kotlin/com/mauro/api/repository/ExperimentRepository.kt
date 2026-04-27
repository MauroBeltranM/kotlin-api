package com.mauro.api.repository

import com.mauro.api.model.Experiment
import com.mauro.api.model.ExperimentType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ExperimentRepository : JpaRepository<Experiment, Long> {

    fun findByFavoriteTrue(): List<Experiment>

    fun findByType(type: ExperimentType): List<Experiment>

    fun findByTitleContainingIgnoreCase(query: String): List<Experiment>

    @Query("SELECT e FROM Experiment e WHERE LOWER(e.tags) LIKE LOWER(CONCAT('%', :tag, '%'))")
    fun findByTag(@Param("tag") tag: String): List<Experiment>

    @Query("SELECT DISTINCT e.type FROM Experiment e")
    fun findDistinctTypes(): List<ExperimentType>

    fun countByType(type: ExperimentType): Long
}
