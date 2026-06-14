package com.andreas_kratzer.ghosttalk.ui.pages.analytics.recommendations

import androidx.compose.runtime.Immutable
import com.andreas_kratzer.ghosttalk.core.data.impl.analytics.PageLayoutOptimizer.LayoutOptimizationProposal
import com.andreas_kratzer.ghosttalk.core.data.impl.analytics.PathAnalyzer.ShortcutRecommendation
import com.andreas_kratzer.ghosttalk.core.model.BookHierarchyProposal
import com.andreas_kratzer.ghosttalk.core.model.BookRestructureProposal
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.model.PageLayoutProposal
import com.andreas_kratzer.ghosttalk.ui.pages.ProposalFilter
import com.andreas_kratzer.ghosttalk.ui.pages.ProposalSort

@Immutable
data class ShortcutWizardState(
    val isCalculating: Boolean,
    val recommendations: List<ShortcutRecommendation>
)

@Immutable
data class ShortcutWizardActions(
    val onApplyRecommendation: (ShortcutRecommendation, (Boolean, String) -> Unit) -> Unit
)

@Immutable
data class LayoutProposalsState(
    val proposals: List<LayoutOptimizationProposal>,
    val currentFilter: ProposalFilter,
    val currentSort: ProposalSort
)

@Immutable
data class LayoutProposalsActions(
    val onSetFilter: (ProposalFilter) -> Unit,
    val onSetSort: (ProposalSort) -> Unit,
    val onGeneratePageSplitProposal: (String) -> Unit,
    val onChangePageScanPattern: (String, String) -> Unit,
    val onChangeScanDelay: (Long) -> Unit,
    val onApplySpacerRelocate: (String, String, String) -> Unit,
    val onNavigateToEditorWithAssistant: (String) -> Unit
)

@Immutable
data class AiRestructureState(
    val proposal: BookRestructureProposal?,
    val isLoading: Boolean,
    val hierarchy: BookHierarchyProposal?,
    val isHierarchyLoading: Boolean,
    val pageLayouts: Map<String, PageLayoutProposal>,
    val isLoadingPageLayout: Map<String, Boolean>,
    val unfilteredPages: List<Page>,
    val selectedPageIds: Set<String>,
    val activeTargetPageIds: Set<String>,
    val restructureScope: String,
    val error: String?,
    val isGeminiEnabled: Boolean,
    val toastApplied: String
)

@Immutable
data class AiRestructureActions(
    val onSelectActivePagesOnly: () -> Unit,
    val onSelectAllPages: () -> Unit,
    val onTogglePageSelection: (String) -> Unit,
    val onSetScope: (String) -> Unit,
    val onClearError: () -> Unit,
    val onClearProposal: () -> Unit,
    val onGenerateHierarchyProposal: (String?) -> Unit,
    val onUpdateHierarchyManualEdit: (BookHierarchyProposal) -> Unit,
    val onLoadPageLayoutProposal: (String) -> Unit,
    val onLoadAllPageLayoutProposals: (() -> Unit) -> Unit,
    val onApplyHierarchyProposal: ((String) -> Unit) -> Unit
)
