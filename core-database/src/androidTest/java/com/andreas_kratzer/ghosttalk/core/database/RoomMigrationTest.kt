package com.andreas_kratzer.ghosttalk.core.database

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomMigrationTest {

    private fun getInMemoryDb(version: Int, onCreateCallback: (SupportSQLiteDatabase) -> Unit): SupportSQLiteDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null) // null name opens an in-memory database
            .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    onCreateCallback(db)
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        return helper.writableDatabase
    }

    private fun createVersion10Db(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `pages` (
                `id` TEXT NOT NULL,
                `bookId` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `templateId` TEXT,
                `rows` INTEGER NOT NULL,
                `columns` INTEGER NOT NULL,
                `scanPattern` TEXT,
                `rowNames` TEXT NOT NULL,
                `orderIndex` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `buttonConfigs` TEXT,
                PRIMARY KEY(`id`)
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `templates` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `rows` INTEGER NOT NULL,
                `columns` INTEGER NOT NULL,
                `buttonConfigs` TEXT NOT NULL,
                `isBuiltIn` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `orderIndex` INTEGER NOT NULL DEFAULT 0,
                `scanPattern` TEXT,
                `rowNames` TEXT NOT NULL DEFAULT '[]',
                PRIMARY KEY(`id`)
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `button_usage_stats` (
                `bookId` TEXT NOT NULL,
                `buttonConfigId` TEXT NOT NULL,
                `label` TEXT NOT NULL,
                `actionJson` TEXT NOT NULL,
                `usageCount` INTEGER NOT NULL DEFAULT 0,
                `lastUsedAt` INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(`bookId`, `buttonConfigId`)
            )
        """.trimIndent())
    }

    private fun createVersion33Db(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `books` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `actionLogLimit` INTEGER NOT NULL DEFAULT 100,
                `limitScanCycles` INTEGER NOT NULL DEFAULT 0,
                `scanCycleLimit` INTEGER NOT NULL DEFAULT 2,
                `logIgnoredActions` INTEGER NOT NULL DEFAULT 1,
                `logStopActions` INTEGER NOT NULL DEFAULT 1,
                `versionSequence` INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(`id`)
            )
        """.trimIndent())
    }

    @Test
    fun testMigration10To11_success() {
        val db = getInMemoryDb(10) { createVersion10Db(it) }

        // Insert valid page with legacy buttons configuration
        val legacyButtonsJson = """
            [
              {
                "id": "btn_1",
                "label": "Hello",
                "spokenText": "Hello World",
                "isActive": true,
                "playActionAsAuditoryCue": false,
                "buttonAction": {
                  "prompt": "Say hello to user"
                },
                "auditoryCue": {
                  "text": "Speak hello"
                }
              },
              {
                "id": "btn_2",
                "label": "Go to Home",
                "isActive": false,
                "playActionAsAuditoryCue": true,
                "buttonAction": {
                  "type": "com.andreas_kratzer.ghosttalk.core.model.NavigateToPageButtonAction",
                  "data": {
                    "pageId": "home_page"
                  }
                },
                "auditoryCue": null
              }
            ]
        """.trimIndent()

        db.execSQL("""
            INSERT INTO pages (id, bookId, name, templateId, rows, columns, scanPattern, rowNames, orderIndex, createdAt, buttonConfigs)
            VALUES ('page_1', 'book_1', 'My Page', NULL, 4, 4, NULL, '[]', 0, 123456789, ?)
        """, arrayOf(legacyButtonsJson))

        // Insert valid template
        val legacyTemplateButtonsJson = """
            [
              {
                "id": "btn_t1",
                "label": "Template Btn",
                "spokenText": "Speech",
                "isActive": true,
                "playActionAsAuditoryCue": false,
                "buttonAction": {
                  "prompt": "Template Action"
                }
              }
            ]
        """.trimIndent()

        db.execSQL("""
            INSERT INTO templates (id, name, rows, columns, buttonConfigs, isBuiltIn, createdAt, orderIndex, scanPattern, rowNames)
            VALUES ('temp_1', 'My Temp', 4, 4, ?, 0, 123456789, 0, NULL, '[]')
        """, arrayOf(legacyTemplateButtonsJson))

        // Insert valid button usage stat
        db.execSQL("""
            INSERT INTO button_usage_stats (bookId, buttonConfigId, label, actionJson, usageCount, lastUsedAt)
            VALUES ('book_1', 'btn_1', 'Hello', ?, 5, 987654321)
        """, arrayOf("""{"prompt":"Say hello to user"}"""))

        AppDatabase.resetMigrationErrorsCount()
        
        // Migrate
        AppDatabase.MIGRATION_10_11.migrate(db)

        assertEquals(0, AppDatabase.migrationErrorsCount)

        // Verify buttons table contents
        val buttonsCursor = db.query("SELECT * FROM buttons ORDER BY globalIndex ASC")
        assertTrue(buttonsCursor.moveToFirst())

        // Validate btn_1
        assertEquals("btn_1", buttonsCursor.getString(buttonsCursor.getColumnIndexOrThrow("id")))
        assertEquals("page_1", buttonsCursor.getString(buttonsCursor.getColumnIndexOrThrow("pageId")))
        assertEquals(0, buttonsCursor.getInt(buttonsCursor.getColumnIndexOrThrow("globalIndex")))
        assertEquals("Hello", buttonsCursor.getString(buttonsCursor.getColumnIndexOrThrow("label")))
        assertEquals("Hello World", buttonsCursor.getString(buttonsCursor.getColumnIndexOrThrow("spokenText")))
        assertEquals(1, buttonsCursor.getInt(buttonsCursor.getColumnIndexOrThrow("isActive")))
        assertEquals(0, buttonsCursor.getInt(buttonsCursor.getColumnIndexOrThrow("playActionAsAuditoryCue")))
        assertTrue(buttonsCursor.getString(buttonsCursor.getColumnIndexOrThrow("buttonAction")).contains("Say hello to user"))
        assertTrue(buttonsCursor.getString(buttonsCursor.getColumnIndexOrThrow("auditoryCue")).contains("Speak hello"))

        // Validate btn_2
        assertTrue(buttonsCursor.moveToNext())
        assertEquals("btn_2", buttonsCursor.getString(buttonsCursor.getColumnIndexOrThrow("id")))
        assertEquals("page_1", buttonsCursor.getString(buttonsCursor.getColumnIndexOrThrow("pageId")))
        assertEquals(1, buttonsCursor.getInt(buttonsCursor.getColumnIndexOrThrow("globalIndex")))
        assertEquals("Go to Home", buttonsCursor.getString(buttonsCursor.getColumnIndexOrThrow("label")))
        assertTrue(buttonsCursor.isNull(buttonsCursor.getColumnIndexOrThrow("spokenText")))
        assertEquals(0, buttonsCursor.getInt(buttonsCursor.getColumnIndexOrThrow("isActive")))
        assertEquals(1, buttonsCursor.getInt(buttonsCursor.getColumnIndexOrThrow("playActionAsAuditoryCue")))
        assertTrue(buttonsCursor.getString(buttonsCursor.getColumnIndexOrThrow("buttonAction")).contains("home_page"))
        assertTrue(buttonsCursor.isNull(buttonsCursor.getColumnIndexOrThrow("auditoryCue")))

        assertFalse(buttonsCursor.moveToNext())
        buttonsCursor.close()

        // Verify pages table schema has been updated
        val pagesSchemaCursor = db.query("PRAGMA table_info(pages)")
        var hasButtonConfigs = false
        var hasLegacyButtonConfigs = false
        while (pagesSchemaCursor.moveToNext()) {
            val columnName = pagesSchemaCursor.getString(1)
            if (columnName == "buttonConfigs") hasButtonConfigs = true
            if (columnName == "legacy_buttonConfigs") hasLegacyButtonConfigs = true
        }
        pagesSchemaCursor.close()

        assertFalse(hasButtonConfigs)
        assertTrue(hasLegacyButtonConfigs)

        // Verify the original legacy configurations are preserved in pages
        val pageCursor = db.query("SELECT legacy_buttonConfigs FROM pages WHERE id = 'page_1'")
        assertTrue(pageCursor.moveToFirst())
        assertEquals(legacyButtonsJson, pageCursor.getString(0))
        pageCursor.close()

        // Verify templates are updated in-place
        val templateCursor = db.query("SELECT buttonConfigs FROM templates WHERE id = 'temp_1'")
        assertTrue(templateCursor.moveToFirst())
        val migratedTempJson = templateCursor.getString(0)
        assertTrue(migratedTempJson.contains("Template Action"))
        templateCursor.close()

        // Verify button usage stats are updated in-place
        val statsCursor = db.query("SELECT actionJson FROM button_usage_stats WHERE bookId = 'book_1' AND buttonConfigId = 'btn_1'")
        assertTrue(statsCursor.moveToFirst())
        val migratedStatAction = statsCursor.getString(0)
        assertTrue(migratedStatAction.contains("Say hello to user"))
        statsCursor.close()

        db.close()
    }

    @Test
    fun testMigration10To11_brokenJson() {
        val db = getInMemoryDb(10) { createVersion10Db(it) }

        // Insert malformed JSON arrays
        db.execSQL("""
            INSERT INTO pages (id, bookId, name, templateId, rows, columns, scanPattern, rowNames, orderIndex, createdAt, buttonConfigs)
            VALUES ('page_bad', 'book_1', 'Bad Page', NULL, 4, 4, NULL, '[]', 0, 123456789, ?)
        """, arrayOf("invalid_json_array{]}"))

        db.execSQL("""
            INSERT INTO templates (id, name, rows, columns, buttonConfigs, isBuiltIn, createdAt, orderIndex, scanPattern, rowNames)
            VALUES ('temp_bad', 'Bad Temp', 4, 4, ?, 0, 123456789, 0, NULL, '[]')
        """, arrayOf("{bad_json}"))

        AppDatabase.resetMigrationErrorsCount()

        // Migrate
        AppDatabase.MIGRATION_10_11.migrate(db)

        // Count should be 2 because both page and template migrations threw exception for bad json
        assertEquals(2, AppDatabase.migrationErrorsCount)

        // Verify no buttons were inserted into the buttons table
        val buttonsCursor = db.query("SELECT COUNT(*) FROM buttons")
        assertTrue(buttonsCursor.moveToFirst())
        assertEquals(0, buttonsCursor.getInt(0))
        buttonsCursor.close()

        // Verify the legacy json was kept inside pages anyway
        val pageCursor = db.query("SELECT legacy_buttonConfigs FROM pages WHERE id = 'page_bad'")
        assertTrue(pageCursor.moveToFirst())
        assertEquals("invalid_json_array{]}", pageCursor.getString(0))
        pageCursor.close()

        db.close()
    }

    @Test
    fun testMigration33To34() {
        val db = getInMemoryDb(33) { createVersion33Db(it) }

        db.execSQL("""
            INSERT INTO books (id, name, createdAt, updatedAt, actionLogLimit, limitScanCycles, scanCycleLimit, logIgnoredActions, logStopActions, versionSequence)
            VALUES ('book_33', 'My Book 33', 100, 200, 100, 0, 2, 1, 1, 0)
        """)

        // Migrate
        AppDatabase.MIGRATION_33_34.migrate(db)

        // Verify new column exists
        val schemaCursor = db.query("PRAGMA table_info(books)")
        var hasActionLogsStorage = false
        while (schemaCursor.moveToNext()) {
            val columnName = schemaCursor.getString(1)
            if (columnName == "actionLogsStorage") {
                hasActionLogsStorage = true
                break
            }
        }
        schemaCursor.close()
        assertTrue(hasActionLogsStorage)

        // Verify book existing data is preserved and new column is null
        val bookCursor = db.query("SELECT * FROM books WHERE id = 'book_33'")
        assertTrue(bookCursor.moveToFirst())
        assertEquals("My Book 33", bookCursor.getString(bookCursor.getColumnIndexOrThrow("name")))
        assertEquals(100, bookCursor.getInt(bookCursor.getColumnIndexOrThrow("createdAt")))
        assertTrue(bookCursor.isNull(bookCursor.getColumnIndexOrThrow("actionLogsStorage")))
        bookCursor.close()

        db.close()
    }
}
