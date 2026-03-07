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

@Database(entities = [Page::class, Book::class, ButtonUsageStat::class, PageTemplate::class], version = 10, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun pageDao(): PageDao
    abstract fun bookDao(): BookDao
    abstract fun buttonUsageDao(): ButtonUsageDao
    abstract fun templateDao(): TemplateDao

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
                    MIGRATION_9_10
                )
                .fallbackToDestructiveMigration(true)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
