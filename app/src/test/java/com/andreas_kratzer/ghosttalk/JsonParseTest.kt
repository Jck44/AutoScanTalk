package com.andreas_kratzer.ghosttalk

import com.andreas_kratzer.ghosttalk.model.importexport.ImportExportData
import com.google.gson.Gson
import org.junit.Test
import java.io.File

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
            page.rows
            page.columns
            page.buttons.forEach { btn ->
                btn.index
            }
        }
    }
}
