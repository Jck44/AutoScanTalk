package com.andreas_kratzer.ghosttalk.data

import com.andreas_kratzer.ghosttalk.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.model.ButtonUsageStat
import com.andreas_kratzer.ghosttalk.model.SpeakTextButtonAction
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ButtonUsageRepositoryTest {

    private val dao: ButtonUsageDao = mockk(relaxed = true)
    private val repository = ButtonUsageRepository(dao)

    private val testButton = ButtonConfig(
        id = "btn-1",
        label = "Ja",
        auditoryCue = null,
        buttonAction = SpeakTextButtonAction()
    )

    @Test
    fun `recordUsage creates new stat when button not yet tracked`() = runTest {
        coEvery { dao.getStatForButton("book1", "btn-1") } returns null

        repository.recordUsage("book1", testButton, rows = 6, columns = 6, indexInPage = 0)

        val statSlot = slot<ButtonUsageStat>()
        coVerify { dao.upsert(capture(statSlot)) }

        val stat = statSlot.captured
        assertEquals("book1", stat.bookId)
        assertEquals("btn-1", stat.buttonConfigId)
        assertEquals("Ja", stat.label)
        assertEquals(1L, stat.usageCount)
    }

    @Test
    fun `recordUsage increments counter for existing stat`() = runTest {
        val existing = ButtonUsageStat(
            bookId = "book1",
            buttonConfigId = "btn-1",
            label = "Ja",
            actionJson = "{}",
            usageCount = 5,
            lastUsedAt = 1000L
        )
        coEvery { dao.getStatForButton("book1", "btn-1") } returns existing

        repository.recordUsage("book1", testButton, rows = 6, columns = 6, indexInPage = 0)

        val statSlot = slot<ButtonUsageStat>()
        coVerify { dao.upsert(capture(statSlot)) }

        assertEquals(6L, statSlot.captured.usageCount)
    }

    @Test
    fun `recordUsage updates label from current button config`() = runTest {
        val existing = ButtonUsageStat(
            bookId = "book1",
            buttonConfigId = "btn-1",
            label = "Old Label",
            actionJson = "{}",
            usageCount = 3,
            lastUsedAt = 1000L
        )
        coEvery { dao.getStatForButton("book1", "btn-1") } returns existing

        repository.recordUsage("book1", testButton, rows = 6, columns = 6, indexInPage = 0)

        val statSlot = slot<ButtonUsageStat>()
        coVerify { dao.upsert(capture(statSlot)) }

        assertEquals("Ja", statSlot.captured.label)
    }

    @Test
    fun `getTopActions delegates to dao`() = runTest {
        val stats = listOf(
            ButtonUsageStat("book1", "btn-1", "Ja", "{}", 10),
            ButtonUsageStat("book1", "btn-2", "Nein", "{}", 5)
        )
        coEvery { dao.getTopButtons("book1", 5) } returns stats

        val result = repository.getTopActions("book1", 5)

        assertEquals(2, result.size)
        assertEquals("Ja", result[0].label)
        assertEquals(10L, result[0].usageCount)
    }

    @Test
    fun `clearStats delegates to dao`() = runTest {
        repository.clearStats("book1")

        coVerify { dao.clearStatsForBook("book1") }
    }

    @Test
    fun `recordUsage serializes action to JSON`() = runTest {
        coEvery { dao.getStatForButton("book1", "btn-1") } returns null

        repository.recordUsage("book1", testButton, rows = 6, columns = 6, indexInPage = 0)

        val statSlot = slot<ButtonUsageStat>()
        coVerify { dao.upsert(capture(statSlot)) }

        // The actionJson should contain the serialized SpeakTextButtonAction
        assert(statSlot.captured.actionJson.contains("SpeakTextButtonAction"))
    }
}
