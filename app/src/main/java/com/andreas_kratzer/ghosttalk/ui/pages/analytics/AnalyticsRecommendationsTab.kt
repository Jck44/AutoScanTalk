@file:Suppress("DEPRECATION")
package com.andreas_kratzer.ghosttalk.ui.pages.analytics

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import com.andreas_kratzer.ghosttalk.ui.pages.analytics.recommendations.AiRestructureActions
import com.andreas_kratzer.ghosttalk.ui.pages.analytics.recommendations.AiRestructureSection
import com.andreas_kratzer.ghosttalk.ui.pages.analytics.recommendations.AiRestructureState
import com.andreas_kratzer.ghosttalk.ui.pages.analytics.recommendations.LayoutOptimizationSection
import com.andreas_kratzer.ghosttalk.ui.pages.analytics.recommendations.LayoutProposalsActions
import com.andreas_kratzer.ghosttalk.ui.pages.analytics.recommendations.LayoutProposalsState
import com.andreas_kratzer.ghosttalk.ui.pages.analytics.recommendations.ShortcutWizardActions
import com.andreas_kratzer.ghosttalk.ui.pages.analytics.recommendations.ShortcutWizardSection
import com.andreas_kratzer.ghosttalk.ui.pages.analytics.recommendations.ShortcutWizardState
import kotlinx.coroutines.CoroutineScope

@Composable
fun AnalyticsRecommendationsTab(
    context: Context,
    coroutineScope: CoroutineScope,
    shortcutWizardState: ShortcutWizardState,
    shortcutWizardActions: ShortcutWizardActions,
    layoutProposalsState: LayoutProposalsState,
    layoutProposalsActions: LayoutProposalsActions,
    aiRestructureState: AiRestructureState,
    aiRestructureActions: AiRestructureActions,
    onNavigateBack: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(LocalDimensions.current.sectionSpacing)
    ) {
        // SECTION 1: SHORTCUT WIZARD
        ShortcutWizardSection(
            context = context,
            state = shortcutWizardState,
            actions = shortcutWizardActions
        )

        // SECTION 2: LAYOUT OPTIMIZATION
        LayoutOptimizationSection(
            context = context,
            state = layoutProposalsState,
            actions = layoutProposalsActions
        )

        // SECTION 3: AI BOOK RESTRUCTURING (Gemini)
        AiRestructureSection(
            context = context,
            coroutineScope = coroutineScope,
            state = aiRestructureState,
            actions = aiRestructureActions,
            onNavigateBack = onNavigateBack
        )
    }
}
