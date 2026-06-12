package com.andreas_kratzer.ghosttalk.ui.main

import android.content.Context
import android.content.SharedPreferences
import com.andreas_kratzer.ghosttalk.core.cloud.domain.RescheduleProfileSyncUseCase
import com.andreas_kratzer.ghosttalk.core.data.BookRepository
import com.andreas_kratzer.ghosttalk.core.data.PageRepository
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.data.impl.SampleDataInitializer
import com.andreas_kratzer.ghosttalk.core.domain.BackgroundScheduler
import com.andreas_kratzer.ghosttalk.core.model.Book
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AppStartupInitializerTest {

    @Test
    fun testRun_activeBookDoesNotExist_fallsBackToInitializedBook() = runTest {
        val context = mockk<Context>()
        val settingsRepository = mockk<SettingsRepository>()
        val bookRepository = mockk<BookRepository>()
        val sampleDataInitializer = mockk<SampleDataInitializer>()
        val backgroundScheduler = mockk<BackgroundScheduler>()
        val rescheduleProfileSyncUseCase = mockk<RescheduleProfileSyncUseCase>()
        val pageRepository = mockk<PageRepository>()

        val sharedPreferences = mockk<SharedPreferences>()
        val editor = mockk<SharedPreferences.Editor>()
        every { context.getSharedPreferences(any(), any()) } returns sharedPreferences
        every { sharedPreferences.getBoolean(any(), any()) } returns true
        every { sharedPreferences.edit() } returns editor
        every { editor.putBoolean(any(), any()) } returns editor
        every { editor.apply() } just runs

        every { settingsRepository.isSetupCompleted } returns true
        coEvery { sampleDataInitializer.initializeIfNeeded("book-default") } returns "book-default"
        every { settingsRepository.activeBookId } returns "non-existent-book"
        every { settingsRepository.activeBookId = any() } just runs
        coEvery { bookRepository.getBookById("non-existent-book") } returns null

        every { backgroundScheduler.scheduleLocationUpdate() } just runs
        every { backgroundScheduler.scheduleWeatherUpdate() } just runs
        every { rescheduleProfileSyncUseCase.reschedule() } just runs
        every { rescheduleProfileSyncUseCase.runOnceImmediately() } just runs
        coEvery { pageRepository.purgeInstallUpdateButtons() } just runs

        val initializer = AppStartupInitializer(
            context = context,
            settingsRepository = settingsRepository,
            bookRepository = bookRepository,
            sampleDataInitializer = sampleDataInitializer,
            backgroundScheduler = backgroundScheduler,
            rescheduleProfileSyncUseCase = rescheduleProfileSyncUseCase,
            pageRepository = pageRepository
        )

        val result = initializer.run()
        assertEquals("book-default", result)
    }

    @Test
    fun testRun_activeBookExists_usesActiveBook() = runTest {
        val context = mockk<Context>()
        val settingsRepository = mockk<SettingsRepository>()
        val bookRepository = mockk<BookRepository>()
        val sampleDataInitializer = mockk<SampleDataInitializer>()
        val backgroundScheduler = mockk<BackgroundScheduler>()
        val rescheduleProfileSyncUseCase = mockk<RescheduleProfileSyncUseCase>()
        val pageRepository = mockk<PageRepository>()

        val sharedPreferences = mockk<SharedPreferences>()
        val editor = mockk<SharedPreferences.Editor>()
        every { context.getSharedPreferences(any(), any()) } returns sharedPreferences
        every { sharedPreferences.getBoolean(any(), any()) } returns true
        every { sharedPreferences.edit() } returns editor
        every { editor.putBoolean(any(), any()) } returns editor
        every { editor.apply() } just runs

        every { settingsRepository.isSetupCompleted } returns true
        coEvery { sampleDataInitializer.initializeIfNeeded("book-default") } returns "book-default"
        every { settingsRepository.activeBookId } returns "existing-book"
        every { settingsRepository.activeBookId = any() } just runs
        coEvery { bookRepository.getBookById("existing-book") } returns Book("existing-book", "Existing Book")

        every { backgroundScheduler.scheduleLocationUpdate() } just runs
        every { backgroundScheduler.scheduleWeatherUpdate() } just runs
        every { rescheduleProfileSyncUseCase.reschedule() } just runs
        every { rescheduleProfileSyncUseCase.runOnceImmediately() } just runs
        coEvery { pageRepository.purgeInstallUpdateButtons() } just runs

        val initializer = AppStartupInitializer(
            context = context,
            settingsRepository = settingsRepository,
            bookRepository = bookRepository,
            sampleDataInitializer = sampleDataInitializer,
            backgroundScheduler = backgroundScheduler,
            rescheduleProfileSyncUseCase = rescheduleProfileSyncUseCase,
            pageRepository = pageRepository
        )

        val result = initializer.run()
        assertEquals("existing-book", result)
    }
}
