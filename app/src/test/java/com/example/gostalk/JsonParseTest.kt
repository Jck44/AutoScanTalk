package com.example.gostalk

import org.junit.Test
import com.google.gson.Gson
import com.example.gostalk.model.importexport.ImportExportData
import java.io.File
import org.junit.Assert.*

class JsonParseTest {
    @Test
    fun parseJsonTest() {
        val file = File("/Users/andreas.kratzer/AndroidStudioProjects/GoSTalk/gotalknow_extracted/test-converted.json")
        val jsonString = file.readText()
        val gson = Gson()
        val importData = gson.fromJson(jsonString, ImportExportData::class.java)
        println("Successfully parsed \${importData.pages.size} pages")
        
        // Validate ints
        importData.pages.forEach { page ->
            val rows = page.rows
            val columns = page.columns
            page.buttons.forEach { btn ->
                val idx = btn.index
            }
        }
    }
}
