package com.andreas_kratzer.ghosttalk.core.data.impl

import androidx.room.withTransaction
import com.andreas_kratzer.ghosttalk.core.data.PageRepository
import com.andreas_kratzer.ghosttalk.core.database.AppDatabase
import com.andreas_kratzer.ghosttalk.core.database.ButtonDao
import com.andreas_kratzer.ghosttalk.core.database.PageDao
import com.andreas_kratzer.ghosttalk.core.database.toButtonEntities
import com.andreas_kratzer.ghosttalk.core.model.ControlDeviceButtonAction
import com.andreas_kratzer.ghosttalk.core.model.DeviceActionType
import com.andreas_kratzer.ghosttalk.core.model.Page
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PageRepositoryImpl(
    private val pageDao: PageDao,
    private val buttonDao: ButtonDao,
    private val appDatabase: AppDatabase
) : PageRepository {

    override fun getAllPagesFlow(): Flow<List<Page>> {
        return pageDao.getAllPagesFlow()
    }

    override fun getPagesForBookFlow(bookId: String): Flow<List<Page>> {
        return pageDao.getPagesForBookFlow(bookId)
    }

    override suspend fun getPageById(id: String): Page? {
        return pageDao.getPageById(id)
    }

    override suspend fun getAllPages(): List<Page> {
        return pageDao.getAllPages()
    }

    override suspend fun getPagesForBook(bookId: String): List<Page> {
        return pageDao.getPagesForBook(bookId)
    }

    override suspend fun insertPage(page: Page) {
        val now = System.currentTimeMillis()
        val updatedPage = page.copy(
            updatedAt = now,
            buttonConfigs = page.buttonConfigs.map { it?.copy(updatedAt = now) }
        )
        appDatabase.withTransaction {
            pageDao.insertPageEntity(updatedPage)
            buttonDao.insertButtons(updatedPage.toButtonEntities())
        }
    }

    override suspend fun insertPageRaw(page: Page) {
        appDatabase.withTransaction {
            pageDao.insertPageEntity(page)
            buttonDao.insertButtons(page.toButtonEntities())
        }
    }

    override suspend fun updatePage(page: Page) {
        val now = System.currentTimeMillis()
        val finalUpdatedAt = maxOf(now, page.updatedAt)
        val updatedPage = page.copy(
            updatedAt = finalUpdatedAt
        )
        appDatabase.withTransaction {
            // Diff: welche Button-IDs verschwinden mit diesem Update?
            val oldButtonIds = buttonDao.getButtonsForPage(updatedPage.id).map { it.id }.toSet()
            val newButtonIds = updatedPage.buttonConfigs.mapNotNull { it?.id }.toSet()
            (oldButtonIds - newButtonIds).forEach { deletedId ->
                appDatabase.deletedEntityDao().insertDeletedEntity(
                    com.andreas_kratzer.ghosttalk.core.database.DeletedEntity(
                        entityId = deletedId,
                        entityType = "BUTTON",
                        bookId = updatedPage.bookId
                    )
                )
            }
            pageDao.updatePageEntity(updatedPage)
            // Refresh buttons: delete old and insert new
            buttonDao.deleteButtonsForPage(updatedPage.id)
            buttonDao.insertButtons(updatedPage.toButtonEntities())
        }
    }

    override suspend fun updatePageSettingsOnly(page: Page) {
        pageDao.updatePageEntity(page)
    }

    override suspend fun movePages(fromPage: Page, toPage: Page) {
        appDatabase.withTransaction {
            updatePage(fromPage)
            updatePage(toPage)
        }
    }

    override suspend fun deletePage(page: Page) {
        appDatabase.withTransaction {
            pageDao.deletePageEntity(page)
            appDatabase.deletedEntityDao().insertDeletedEntity(
                com.andreas_kratzer.ghosttalk.core.database.DeletedEntity(
                    entityId = page.id,
                    entityType = "PAGE",
                    bookId = page.bookId
                )
            )
        }
    }

    // IMPORTANT: Only use for import! Does NOT create tombstones.
    override suspend fun deletePagesForBook(bookId: String) {
        pageDao.deletePagesForBook(bookId)
    }

    override suspend fun duplicatePage(pageId: String, duplicateSuffix: String): String? {
        return appDatabase.withTransaction {
            val original = pageDao.getPageWithButtonsById(pageId) ?: return@withTransaction null
            val newPageId = java.util.UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            
            val newPage = original.page.copy(
                id = newPageId,
                name = "${original.page.name}${duplicateSuffix}",
                createdAt = now,
                updatedAt = now
            )
            
            val newButtons = original.buttons.map { entity ->
                entity.copy(
                    id = java.util.UUID.randomUUID().toString(),
                    pageId = newPageId,
                    updatedAt = now
                )
            }
            
            pageDao.insertPageEntity(newPage)
            buttonDao.insertButtons(newButtons)
            
            // Update the book's updatedAt timestamp
            appDatabase.bookDao().updateLastModified(original.page.bookId, now)
            
            newPageId
        }
    }

    override fun getUsedTemplateIdsFlow(): Flow<Set<String>> {
        return pageDao.getAllPagesFlow().map { pages ->
            pages.mapNotNull { it.templateId }.toSet()
        }
    }

    override suspend fun deleteEmptyButtons(): Int {
        // Maintenance: empty buttons (label == "") do not require tombstones as they carry no configuration
        // and resurrecting them empty in concurrent scenarios is harmless/acceptable.
        return buttonDao.deleteEmptyButtons()
    }

    override suspend fun updatePageName(pageId: String, name: String) {
        pageDao.updatePageName(pageId, name, System.currentTimeMillis())
    }

    override suspend fun updatePageScanPattern(pageId: String, scanPattern: String?) {
        pageDao.updatePageScanPattern(pageId, scanPattern, System.currentTimeMillis())
    }

    override suspend fun updatePageRowNames(pageId: String, rowNames: List<String>) {
        pageDao.updatePageRowNames(pageId, rowNames, System.currentTimeMillis())
    }

    override suspend fun updatePageGridSize(pageId: String, rows: Int, columns: Int) {
        pageDao.updatePageGridSize(pageId, rows, columns, System.currentTimeMillis())
    }

    override suspend fun <R> runInTransaction(block: suspend () -> R): R {
        return appDatabase.withTransaction(block)
    }

    override suspend fun purgeInstallUpdateButtons() {
        appDatabase.withTransaction {
            val allPages = getAllPages()
            for (page in allPages) {
                val hasUpdateAction = page.buttonConfigs.any { config ->
                    config?.buttonAction is ControlDeviceButtonAction &&
                            (config.buttonAction as ControlDeviceButtonAction).actionType == DeviceActionType.INSTALL_UPDATE
                }
                if (hasUpdateAction) {
                    val updatedButtonConfigs = page.buttonConfigs.map { config ->
                        if (config?.buttonAction is ControlDeviceButtonAction &&
                            (config.buttonAction as ControlDeviceButtonAction).actionType == DeviceActionType.INSTALL_UPDATE
                        ) {
                            // Insert tombstone for the deleted button
                            appDatabase.deletedEntityDao().insertDeletedEntity(
                                com.andreas_kratzer.ghosttalk.core.database.DeletedEntity(
                                    entityId = config.id,
                                    entityType = "BUTTON",
                                    bookId = page.bookId
                                )
                            )
                            null
                        } else {
                            config
                        }
                    }
                    val now = System.currentTimeMillis()
                    val updatedPage = page.copy(
                        buttonConfigs = updatedButtonConfigs,
                        updatedAt = now
                    )
                    pageDao.updatePageEntity(updatedPage)
                    buttonDao.deleteButtonsForPage(updatedPage.id)
                    buttonDao.insertButtons(updatedPage.toButtonEntities())
                    // Update book timestamp to trigger sync
                    appDatabase.bookDao().updateLastModified(page.bookId, now)
                }
            }
        }
    }
}
