package com.andreas_kratzer.ghosttalk.core.database

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.andreas_kratzer.ghosttalk.core.model.AuditoryCue
import com.andreas_kratzer.ghosttalk.core.model.Book
import com.andreas_kratzer.ghosttalk.core.model.ButtonAction
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.ButtonUsageStat
import com.andreas_kratzer.ghosttalk.core.model.FrequentActionButtonAction
import com.andreas_kratzer.ghosttalk.core.model.GeminiButtonAction
import com.andreas_kratzer.ghosttalk.core.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.model.PageTemplate
import com.andreas_kratzer.ghosttalk.core.model.SpeakTextButtonAction
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

@Database(
    entities = [
        Page::class, Book::class, ButtonUsageStat::class, PageTemplate::class,
        ButtonEntity::class, ButtonUsageHistoryEntity::class, ButtonTemplateEntity::class,
        UserModeSessionEntity::class, VocalProfileEntity::class, DeletedEntity::class,
        SettingsProfileEntity::class
    ],
    version = 34,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun pageDao(): PageDao
    abstract fun bookDao(): BookDao
    abstract fun buttonUsageDao(): ButtonUsageDao
    abstract fun templateDao(): TemplateDao
    abstract fun buttonDao(): ButtonDao
    abstract fun buttonTemplateDao(): ButtonTemplateDao
    abstract fun userModeSessionDao(): UserModeSessionDao
    abstract fun vocalProfileDao(): VocalProfileDao
    abstract fun deletedEntityDao(): DeletedEntityDao
    abstract fun settingsProfileDao(): SettingsProfileDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        @Volatile
        var migrationErrorsCount = 0
            internal set

        fun resetMigrationErrorsCount() {
            migrationErrorsCount = 0
        }

        val MIGRATION_33_34: Migration = object : Migration(33, 34) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `books` ADD COLUMN `actionLogsStorage` TEXT")
            }
        }

        val MIGRATION_32_33: Migration = object : Migration(32, 33) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `settings_profiles` (
                        `id` TEXT NOT NULL, 
                        `name` TEXT NOT NULL, 
                        `configJson` TEXT NOT NULL, 
                        `profileVersionSequence` INTEGER NOT NULL DEFAULT 0, 
                        `updatedAt` INTEGER NOT NULL, 
                        `isDeleted` INTEGER NOT NULL DEFAULT 0, 
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_29_30: Migration = object : Migration(29, 30) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add positive/negative template columns for V2 false-positive training.
                db.execSQL("ALTER TABLE `vocal_profiles` ADD COLUMN `positiveTemplatesJson` TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE `vocal_profiles` ADD COLUMN `negativeTemplatesJson` TEXT NOT NULL DEFAULT '[]'")
                // Rename the old referenceEmbedding column is not supported by SQLite ALTER TABLE;
                // we keep the old column as-is (it maps to referenceEmbeddingJson with ColumnInfo).
            }
        }

        val MIGRATION_31_32: Migration = object : Migration(31, 32) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `books` ADD COLUMN `versionSequence` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_30_31: Migration = object : Migration(30, 31) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `deleted_entities` (
                        `entityId` TEXT NOT NULL, 
                        `entityType` TEXT NOT NULL, 
                        `bookId` TEXT NOT NULL, 
                        `deletedAt` INTEGER NOT NULL, 
                        PRIMARY KEY(`entityId`)
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_deleted_entities_bookId` ON `deleted_entities` (`bookId`)")
            }
        }

        val MIGRATION_28_29: Migration = object : Migration(28, 29) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `vocal_profiles` (
                        `id` TEXT NOT NULL, 
                        `name` TEXT NOT NULL, 
                        `referenceEmbedding` TEXT NOT NULL, 
                        `buttonAction` TEXT, 
                        `spokenText` TEXT, 
                        `isActive` INTEGER NOT NULL DEFAULT 1, 
                        PRIMARY KEY(`id`)
                    )
                """)
            }
        }

        val MIGRATION_27_28: Migration = object : Migration(27, 28) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `button_usage_history` ADD COLUMN `scanCyclesBeforeClick` INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE `button_usage_history` ADD COLUMN `isAccidental` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `button_usage_history` ADD COLUMN `intendedButtonId` TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_26_27: Migration = object : Migration(26, 27) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val currentTime = System.currentTimeMillis()
                db.execSQL("ALTER TABLE `pages` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT $currentTime")
                db.execSQL("ALTER TABLE `buttons` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT $currentTime")
            }
        }

        val MIGRATION_25_26: Migration = object : Migration(25, 26) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `button_usage_history` ADD COLUMN `isHardwareTriggered` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_24_25: Migration = object : Migration(24, 25) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `button_usage_history` ADD COLUMN `wifiSsid` TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_23_24: Migration = object : Migration(23, 24) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `button_usage_history` ADD COLUMN `isTouchIntervention` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_22_23: Migration = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `button_usage_stats` ADD COLUMN `firstUsedAt` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE `button_usage_stats` SET `firstUsedAt` = `lastUsedAt` WHERE `firstUsedAt` = 0")
            }
        }

        val MIGRATION_21_22: Migration = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `button_usage_history` ADD COLUMN `reactionTimeMs` INTEGER DEFAULT NULL")
            }
        }

        val MIGRATION_20_21: Migration = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `button_usage_history` ADD COLUMN `sessionId` INTEGER DEFAULT NULL")
            }
        }

        val MIGRATION_19_20: Migration = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `button_usage_history` ADD COLUMN `latitude` REAL DEFAULT NULL")
                db.execSQL("ALTER TABLE `button_usage_history` ADD COLUMN `longitude` REAL DEFAULT NULL")
                db.execSQL("""
                    CREATE INDEX IF NOT EXISTS `index_button_usage_history_bookId_buttonId_timestamp` 
                    ON `button_usage_history` (`bookId`, `buttonId`, `timestamp`)
                """)
            }
        }
        
        val MIGRATION_16_17: Migration = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `button_templates` (
                        `id` TEXT NOT NULL, 
                        `name` TEXT NOT NULL, 
                        `isBuiltIn` INTEGER NOT NULL, 
                        `buttonConfig` TEXT NOT NULL, 
                        `orderIndex` INTEGER NOT NULL, 
                        PRIMARY KEY(`id`)
                    )
                """)
            }
        }

        val MIGRATION_17_18: Migration = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `buttons` ADD COLUMN `spokenTextMode` TEXT NOT NULL DEFAULT 'TTS'")
                db.execSQL("ALTER TABLE `buttons` ADD COLUMN `audioFileName` TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_18_19: Migration = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `user_mode_sessions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `bookId` TEXT NOT NULL, 
                        `startTime` INTEGER NOT NULL, 
                        `endTime` INTEGER NOT NULL
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_user_mode_sessions_bookId` ON `user_mode_sessions` (`bookId`)")
            }
        }

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

                val json = Json {
                    ignoreUnknownKeys = true
                    encodeDefaults = true
                }

                /**
                 * Helper to extract data from legacy Gson JSON which might be wrapped in {"type": "...", "data": {...}}
                 */
                fun extractLegacyObject(element: JsonElement?): JsonObject? {
                    val obj = element as? JsonObject ?: return null
                    return if (obj.containsKey("type") && obj.containsKey("data")) {
                        obj["data"] as? JsonObject
                    } else {
                        obj
                    }
                }

                fun migrateAction(actionElement: JsonElement?): ButtonAction {
                    val actionObj = extractLegacyObject(actionElement)
                    if (actionObj == null || actionObj.isEmpty()) return SpeakTextButtonAction()
                    
                    return if (actionObj.containsKey("pageId") || actionObj.containsKey("targetPageId")) {
                        val targetId = actionObj["pageId"]?.jsonPrimitive?.content 
                            ?: actionObj["targetPageId"]?.jsonPrimitive?.content 
                            ?: ""
                        NavigateToPageButtonAction(targetId)
                    } else if (actionObj.containsKey("rank")) {
                        val rank = actionObj["rank"]?.jsonPrimitive?.int ?: 1
                        FrequentActionButtonAction(rank)
                    } else if (actionObj.containsKey("prompt")) {
                        GeminiButtonAction(prompt = actionObj["prompt"]?.jsonPrimitive?.content ?: "")
                    } else {
                        SpeakTextButtonAction()
                    }
                }

                fun migrateCue(cueElement: JsonElement?): AuditoryCue? {
                    val cueObj = extractLegacyObject(cueElement)
                    if (cueObj == null || cueObj.isEmpty()) return null
                    
                    return if (cueObj.containsKey("text")) {
                        AuditoryCue.TextToSpeechCue(text = cueObj["text"]?.jsonPrimitive?.content ?: "")
                    } else {
                        null
                    }
                }

                // 2. Migrate Pages to Buttons table
                val pageCursor = db.query("SELECT id, buttonConfigs FROM pages")
                try {
                    while (pageCursor.moveToNext()) {
                        val pageId = pageCursor.getString(0)
                        val buttonConfigsJson = pageCursor.getString(1)
                        if (!buttonConfigsJson.isNullOrBlank()) {
                            try {
                                val jsonArray = json.parseToJsonElement(buttonConfigsJson).jsonArray
                                jsonArray.forEachIndexed { index, configElement ->
                                    val configObj = configElement as? JsonObject
                                    if (configObj != null && configObj.isNotEmpty()) {
                                        val id = configObj["id"]?.jsonPrimitive?.content ?: UUID.randomUUID().toString()
                                        val label = configObj["label"]?.jsonPrimitive?.content ?: ""
                                        val spokenText = configObj["spokenText"]?.jsonPrimitive?.content
                                        val isActive = configObj["isActive"]?.jsonPrimitive?.boolean ?: true
                                        val playActionAsAuditoryCue = configObj["playActionAsAuditoryCue"]?.jsonPrimitive?.boolean ?: false
                                        
                                        val action = migrateAction(configObj["buttonAction"])
                                        val auditoryCue = migrateCue(configObj["auditoryCue"])

                                        val auditoryCueJson = auditoryCue?.let { json.encodeToString<AuditoryCue>(it) }
                                        val buttonActionJson = json.encodeToString<ButtonAction>(action)
                                        
                                        db.execSQL(
                                            "INSERT INTO `buttons` (id, pageId, globalIndex, label, spokenText, auditoryCue, buttonAction, isActive, playActionAsAuditoryCue) " +
                                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                                            arrayOf<Any?>(id, pageId, index, label, spokenText, auditoryCueJson, buttonActionJson, if (isActive) 1 else 0, if (playActionAsAuditoryCue) 1 else 0)
                                        )
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("AppDatabase", "Failed to migrate buttonConfigs for page $pageId", e)
                                migrationErrorsCount++
                            }
                        }
                    }
                } finally {
                    pageCursor.close()
                }

                // 3. Migrate Templates (JSON to JSON migration)
                val templateCursor = db.query("SELECT id, buttonConfigs FROM templates")
                try {
                    while (templateCursor.moveToNext()) {
                        val templateId = templateCursor.getString(0)
                        val configsJson = templateCursor.getString(1)
                        if (!configsJson.isNullOrBlank()) {
                            try {
                                val oldArray = json.parseToJsonElement(configsJson).jsonArray
                                val newConfigs = oldArray.map { configElement ->
                                    val configObj = configElement as? JsonObject
                                    if (configObj == null || configObj.isEmpty()) return@map null
                                    
                                    ButtonConfig(
                                        id = configObj["id"]?.jsonPrimitive?.content ?: UUID.randomUUID().toString(),
                                        label = configObj["label"]?.jsonPrimitive?.content ?: "",
                                        spokenText = configObj["spokenText"]?.jsonPrimitive?.content,
                                        isActive = configObj["isActive"]?.jsonPrimitive?.boolean ?: true,
                                        playActionAsAuditoryCue = configObj["playActionAsAuditoryCue"]?.jsonPrimitive?.boolean ?: false,
                                        buttonAction = migrateAction(configObj["buttonAction"]),
                                        auditoryCue = migrateCue(configObj["auditoryCue"])
                                    )
                                }
                                val newConfigsJson = json.encodeToString<List<ButtonConfig?>>(newConfigs)
                                db.execSQL("UPDATE templates SET buttonConfigs = ? WHERE id = ?", arrayOf(newConfigsJson, templateId))
                            } catch (e: Exception) {
                                Log.e("AppDatabase", "Failed to migrate templates for id $templateId", e)
                                migrationErrorsCount++
                            }
                        }
                    }
                } finally {
                    templateCursor.close()
                }

                // 4. Migrate Button Usage Stats (actionJson migration)
                val statsCursor = db.query("SELECT bookId, buttonConfigId, actionJson FROM button_usage_stats")
                try {
                    while (statsCursor.moveToNext()) {
                        val bookId = statsCursor.getString(0)
                        val configId = statsCursor.getString(1)
                        val oldActionJson = statsCursor.getString(2)
                        if (!oldActionJson.isNullOrBlank()) {
                            try {
                                val action = migrateAction(json.parseToJsonElement(oldActionJson))
                                val newActionJson = json.encodeToString<ButtonAction>(action)
                                db.execSQL("UPDATE button_usage_stats SET actionJson = ? WHERE bookId = ? AND buttonConfigId = ?", arrayOf(newActionJson, bookId, configId))
                            } catch (e: Exception) {
                                Log.e("AppDatabase", "Failed to migrate usage stat for $configId", e)
                                migrationErrorsCount++
                            }
                        }
                    }
                } finally {
                    statsCursor.close()
                }

                // 5. Finalize Pages Table (Preserve legacy JSON as backup)
                db.execSQL("""
                    CREATE TABLE `pages_new` (
                        `id` TEXT NOT NULL, `bookId` TEXT NOT NULL, `name` TEXT NOT NULL, `templateId` TEXT, 
                        `rows` INTEGER NOT NULL, `columns` INTEGER NOT NULL, `scanPattern` TEXT, 
                        `rowNames` TEXT NOT NULL, `orderIndex` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, 
                        `legacy_buttonConfigs` TEXT, PRIMARY KEY(`id`)
                    )
                """)
                db.execSQL("""
                    INSERT INTO `pages_new` (id, bookId, name, templateId, `rows`, `columns`, scanPattern, rowNames, orderIndex, createdAt, legacy_buttonConfigs)
                    SELECT id, bookId, name, templateId, `rows`, `columns`, scanPattern, rowNames, orderIndex, createdAt, buttonConfigs FROM pages
                """)
                db.execSQL("DROP TABLE pages")
                db.execSQL("ALTER TABLE pages_new RENAME TO pages")
            }
        }


        val MIGRATION_11_12: Migration = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `books` ADD COLUMN `actionLogLimit` INTEGER NOT NULL DEFAULT 100")
            }
        }
        
        val MIGRATION_12_13: Migration = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `books` ADD COLUMN `limitScanCycles` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `books` ADD COLUMN `scanCycleLimit` INTEGER NOT NULL DEFAULT 2")
            }
        }

        val MIGRATION_13_14: Migration = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `books` ADD COLUMN `logIgnoredActions` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `books` ADD COLUMN `logStopActions` INTEGER NOT NULL DEFAULT 1")
            }
        }

        val MIGRATION_14_15: Migration = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `button_usage_history` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `bookId` TEXT NOT NULL, 
                        `timestamp` INTEGER NOT NULL, 
                        `label` TEXT NOT NULL, 
                        `actionType` TEXT NOT NULL, 
                        `imagePath` TEXT, 
                        `buttonId` TEXT, 
                        `pageId` TEXT, 
                        `geminiResponse` TEXT
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_button_usage_history_bookId_timestamp` ON `button_usage_history` (`bookId`, `timestamp`)")
            }
        }

        val MIGRATION_15_16: Migration = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `button_usage_stats` ADD COLUMN `pageId` TEXT NOT NULL DEFAULT ''")
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
                    MIGRATION_10_11,
                    MIGRATION_11_12,
                    MIGRATION_12_13,
                    MIGRATION_13_14,
                    MIGRATION_14_15,
                    MIGRATION_15_16,
                    MIGRATION_16_17,
                    MIGRATION_17_18,
                    MIGRATION_18_19,
                    MIGRATION_19_20,
                    MIGRATION_20_21,
                    MIGRATION_21_22,
                    MIGRATION_22_23,
                    MIGRATION_23_24,
                    MIGRATION_24_25,
                    MIGRATION_25_26,
                    MIGRATION_26_27,
                    MIGRATION_27_28,
                    MIGRATION_28_29,
                    MIGRATION_29_30,
                    MIGRATION_30_31,
                    MIGRATION_31_32,
                    MIGRATION_32_33,
                    MIGRATION_33_34
                )
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
