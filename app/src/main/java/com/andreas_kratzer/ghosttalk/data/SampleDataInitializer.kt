package com.andreas_kratzer.ghosttalk.data

import com.andreas_kratzer.ghosttalk.model.AuditoryCue
import com.andreas_kratzer.ghosttalk.model.Book
import com.andreas_kratzer.ghosttalk.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.model.Page
import com.andreas_kratzer.ghosttalk.data.PageRepository
import com.andreas_kratzer.ghosttalk.model.SpeakTextButtonAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SampleDataInitializer @Inject constructor(
    private val bookRepository: BookRepository,
    private val pageRepository: PageRepository
) {
    suspend fun initializeIfNeeded(defaultBookId: String): String = withContext(Dispatchers.IO) {
        val allBooks = bookRepository.getAllBooksList()
        if (allBooks.isEmpty()) {
            android.util.Log.d("SampleDataInitializer", "No books found, creating default book and sample data.")
            bookRepository.insertBook(Book(id = defaultBookId, name = "Standardbuch"))
            
            if (pageRepository.getAllPages().isEmpty()) {
                val (samplePage, secondPage) = createSampleData(defaultBookId)
                pageRepository.insertPage(samplePage)
                pageRepository.insertPage(secondPage)
            }
            return@withContext defaultBookId
        } else {
            android.util.Log.d("SampleDataInitializer", "Books already exist, skipping initialization.")
            return@withContext allBooks.first().id
        }
    }

    private fun createSampleData(defaultBookId: String): Pair<Page, Page> {
        val secondPage = Page(
            id = "page2",
            bookId = defaultBookId,
            name = "Zweite Seite",
            columns = 2,
            rows = 2,
            buttonConfigs = listOf(
                ButtonConfig(
                    id = "p2_btn0",
                    label = "Aktion 1",
                    buttonAction = SpeakTextButtonAction(),
                    auditoryCue = AuditoryCue.TextToSpeechCue("Hinweis Aktion 1")
                ),
                ButtonConfig(
                    id = "p2_btn1",
                    label = "Aktion 2",
                    buttonAction = SpeakTextButtonAction(),
                    auditoryCue = AuditoryCue.TextToSpeechCue("Hinweis Aktion 2")
                ),
                ButtonConfig(
                    id = "p2_btn2",
                    label = "Aktion 3",
                    buttonAction = SpeakTextButtonAction(),
                    auditoryCue = AuditoryCue.TextToSpeechCue("Hinweis Aktion 3")
                ),
                ButtonConfig(
                    id = "p2_btn3",
                    label = "Zurück",
                    spokenText = "Zurück zur Hauptseite",
                    buttonAction = NavigateToPageButtonAction(pageId = "page1"),
                    auditoryCue = AuditoryCue.TextToSpeechCue("Zurück zur Hauptseite navigieren")
                )
            )
        )

        val samplePage = Page(
            id = "page1",
            bookId = defaultBookId,
            name = "Hauptseite",
            columns = 4,
            rows = 4,
            buttonConfigs = List(16) { index ->
                when {
                    index % 5 == 0 -> null
                    index == 2 -> ButtonConfig(
                        id = "btn_nav_page2",
                        label = "Zur Seite 2",
                        spokenText = "Zur zweiten Seite",
                        buttonAction = NavigateToPageButtonAction(pageId = "page2"),
                        auditoryCue = AuditoryCue.TextToSpeechCue("Zur zweiten Seite navigieren")
                    )
                    else -> ButtonConfig(
                        id = "btn$index",
                        label = "Button ${index + 1}",
                        buttonAction = SpeakTextButtonAction(),
                        auditoryCue = AuditoryCue.TextToSpeechCue("Hinweis Button ${index + 1}")
                    )
                }
            }
        )
        
        return samplePage to secondPage
    }
}
