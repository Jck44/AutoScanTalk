package com.andreas_kratzer.ghosttalk.domain.executors

import com.andreas_kratzer.ghosttalk.domain.executors.SystemTimeExecutor

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SystemTimeExecutorTest {

    private lateinit var classUnderTest: SystemTimeExecutor

    @Before
    fun setup() {
        classUnderTest = SystemTimeExecutor()
    }

    @Test
    fun `getCurrentTimeOutput returns formatted time string`() {
        // We can't easily mock Date() without dependency injection of a Clock, 
        // so we just verify the format structure dynamically here
        val output = classUnderTest.getCurrentTimeOutput()
        
        val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
        val expectedTimeStr = formatter.format(Date())
        
        assertEquals("Es ist $expectedTimeStr Uhr.", output)
    }

    @Test
    fun `getCurrentDateOutput returns formatted date string`() {
        val output = classUnderTest.getCurrentDateOutput()
        
        val formatter = SimpleDateFormat("EEEE, d. MMMM yyyy", Locale.getDefault())
        val expectedDateStr = formatter.format(Date())
        
        assertEquals("Heute ist $expectedDateStr.", output)
    }
}
