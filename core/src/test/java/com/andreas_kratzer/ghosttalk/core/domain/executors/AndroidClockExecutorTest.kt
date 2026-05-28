package com.andreas_kratzer.ghosttalk.core.domain.executors

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AndroidClockExecutorTest {

    private lateinit var context: Context
    private lateinit var executor: AndroidClockExecutor

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        executor = AndroidClockExecutor(context)
        
        // Mock Intent constructor for all tests
        io.mockk.mockkConstructor(Intent::class)
        every { anyConstructed<Intent>().putExtra(any<String>(), any<Int>()) } returns mockk(relaxed = true)
        every { anyConstructed<Intent>().putExtra(any<String>(), any<String>()) } returns mockk(relaxed = true)
        every { anyConstructed<Intent>().putExtra(any<String>(), any<Boolean>()) } returns mockk(relaxed = true)
        every { anyConstructed<Intent>().setFlags(any()) } returns mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        io.mockk.unmockkConstructor(Intent::class)
    }

    @Test
    fun `setAlarm fires ACTION_SET_ALARM intent with correct extras`() {
        val intentSlot = slot<Intent>()
        every { context.startActivity(capture(intentSlot)) } answers {}

        val result = executor.setAlarm(7, 30, "Aufstehen")

        assertTrue(result)
        verify(exactly = 1) { context.startActivity(any()) }

        verify { anyConstructed<Intent>().putExtra(AlarmClock.EXTRA_HOUR, 7) }
        verify { anyConstructed<Intent>().putExtra(AlarmClock.EXTRA_MINUTES, 30) }
        verify { anyConstructed<Intent>().putExtra(AlarmClock.EXTRA_MESSAGE, "Aufstehen") }
        verify { anyConstructed<Intent>().putExtra(AlarmClock.EXTRA_SKIP_UI, true) }
    }

    @Test
    fun `setAlarm returns false if startActivity throws exception`() {
        every { context.startActivity(any()) } throws RuntimeException("No Activity found to handle Intent")

        val result = executor.setAlarm(7, 30)

        assertFalse(result)
    }
}
