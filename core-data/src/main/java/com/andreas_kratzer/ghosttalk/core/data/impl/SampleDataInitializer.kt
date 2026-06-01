package com.andreas_kratzer.ghosttalk.core.data.impl

import com.andreas_kratzer.ghosttalk.core.data.BookRepository
import com.andreas_kratzer.ghosttalk.core.data.PageRepository
import com.andreas_kratzer.ghosttalk.core.data.ButtonUsageRepository

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
    private val pageRepository: PageRepository,
    private val buttonUsageRepository: ButtonUsageRepository
) {
    suspend fun initializeIfNeeded(defaultBookId: String): String = withContext(Dispatchers.IO) {
        val allBooks = bookRepository.getAllBooksList()
        if (allBooks.isEmpty()) {
            android.util.Log.d("SampleDataInitializer", "No books found, creating default book and sample data.")
            bookRepository.insertBook(Book(id = defaultBookId, name = "Standardbuch"))
            
            if (pageRepository.getAllPages().isEmpty()) {
                val pages = createSampleData(defaultBookId)
                pages.forEach { pageRepository.insertPage(it) }

                seedTransitionData(defaultBookId, pages)
            }
            return@withContext defaultBookId
        } else {
            android.util.Log.d("SampleDataInitializer", "Books already exist, checking transition stats.")
            val activeBookId = allBooks.first().id
            val stats = buttonUsageRepository.getGroupedUsageStats(activeBookId)
            if (stats.isEmpty()) {
                android.util.Log.d("SampleDataInitializer", "Transition stats are empty. Backfilling transition data for existing book.")
                val pages = pageRepository.getAllPages().filter { it.bookId == activeBookId }
                if (pages.isNotEmpty()) {
                    seedTransitionData(activeBookId, pages)
                }
            }
            return@withContext activeBookId
        }
    }

    private suspend fun seedTransitionData(defaultBookId: String, pages: List<Page>) {
        val mainPage = pages.find { it.id == "page1" }
        val secondPage = pages.find { it.id == "page2" }
        val testPage = pages.find { it.id == "page_test" }
        val rowScanPage = pages.find { it.id == "page_row_scan" }
        val needsPage = pages.find { it.id == "page_needs" }

        val baseTime = System.currentTimeMillis() - 24L * 60L * 60L * 1000L // 1 day ago

        // -------------------------------------------------------------
        // 1. Scenario A: Musiksteuerung abkürzen (Main Page -> Testseite -> Play/Pause & Vor)
        // We run 6 distinct sessions/occasions spaced 15 minutes apart.
        // Each occasion: 
        //   t + 0s: Click "Testseite" on Hauptseite (page1)
        //   t + 2s: Click "Play/Pause" on Testseite (page_test)
        //   t + 4s: Click "Vor" on Testseite (page_test)
        // -------------------------------------------------------------
        val navTestBtn = mainPage?.buttonConfigs?.find { it?.id == "btn_nav_test" }
        val playPauseBtn = testPage?.buttonConfigs?.find { it?.id == "t_btn7" }
        val vorBtn = testPage?.buttonConfigs?.find { it?.id == "t_btn5" }

        if (mainPage != null && testPage != null && navTestBtn != null && playPauseBtn != null && vorBtn != null) {
            for (i in 0 until 6) {
                val sessionStart = baseTime + i * 15 * 60 * 1000L
                // 1. Navigate to Testseite
                buttonUsageRepository.recordUsage(defaultBookId, "page1", navTestBtn, 4, 4, 1, sessionStart)
                // 2. Click Play/Pause 2 seconds later
                buttonUsageRepository.recordUsage(defaultBookId, "page_test", playPauseBtn, 5, 4, 6, sessionStart + 2000L)
                // 3. Click Vor 2 seconds later
                buttonUsageRepository.recordUsage(defaultBookId, "page_test", vorBtn, 5, 4, 4, sessionStart + 4000L)
            }
        }

        // -------------------------------------------------------------
        // 2. Scenario B: Lieblingsbeschäftigung von Seite 2 abkürzen (Main Page -> Seite 2 -> Aktion 1)
        // We run 8 sessions spaced 30 minutes apart.
        // Each session:
        //   t + 0s: Click "Seite 2" on Hauptseite (page1)
        //   t + 3s: Click "Aktion 1" on Zweite Seite (page2)
        // -------------------------------------------------------------
        val navPage2Btn = mainPage?.buttonConfigs?.find { it?.id == "btn_nav_page2" }
        val p2Btn0 = secondPage?.buttonConfigs?.find { it?.id == "p2_btn0" }

        if (mainPage != null && secondPage != null && navPage2Btn != null && p2Btn0 != null) {
            for (i in 0 until 8) {
                val sessionStart = baseTime + (i * 30 + 100) * 60 * 1000L
                // 1. Navigate to Seite 2
                buttonUsageRepository.recordUsage(defaultBookId, "page1", navPage2Btn, 4, 4, 2, sessionStart)
                // 2. Click Aktion 1
                buttonUsageRepository.recordUsage(defaultBookId, "page2", p2Btn0, 2, 2, 0, sessionStart + 3000L)
            }
        }

        // -------------------------------------------------------------
        // 3. Scenario C: Direkter Sprung von Reihenweise Seite zu Testseite / Uhrzeit
        // We run 5 sessions spaced 45 minutes apart.
        // Each session:
        //   t + 0s: Click "Aktion 3" on Reihenweise Seite (page_row_scan) (High effort click!)
        //   t + 3s: Click "Zurück" on Reihenweise Seite (page_row_scan)
        //   t + 6s: Click "Testseite" on Hauptseite (page1)
        //   t + 8s: Click "Uhrzeit" on Testseite (page_test)
        // -------------------------------------------------------------
        val rowBtn3 = rowScanPage?.buttonConfigs?.find { it?.id == "row_btn3" } // Zurück button
        val rowBtn2 = rowScanPage?.buttonConfigs?.find { it?.id == "row_btn2" } // Aktion 3
        val uhrBtn = testPage?.buttonConfigs?.find { it?.id == "t_btn3" }

        if (rowScanPage != null && mainPage != null && testPage != null &&
            rowBtn3 != null && rowBtn2 != null && navTestBtn != null && uhrBtn != null) {
            for (i in 0 until 5) {
                val sessionStart = baseTime + (i * 45 + 300) * 60 * 1000L
                // 1. Click Aktion 3 on rows page
                buttonUsageRepository.recordUsage(defaultBookId, "page_row_scan", rowBtn2, 2, 2, 2, sessionStart)
                // 2. Click Zurück on rows page (System action)
                buttonUsageRepository.recordUsage(defaultBookId, "page_row_scan", rowBtn3, 2, 2, 3, sessionStart + 3000L)
                // 3. Click Testseite on main page
                buttonUsageRepository.recordUsage(defaultBookId, "page1", navTestBtn, 4, 4, 1, sessionStart + 6000L)
                // 4. Click Uhrzeit on test page
                buttonUsageRepository.recordUsage(defaultBookId, "page_test", uhrBtn, 5, 4, 2, sessionStart + 8000L)
            }
        }

        // -------------------------------------------------------------
        // 4. Scenario D: Device Status Check abkürzen (Main Page -> Testseite -> Uhrzeit & Batterie)
        // We run 7 sessions/occasions spaced 20 minutes apart.
        // Each occasion:
        //   t + 0s: Click "Testseite" on Hauptseite (page1)
        //   t + 2s: Click "Batterie" on Testseite (page_test)
        //   t + 4s: Click "Uhrzeit" on Testseite (page_test)
        // -------------------------------------------------------------
        val batBtn = testPage?.buttonConfigs?.find { it?.id == "t_btn2" }

        if (mainPage != null && testPage != null && navTestBtn != null && batBtn != null && uhrBtn != null) {
            for (i in 0 until 7) {
                val sessionStart = baseTime + (i * 20 + 500) * 60 * 1000L
                buttonUsageRepository.recordUsage(defaultBookId, "page1", navTestBtn, 4, 4, 1, sessionStart)
                buttonUsageRepository.recordUsage(defaultBookId, "page_test", batBtn, 5, 4, 1, sessionStart + 2000L)
                buttonUsageRepository.recordUsage(defaultBookId, "page_test", uhrBtn, 5, 4, 2, sessionStart + 4000L)
            }
        }

        // -------------------------------------------------------------
        // 5. Scenario E: Dringende Bedürfnisse abkürzen (Main Page -> Bedürfnissseite -> Toilette & Durst)
        // We run 9 sessions/occasions spaced 12 minutes apart.
        // Each occasion:
        //   t + 0s: Click "Bedürfnisse" on Hauptseite (page1)
        //   t + 2s: Click "Durst" on Bedürfnisse (page_needs)
        //   t + 4s: Click "Toilette" on Bedürfnisse (page_needs)
        // -------------------------------------------------------------
        val navNeedsBtn = mainPage?.buttonConfigs?.find { it?.id == "btn_nav_needs" }
        val durstBtn = needsPage?.buttonConfigs?.find { it?.id == "n_btn1" }
        val toiletBtn = needsPage?.buttonConfigs?.find { it?.id == "n_btn2" }

        if (mainPage != null && needsPage != null && navNeedsBtn != null && durstBtn != null && toiletBtn != null) {
            for (i in 0 until 9) {
                val sessionStart = baseTime + (i * 12 + 800) * 60 * 1000L
                buttonUsageRepository.recordUsage(defaultBookId, "page1", navNeedsBtn, 4, 4, 6, sessionStart)
                buttonUsageRepository.recordUsage(defaultBookId, "page_needs", durstBtn, 2, 3, 1, sessionStart + 2000L)
                buttonUsageRepository.recordUsage(defaultBookId, "page_needs", toiletBtn, 2, 3, 2, sessionStart + 4000L)
            }
        }

        // -------------------------------------------------------------
        // 6. Visual Badges/Heatmaps seeding:
        // Seed a high access effort warning for "Aktion 3" on page_row_scan
        // and some simple active/inactive clicks
        // -------------------------------------------------------------
        if (rowScanPage != null) {
            val btn1 = rowScanPage.buttonConfigs.getOrNull(0)
            val btn3 = rowScanPage.buttonConfigs.getOrNull(2)
            if (btn1 != null) {
                // Click Row 0 Col 0 ("Aktion 1") a few times
                for (i in 0 until 10) {
                    buttonUsageRepository.recordUsage(defaultBookId, "page_row_scan", btn1, 2, 2, 0, baseTime + i * 2000L)
                }
            }
            if (btn3 != null) {
                // Click Row 1 Col 0 ("Aktion 3") 25 times to generate warning (high scan effort + high clicks)
                for (i in 0 until 25) {
                    buttonUsageRepository.recordUsage(defaultBookId, "page_row_scan", btn3, 2, 2, 2, baseTime + (i + 50) * 2000L)
                }
            }
        }

        // Click on inactive button on Second Page -> 50 times (Should NOT render heatmaps or badges!)
        if (secondPage != null) {
            secondPage.buttonConfigs.getOrNull(1)?.let { btn ->
                repeat(50) {
                    buttonUsageRepository.recordUsage(
                        bookId = defaultBookId,
                        pageId = "page2",
                        buttonConfig = btn,
                        rows = 2,
                        columns = 2,
                        indexInPage = 1,
                        timestamp = baseTime + it * 1000L
                    )
                }
            }
        }
    }

    private fun createSampleData(defaultBookId: String): List<Page> {
        val secondPage = Page(
            id = "page2",
            bookId = defaultBookId,
            name = "Zweite Seite",
            columns = 2,
            rows = 2,
            buttonConfigs = com.andreas_kratzer.ghosttalk.core.util.GridUtils.adjustButtonConfigs(
                configs = listOf(
                    ButtonConfig(
                        id = "p2_btn0",
                        label = "Aktion 1",
                        buttonAction = SpeakTextButtonAction(),
                        auditoryCue = AuditoryCue.TextToSpeechCue("Hinweis Aktion 1")
                    ),
                    ButtonConfig(
                        id = "p2_btn1",
                        label = "Aktion 2 (Inaktiv)",
                        isActive = false,
                        buttonAction = SpeakTextButtonAction(),
                        auditoryCue = AuditoryCue.TextToSpeechCue("Hinweis Aktion 2 inaktiv")
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
                ),
                sourceRows = 2,
                sourceColumns = 2
            )
        )

        val testPage = Page(
            id = "page_test",
            bookId = defaultBookId,
            name = "Testseite",
            columns = 4,
            rows = 5,
            buttonConfigs = com.andreas_kratzer.ghosttalk.core.util.GridUtils.adjustButtonConfigs(
                configs = listOf(
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
                ),
                sourceRows = 5,
                sourceColumns = 4
            )
        )

        val rowScanPage = Page(
            id = "page_row_scan",
            bookId = defaultBookId,
            name = "Reihenweise Seite",
            columns = 2,
            rows = 2,
            scanPattern = "row_by_row",
            buttonConfigs = com.andreas_kratzer.ghosttalk.core.util.GridUtils.adjustButtonConfigs(
                configs = listOf(
                    ButtonConfig(
                        id = "row_btn0",
                        label = "Aktion 1",
                        buttonAction = SpeakTextButtonAction(),
                        auditoryCue = AuditoryCue.TextToSpeechCue("Hinweis Aktion 1")
                    ),
                    ButtonConfig(
                        id = "row_btn1",
                        label = "Aktion 2",
                        buttonAction = SpeakTextButtonAction(),
                        auditoryCue = AuditoryCue.TextToSpeechCue("Hinweis Aktion 2")
                    ),
                    ButtonConfig(
                        id = "row_btn2",
                        label = "Aktion 3",
                        buttonAction = SpeakTextButtonAction(),
                        auditoryCue = AuditoryCue.TextToSpeechCue("Hinweis Aktion 3")
                    ),
                    ButtonConfig(
                        id = "row_btn3",
                        label = "Zurück",
                        spokenText = "Zurück zur Hauptseite",
                        buttonAction = NavigateToPageButtonAction(pageId = "page1"),
                        auditoryCue = AuditoryCue.TextToSpeechCue("Zurück zur Hauptseite navigieren")
                    )
                ),
                sourceRows = 2,
                sourceColumns = 2
            )
        )

        val samplePage = Page(
            id = "page1",
            bookId = defaultBookId,
            name = "Hauptseite",
            columns = 4,
            rows = 4,
            buttonConfigs = com.andreas_kratzer.ghosttalk.core.util.GridUtils.adjustButtonConfigs(
                configs = List(16) { index ->
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
                        index == 3 -> ButtonConfig(
                            id = "btn_nav_row_scan",
                            label = "Reihenweise Seite",
                            spokenText = "Zur reihenweisen Seite",
                            buttonAction = NavigateToPageButtonAction(pageId = "page_row_scan"),
                            auditoryCue = AuditoryCue.TextToSpeechCue("Zur reihenweisen Seite navigieren")
                        )
                        index == 6 -> ButtonConfig(
                            id = "btn_nav_needs",
                            label = "Bedürfnisse",
                            spokenText = "Zu den Bedürfnissen",
                            buttonAction = NavigateToPageButtonAction(pageId = "page_needs"),
                            auditoryCue = AuditoryCue.TextToSpeechCue("Zu den Bedürfnissen navigieren")
                        )
                        else -> ButtonConfig(
                            id = "btn$index",
                            label = "Button ${index + 1}",
                            buttonAction = SpeakTextButtonAction(),
                            auditoryCue = AuditoryCue.TextToSpeechCue("Hinweis Button ${index + 1}")
                        )
                    }
                },
                sourceRows = 4,
                sourceColumns = 4
            )
        )

        val needsPage = Page(
            id = "page_needs",
            bookId = defaultBookId,
            name = "Bedürfnisse",
            columns = 3,
            rows = 2,
            buttonConfigs = com.andreas_kratzer.ghosttalk.core.util.GridUtils.adjustButtonConfigs(
                configs = listOf(
                    ButtonConfig("n_btn0", "Hunger", spokenText = "Ich habe Hunger", buttonAction = SpeakTextButtonAction(), auditoryCue = AuditoryCue.TextToSpeechCue("Hinweis Hunger")),
                    ButtonConfig("n_btn1", "Durst", spokenText = "Ich habe Durst", buttonAction = SpeakTextButtonAction(), auditoryCue = AuditoryCue.TextToSpeechCue("Hinweis Durst")),
                    ButtonConfig("n_btn2", "Toilette", spokenText = "Ich muss auf Toilette", buttonAction = SpeakTextButtonAction(), auditoryCue = AuditoryCue.TextToSpeechCue("Hinweis Toilette")),
                    ButtonConfig("n_btn3", "Schmerzen", spokenText = "Ich habe Schmerzen", buttonAction = SpeakTextButtonAction(), auditoryCue = AuditoryCue.TextToSpeechCue("Hinweis Schmerzen")),
                    ButtonConfig("n_btn4", "Müde", spokenText = "Ich bin müde", buttonAction = SpeakTextButtonAction(), auditoryCue = AuditoryCue.TextToSpeechCue("Hinweis Müde")),
                    ButtonConfig("n_btn5", "ZURÜCK", buttonAction = NavigateToPageButtonAction(pageId = "page1"), auditoryCue = AuditoryCue.TextToSpeechCue("Zurück zur Hauptseite"))
                ),
                sourceRows = 2,
                sourceColumns = 3
            )
        )
        
        return listOf(samplePage, secondPage, testPage, rowScanPage, needsPage)
    }
}
