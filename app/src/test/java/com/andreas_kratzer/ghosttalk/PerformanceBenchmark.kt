package com.andreas_kratzer.ghosttalk

import com.andreas_kratzer.ghosttalk.data.Converters
import com.andreas_kratzer.ghosttalk.model.AuditoryCue
import com.andreas_kratzer.ghosttalk.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.model.Page
import com.andreas_kratzer.ghosttalk.model.SpeakTextButtonAction
import org.junit.Test
import kotlin.system.measureTimeMillis

class PerformanceBenchmark {

    @Test
    fun benchmarkLargeDataset() {
        // Timestamp: 2026-03-09T18:45:00
        val converters = Converters()
        val pageCount = 1000
        val buttonsPerPage = 49

        println("--- Starting Benchmark with $pageCount pages and $buttonsPerPage buttons per page ---")

        // 1. Generation
        val pages = mutableListOf<Page>()
        val generationTime = measureTimeMillis {
            repeat(pageCount) { pIdx ->
                val configs = mutableListOf<ButtonConfig?>()
                repeat(buttonsPerPage) { bIdx ->
                    configs.add(
                        ButtonConfig(
                            label = "Button $bIdx on Page $pIdx",
                            spokenText = "I am button $bIdx",
                            auditoryCue = AuditoryCue.TextToSpeechCue("Button $bIdx"),
                            buttonAction = if (bIdx % 10 == 0) {
                                NavigateToPageButtonAction("page_${(pIdx + 1) % pageCount}")
                            } else {
                                SpeakTextButtonAction()
                            }
                        )
                    )
                }
                pages.add(
                    Page(
                        id = "page_$pIdx",
                        bookId = "book_1",
                        name = "Page $pIdx",
                        rows = 7,
                        columns = 7,
                        buttonConfigs = configs
                    )
                )
            }
        }
        println("Generation of $pageCount pages: ${generationTime}ms")

        // 2. Serialization (JSON) - simulating DB write
        var totalJsonLength = 0L
        val serializationTime = measureTimeMillis {
            pages.forEach { page ->
                val json = converters.fromButtonConfigList(page.buttonConfigs)
                totalJsonLength += json?.length ?: 0
            }
        }
        println("Serialization (JSON) of $pageCount pages: ${serializationTime}ms")
        println("Approximate total JSON size: ${totalJsonLength / 1024} KB")

        // 3. Deserialization (JSON) - simulating DB read
        val deserializationTime = measureTimeMillis {
            pages.forEach { page ->
                val json = converters.fromButtonConfigList(page.buttonConfigs)
                converters.toButtonConfigList(json)
            }
        }
        println("Deserialization (JSON) of $pageCount pages: ${deserializationTime}ms")

        // 4. Lookup Map Creation - simulating ResolveDynamicButtonsUseCase
        // Pass 1: Initial build
        val buttonLookup: Map<String, ButtonConfig>
        val pageLookup: Map<String, Page>
        val lookupTime1 = measureTimeMillis {
            buttonLookup = pages.flatMap { it.buttonConfigs }.filterNotNull().associateBy { it.id }
            pageLookup = pages.associateBy { it.id }
        }
        println("Lookup Map Creation Pass 1 (Initial): ${lookupTime1}ms")
        
        // Pass 2: Cached build (simulating the new logic)
        var lastPagesRef = pages
        val lookupTime2 = measureTimeMillis {
            if (lastPagesRef === pages) {
                // Return cached version (simulated)
                val _b = buttonLookup
                val _p = pageLookup
            }
        }
        println("Lookup Map Creation Pass 2 (Cached): ${lookupTime2}ms")
        
        println("--- Benchmark Finished ---")
    }
}
