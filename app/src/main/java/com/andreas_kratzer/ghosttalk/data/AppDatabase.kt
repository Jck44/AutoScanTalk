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

@Database(entities = [Page::class, Book::class, ButtonUsageStat::class, PageTemplate::class], version = 5, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun pageDao(): PageDao
    abstract fun bookDao(): BookDao
    abstract fun buttonUsageDao(): ButtonUsageDao
    abstract fun templateDao(): TemplateDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Create the new books table
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `books` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )

                // 2. Generate a Default Book ID and insert it
                val defaultBookId = "book-default-" + UUID.randomUUID().toString()
                val currentTime = System.currentTimeMillis()
                db.execSQL(
                    "INSERT INTO `books` (`id`, `name`, `createdAt`) VALUES ('$defaultBookId', 'Standardbuch', $currentTime)"
                )

                // 3. Add the bookId column to the existing pages table
                // SQLite ALTER TABLE ADD COLUMN allows adding a column. We can set a DEFAULT value or allow NULL.
                // Since our model defines val bookId: String (NOT NULL), we must provide a default for existing rows.
                db.execSQL(
                    "ALTER TABLE `pages` ADD COLUMN `bookId` TEXT NOT NULL DEFAULT '$defaultBookId'"
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add scanPattern (nullable) and rowNames (not null with default empty JSON array) to pages
                db.execSQL(
                    "ALTER TABLE `pages` ADD COLUMN `scanPattern` TEXT DEFAULT NULL"
                )
                db.execSQL(
                    "ALTER TABLE `pages` ADD COLUMN `rowNames` TEXT NOT NULL DEFAULT '[]'"
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
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

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add orderIndex and createdAt to pages
                db.execSQL("ALTER TABLE `pages` ADD COLUMN `orderIndex` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `pages` ADD COLUMN `createdAt` INTEGER NOT NULL DEFAULT 0")
                
                // Add orderIndex to templates (createdAt was already there)
                db.execSQL("ALTER TABLE `templates` ADD COLUMN `orderIndex` INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ghosttalk_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .fallbackToDestructiveMigration(true)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
