package com.andreas_kratzer.ghosttalk.domain.pages


import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.database.PageRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GetPagesUseCaseTest {

    private val pageRepository: PageRepository = mockk(relaxed = true)
    private val useCase = GetPagesUseCase(pageRepository)

    @Test
    fun `execute returns pages for given book id`() = runTest {
        val pagesFlow = MutableStateFlow<List<Page>>(emptyList())
        coEvery { pageRepository.getPagesForBookFlow("book1") } returns pagesFlow

        val activeBookIdFlow = MutableStateFlow<String?>("book1")
        val resultFlow = useCase.execute(activeBookIdFlow)
        // Collect first emission
        val result = resultFlow.first()
        assertEquals(emptyList<Page>(), result)
    }

    @Test
    fun `execute returns empty list when book id is null`() = runTest {
        val activeBookIdFlow = MutableStateFlow<String?>(null)
        val resultFlow = useCase.execute(activeBookIdFlow)
        val result = resultFlow.first()
        assertEquals(emptyList<Page>(), result)
    }
}
