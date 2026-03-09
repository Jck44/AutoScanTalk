package com.andreas_kratzer.ghosttalk.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.andreas_kratzer.ghosttalk.model.Book
import com.andreas_kratzer.ghosttalk.model.ButtonUsageStat
import com.andreas_kratzer.ghosttalk.model.Page
import com.andreas_kratzer.ghosttalk.model.PageTemplate
import java.util.UUID
import kotlinx.serialization.json.*
import kotlinx.serialization.encodeToString
import android.util.Log

import com.andreas_kratzer.ghosttalk.data.entities.ButtonEntity

@Database(entities = [Page::class, Book::class, ButtonUsageStat::class, PageTemplate::class, ButtonEntity::class], version = 11, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun pageDao(): PageDao
    abstract fun bookDao(): BookDao
    abstract fun buttonUsageDao(): ButtonUsageDao
    abstract fun templateDao(): TemplateDao
    abstract fun buttonDao(): ButtonDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `books` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )

                val defaultBookId = "book-default-" + UUID.randomUUID().toString()
                val currentTime = System.currentTimeMillis()
                db.execSQL(
                    "INSERT INTO `books` (`id`, `name`, `createdAt`) VALUES ('$defaultBookId', 'Standardbuch', $currentTime)"
                )

                db.execSQL(
                    "ALTER TABLE `pages` ADD COLUMN `bookId` TEXT NOT NULL DEFAULT '$defaultBookId'"
                )
            }
        }

        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `pages` ADD COLUMN `scanPattern` TEXT DEFAULT NULL"
                )
                db.execSQL(
                    "ALTER TABLE `pages` ADD COLUMN `rowNames` TEXT NOT NULL DEFAULT '[]'"
                )
            }
        }

        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `button_usage_stats` (" +
                    "`bookId` TEXT NOT NULL, " +
                    "`buttonConfigId` TEXT NOT NULL, " +
                    "`label` TEXT NOT NULL, " +
                    "`actionJson` TEXT NOT NULL, " +
                    "`usageCount` INTEGER NOT NULL DEFAULT 0, " +
                    "`lastUsedAt` INTEGER NOT NULL DEFAULT 0, " +
                    "PRIMARY KEY(`bookId`, `buttonConfigId`))"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_button_usage_stats_bookId_usageCount` ON `button_usage_stats` (`bookId`, `usageCount`)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `templates` (" +
                    "`id` TEXT NOT NULL, " +
                    "`name` TEXT NOT NULL, " +
                    "`rows` INTEGER NOT NULL, " +
                    "`columns` INTEGER NOT NULL, " +
                    "`buttonConfigs` TEXT NOT NULL, " +
                    "`isBuiltIn` INTEGER NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`id`))"
                )
            }
        }

        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `pages` ADD COLUMN `orderIndex` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `pages` ADD COLUMN `createdAt` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `templates` ADD COLUMN `orderIndex` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_5_6: Migration = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `books` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE `books` SET `updatedAt` = `createdAt` WHERE `updatedAt` = 0")
            }
        }

        val MIGRATION_6_7: Migration = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Placeholder for potential data migrations within JSON strings
            }
        }

        val MIGRATION_7_8: Migration = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `templates` ADD COLUMN `scanPattern` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `templates` ADD COLUMN `rowNames` TEXT NOT NULL DEFAULT '[]'")
            }
        }

        val MIGRATION_8_9: Migration = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Version bump to fix schema mismatch
            }
        }

        val MIGRATION_9_10: Migration = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `pages` ADD COLUMN `templateId` TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_10_11: Migration = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Create the new buttons table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `buttons` (
                        `id` TEXT NOT NULL, 
                        `pageId` TEXT NOT NULL, 
                        `globalIndex` INTEGER NOT NULL, 
                        `label` TEXT NOT NULL, 
                        `spokenText` TEXT, 
                        `auditoryCue` TEXT, 
                        `buttonAction` TEXT NOT NULL, 
                        `isActive` INTEGER NOT NULL, 
                        `playActionAsAuditoryCue` INTEGER NOT NULL, 
                        PRIMARY KEY(`id`), 
                        FOREIGN KEY(`pageId`) REFERENCES `pages`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE 
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_buttons_pageId` ON `buttons` (`pageId`)")

                // 2. Migrate existing buttonConfigs from JSON to the new buttons table
                val json = Json {
                    ignoreUnknownKeys = true
                    encodeDefaults = true
                }

                val cursor = db.query("SELECT id, buttonConfigs FROM pages")
                try {
                    while (cursor.moveToNext()) {
                        val pageId = cursor.getString(0)
                        val buttonConfigsJson = cursor.getString(1)
                        if (!buttonConfigsJson.isNullOrBlank()) {
                            try {
                                val jsonElement = json.parseToJsonElement(buttonConfigsJson)
                                val jsonArray = jsonElement.jsonArray
                                
                                jsonArray.forEachIndexed { index, configElement ->
                                    val configObj = configElement as? JsonObject
                                    if (configObj != null && configObj.isNotEmpty()) {
                                        val id = configObj["id"]?.jsonPrimitive?.content ?: UUID.randomUUID().toString()
                                        val label = configObj["label"]?.jsonPrimitive?.content ?: ""
                                        val spokenText = configObj["spokenText"]?.jsonPrimitive?.content
                                        val isActive = configObj["isActive"]?.jsonPrimitive?.boolean ?: true
                                        val playActionAsAuditoryCue = configObj["playActionAsAuditoryCue"]?.jsonPrimitive?.boolean ?: false
                                        
                                        // Detect AuditoryCue type manually (Gson legacy compat)
                                        val cueObj = configObj["auditoryCue"] as? JsonObject
                                        val auditoryCue = if (cueObj != null) {
                                            if (cueObj.containsKey("text")) {
                                                com.andreas_kratzer.ghosttalk.model.AuditoryCue.TextToSpeechCue(
                                                    text = cueObj["text"]?.jsonPrimitive?.content ?: ""
                                                )
                                            } else {
                                                null
                                            }
                                        } else {
                                            null
                                        }

                                        // Detect ButtonAction type manually (Gson legacy compat)
                                        val actionObj = configObj["buttonAction"] as? JsonObject
                                        val action: com.andreas_kratzer.ghosttalk.model.ButtonAction = if (actionObj != null) {
                                            if (actionObj.containsKey("pageId") || actionObj.containsKey("targetPageId")) {
                                                val targetId = actionObj["pageId"]?.jsonPrimitive?.content 
                                                    ?: actionObj["targetPageId"]?.jsonPrimitive?.content 
                                                    ?: ""
                                                com.andreas_kratzer.ghosttalk.model.NavigateToPageButtonAction(targetId)
                                            } else if (actionObj.containsKey("rank")) {
                                                val rank = actionObj["rank"]?.jsonPrimitive?.int ?: 1
                                                com.andreas_kratzer.ghosttalk.model.FrequentActionButtonAction(rank)
                                            } else if (actionObj.containsKey("prompt")) {
                                                com.andreas_kratzer.ghosttalk.model.GeminiButtonAction(
                                                    prompt = actionObj["prompt"]?.jsonPrimitive?.content ?: ""
                                                )
                                            } else {
                                                com.andreas_kratzer.ghosttalk.model.SpeakTextButtonAction()
                                            }
                                        } else {
                                            com.andreas_kratzer.ghosttalk.model.SpeakTextButtonAction()
                                        }

                                        val auditoryCueJson = auditoryCue?.let { json.encodeToString<com.andreas_kratzer.ghosttalk.model.AuditoryCue>(it) }
                                        val buttonActionJson = json.encodeToString<com.andreas_kratzer.ghosttalk.model.ButtonAction>(action)
                                        
                                        db.execSQL(
                                            "INSERT INTO `buttons` (id, pageId, globalIndex, label, spokenText, auditoryCue, buttonAction, isActive, playActionAsAuditoryCue) " +
                                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                                            arrayOf<Any?>(
                                                id,
                                                pageId,
                                                index,
                                                label,
                                                spokenText,
                                                auditoryCueJson,
                                                buttonActionJson,
                                                if (isActive) 1 else 0,
                                                if (playActionAsAuditoryCue) 1 else 0
                                            )
                                        )
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("AppDatabase", "Failed to migrate buttonConfigs for page $pageId", e)
                            }
                        }
                    }
                } finally {
                    cursor.close()
                }

                // 3. Create a temporary table for pages without the buttonConfigs column, 
                // but preserve the original JSON in legacy_buttonConfigs as a safety backup.
                // TODO 2026-04-09: Check if legacy_buttonConfigs can be removed after successful migration period.
                db.execSQL("""
                    CREATE TABLE `pages_new` (
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
                        `legacy_buttonConfigs` TEXT, 
                        PRIMARY KEY(`id`)
                    )
                """)
                
                // Copy data from old pages to new pages including the JSON blob
                db.execSQL("""
                    INSERT INTO `pages_new` (id, bookId, name, templateId, `rows`, `columns`, scanPattern, rowNames, orderIndex, createdAt, legacy_buttonConfigs)
                    SELECT id, bookId, name, templateId, `rows`, `columns`, scanPattern, rowNames, orderIndex, createdAt, buttonConfigs FROM pages
                """)
                
                // Drop old table and rename new one
                db.execSQL("DROP TABLE pages")
                db.execSQL("ALTER TABLE pages_new RENAME TO pages")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ghosttalk_database"
                )
                .addMigrations(
                    MIGRATION_1_2, 
                    MIGRATION_2_3, 
                    MIGRATION_3_4, 
                    MIGRATION_4_5, 
                    MIGRATION_5_6, 
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                    MIGRATION_8_9,
                    MIGRATION_9_10,
                    MIGRATION_10_11
                )
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
