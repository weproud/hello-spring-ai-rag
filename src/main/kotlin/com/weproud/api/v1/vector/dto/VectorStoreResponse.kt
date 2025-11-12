package com.weproud.api.v1.vector.dto

data class CreateVectorStoreResponse(
    val inserted: Int
)

data class SearchVectorStoreHit(
    val content: String,
    val metadata: Map<String, Any>
)
data class SearchVectorStoreResponse(
    val hits: List<SearchVectorStoreHit>,
    val count: Int
)