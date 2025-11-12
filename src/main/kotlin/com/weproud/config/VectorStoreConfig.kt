package com.weproud.config

import org.springframework.ai.document.Document
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.nio.file.Files
import java.nio.file.Paths

@Configuration
class VectorStoreConfig {

    @Bean
    fun ragInitializer(vectorStore: VectorStore): ApplicationRunner = ApplicationRunner {
        val path = Paths.get("src/main/resources/data/ratings.txt")
        if (!Files.exists(path)) return@ApplicationRunner

        Files.newBufferedReader(path).use { reader ->
            var headerSkipped = false
            val batch = mutableListOf<Document>()

            reader.forEachLine { line ->
                if (!headerSkipped) {
                    headerSkipped = true
                    return@forEachLine
                }

                val parts = line.split('\t')
                if (parts.size >= 3) {
                    val id = parts[0]
                    val text = parts[1]
                    val label = parts[2]

                    batch.add(Document(text, mapOf("id" to id, "label" to label)))

                    if (batch.size >= 100) {
                        vectorStore.add(batch.toList())
                        batch.clear()
                    }
                }
            }

            if (batch.isNotEmpty()) {
                vectorStore.add(batch.toList())
            }
        }
    }
}
