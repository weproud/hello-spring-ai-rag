package com.weproud.api.v1.vector

import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import jakarta.validation.Valid
import com.weproud.api.v1.vector.dto.CreateVectorStoreRequest
import com.weproud.api.v1.vector.dto.CreateVectorStoreResponse
import com.weproud.api.v1.vector.dto.SearchVectorStoreRequest
import com.weproud.api.v1.vector.dto.SearchVectorStoreResponse

@Validated
@RestController
@RequestMapping("/api/v1/vectors")
class VectorStoreController(
    private val vectorStoreService: VectorStoreService
) {

    @PostMapping
    fun add(@Valid @RequestBody req: CreateVectorStoreRequest): CreateVectorStoreResponse {
        val inserted = vectorStoreService.addDocuments(req)
        return CreateVectorStoreResponse(inserted)
    }

    @PostMapping("/search")
    fun search(@Valid @RequestBody req: SearchVectorStoreRequest): SearchVectorStoreResponse {
        return vectorStoreService.search(req)
    }
}
