package com.andreas_kratzer.ghosttalk.ui.pages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andreas_kratzer.ghosttalk.core.ai.domain.SplitPageUseCase
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.ui.pages.delegates.AnalyticsDelegate
import com.andreas_kratzer.ghosttalk.ui.pages.delegates.LayoutWizardDelegate
import com.andreas_kratzer.ghosttalk.ui.pages.delegates.PageManagementDelegate
import com.andreas_kratzer.ghosttalk.ui.pages.delegates.PageSplitDelegate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class PageSplitViewModel @Inject constructor(
    private val pageSplitDelegate: PageSplitDelegate,
    private val layoutWizardDelegate: LayoutWizardDelegate,
    private val pageManagementDelegate: PageManagementDelegate,
    private val settingsRepository: SettingsRepository,
    private val analyticsDelegate: AnalyticsDelegate
) : ViewModel() {

    init {
        pageSplitDelegate.init(
            coroutineScope = viewModelScope,
            unfilteredPages = pageManagementDelegate.unfilteredPages,
            activeBookId = pageManagementDelegate.activeBookId,
            buttonHistory = analyticsDelegate.buttonHistory
        )
        layoutWizardDelegate.init(viewModelScope)
    }

    val pageSplitProposal: StateFlow<SplitPageUseCase.PageSplitProposal?> = pageSplitDelegate.pageSplitProposal
    val isPageSplitLoading: StateFlow<Boolean> = pageSplitDelegate.isPageSplitLoading
    val currentProposalFilter: StateFlow<ProposalFilter> = pageSplitDelegate.currentProposalFilter
    val currentProposalSort: StateFlow<ProposalSort> = pageSplitDelegate.currentProposalSort
    val layoutOptimizationProposals = pageSplitDelegate.layoutOptimizationProposals
    val magicCleanupProgress: StateFlow<String?> = layoutWizardDelegate.magicCleanupProgress

    fun generatePageSplitPrompt(buttonLabels: List<String>): String {
        return pageSplitDelegate.generatePageSplitPrompt(buttonLabels)
    }

    fun parsePageSplitProposal(response: String) {
        pageSplitDelegate.parsePageSplitProposal(response)
    }

    fun clearPageSplitProposal() {
        pageSplitDelegate.clearPageSplitProposal()
    }

    fun setProposalFilter(filter: ProposalFilter) {
        pageSplitDelegate.setProposalFilter(filter)
    }

    fun setProposalSort(sort: ProposalSort) {
        pageSplitDelegate.setProposalSort(sort)
    }

    fun generatePageSplitProposal(pageId: String) {
        pageSplitDelegate.generatePageSplitProposal(pageId)
    }

    fun applyPageSplit(pageId: String, proposal: SplitPageUseCase.PageSplitProposal) {
        pageSplitDelegate.applyPageSplit(pageId, proposal)
    }

    fun shouldFilterButtonFromSplit(buttonConfig: ButtonConfig?, defaultStartPageId: String?, currentPageId: String?): Boolean {
        return pageSplitDelegate.shouldFilterButtonFromSplit(buttonConfig, defaultStartPageId, currentPageId)
    }

    fun magicCleanup(pageId: String, onComplete: () -> Unit = {}) {
        layoutWizardDelegate.magicCleanup(pageId, onComplete)
    }

    fun reorderByClickStats(pageId: String, onComplete: () -> Unit = {}) {
        layoutWizardDelegate.reorderByClickStats(pageId, onComplete)
    }

    fun insertHomeNavigationEveryX(pageId: String, x: Int, onComplete: () -> Unit = {}) {
        layoutWizardDelegate.insertHomeNavigationEveryX(pageId, x, onComplete)
    }

    fun shrinkGridToMinimum(pageId: String, onComplete: () -> Unit = {}) {
        layoutWizardDelegate.shrinkGridToMinimum(pageId, onComplete)
    }

    fun deleteDeactivatedButtons(pageId: String, onComplete: () -> Unit = {}) {
        layoutWizardDelegate.deleteDeactivatedButtons(pageId, onComplete)
    }

    fun changePageScanPattern(pageId: String, pattern: String) {
        pageSplitDelegate.changePageScanPattern(pageId, pattern)
    }

    fun changeScanDelay(delayMs: Long) {
        pageSplitDelegate.changeScanDelay(delayMs)
    }

    fun applySpacerRelocate(pageId: String, buttonId: String, intendedButtonId: String) {
        pageSplitDelegate.applySpacerRelocate(pageId, buttonId, intendedButtonId)
    }

    var hasAcceptedPageSplitOptIn: Boolean
        get() = settingsRepository.hasAcceptedPageSplitOptIn
        set(value) {
            settingsRepository.hasAcceptedPageSplitOptIn = value
        }

    val defaultStartPageId: String?
        get() = settingsRepository.defaultStartPageId
}
