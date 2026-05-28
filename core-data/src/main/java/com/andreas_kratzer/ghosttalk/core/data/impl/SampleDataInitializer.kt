package com.andreas_kratzer.ghosttalk.core.data.impl

import com.andreas_kratzer.ghosttalk.core.data.BookRepository
import com.andreas_kratzer.ghosttalk.core.data.PageRepository

import com.andreas_kratzer.ghosttalk.core.model.AuditoryCue
import com.andreas_kratzer.ghosttalk.core.model.Book
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.ControlDeviceButtonAction
import com.andreas_kratzer.ghosttalk.core.model.DeviceActionType
import com.andreas_kratzer.ghosttalk.core.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.model.SpeakTextButtonAction
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
                val pages = createSampleData(defaultBookId)
                pages.forEach { pageRepository.insertPage(it) }
            }
            return@withContext defaultBookId
        } else {
            android.util.Log.d("SampleDataInitializer", "Books already exist, skipping initialization.")
            return@withContext allBooks.first().id
        }
    }

    private fun createSampleData(defaultBookId: String): List<Page> {
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

        val testPage = Page(
            id = "page_test",
            bookId = defaultBookId,
            name = "Testseite",
            columns = 4,
            rows = 5,
            buttonConfigs = listOf(
                ButtonConfig("t_btn1", "Pause/Resume", buttonAction = ControlDeviceButtonAction(DeviceActionType.TOGGLE_SCANNING)),
                ButtonConfig("t_btn2", "Batterie", buttonAction = ControlDeviceButtonAction(DeviceActionType.READ_BATTERY)),
                ButtonConfig("t_btn3", "Uhrzeit", buttonAction = ControlDeviceButtonAction(DeviceActionType.READ_TIME)),
                ButtonConfig("t_btn4", "Datum", buttonAction = ControlDeviceButtonAction(DeviceActionType.READ_DATE)),
                
                ButtonConfig("t_btn5", "Vor", buttonAction = ControlDeviceButtonAction(DeviceActionType.MEDIA_NEXT)),
                ButtonConfig("t_btn6", "Zurück", buttonAction = ControlDeviceButtonAction(DeviceActionType.MEDIA_PREVIOUS)),
                ButtonConfig("t_btn7", "Play/Pause", buttonAction = ControlDeviceButtonAction(DeviceActionType.MEDIA_PLAY_PAUSE)),
                ButtonConfig("t_btn8", "Notif. lesen", buttonAction = ControlDeviceButtonAction(DeviceActionType.READ_NOTIFICATIONS)),
                
                ButtonConfig("t_btn9", "Vol Notif", buttonAction = ControlDeviceButtonAction(DeviceActionType.VOLUME_NOTIFICATION, volumeValue = "50")),
                ButtonConfig("t_btn10", "Vol Alarm", buttonAction = ControlDeviceButtonAction(DeviceActionType.VOLUME_ALARM, volumeValue = "70")),
                ButtonConfig("t_btn11", "Vol Media", buttonAction = ControlDeviceButtonAction(DeviceActionType.VOLUME_MEDIA, volumeValue = "30")),
                ButtonConfig("t_btn12", "Vol Call", buttonAction = ControlDeviceButtonAction(DeviceActionType.VOLUME_CALL, volumeValue = "100")),
                
                ButtonConfig("t_btn13", "Lautlos", buttonAction = ControlDeviceButtonAction(DeviceActionType.STATUS_SILENT)),
                ButtonConfig("t_btn14", "Vibration", buttonAction = ControlDeviceButtonAction(DeviceActionType.STATUS_VIBRATE)),
                ButtonConfig("t_btn15", "Laut", buttonAction = ControlDeviceButtonAction(DeviceActionType.STATUS_LOUD)),
                ButtonConfig("t_btn16", "SMS Senden", buttonAction = ControlDeviceButtonAction(DeviceActionType.SEND_MESSAGE, contactPhone = "123456789", messageText = "Test Nachricht")),
                
                ButtonConfig("t_btn17", "Notif Clear", buttonAction = ControlDeviceButtonAction(DeviceActionType.CLEAR_NOTIFICATIONS)),
                null,
                null,
                ButtonConfig("t_btn20", "ZURÜCK", buttonAction = NavigateToPageButtonAction(pageId = "page1"))
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
                        label = "Seite 2",
                        spokenText = "Zur zweiten Seite",
                        buttonAction = NavigateToPageButtonAction(pageId = "page2"),
                        auditoryCue = AuditoryCue.TextToSpeechCue("Zur zweiten Seite navigieren")
                    )
                    index == 1 -> ButtonConfig(
                        id = "btn_nav_test",
                        label = "Testseite",
                        spokenText = "Zur Testseite",
                        buttonAction = NavigateToPageButtonAction(pageId = "page_test"),
                        auditoryCue = AuditoryCue.TextToSpeechCue("Zur Testseite navigieren")
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
        
        return listOf(samplePage, secondPage, testPage)
    }
}
