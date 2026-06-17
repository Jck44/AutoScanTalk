package com.andreas_kratzer.ghosttalk.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.core.ui.theme.GhostTalkIcons

enum class SidePanelTab {
    TREE,
    TEMPLATES
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorSidePanel(
    selectedTab: SidePanelTab,
    onSelectTab: (SidePanelTab) -> Unit,
    onCollapse: () -> Unit,
    treeContent: @Composable () -> Unit,
    templatesContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    treeTabTitle: String = "Seitenbaum",
    templatesTabTitle: String = "Button-Vorlagen",
    collapseContentDescription: String = "Seitenleiste einklappen"
) {
    Card(
        modifier = modifier
            .width(300.dp)
            .fillMaxHeight(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onCollapse) {
                    Icon(
                        imageVector = GhostTalkIcons.ArrowBack,
                        contentDescription = collapseContentDescription
                    )
                }
            }
            SecondaryTabRow(
                selectedTabIndex = if (selectedTab == SidePanelTab.TREE) 0 else 1,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = selectedTab == SidePanelTab.TREE,
                    onClick = { onSelectTab(SidePanelTab.TREE) },
                    text = { Text(treeTabTitle) }
                )
                Tab(
                    selected = selectedTab == SidePanelTab.TEMPLATES,
                    onClick = { onSelectTab(SidePanelTab.TEMPLATES) },
                    text = { Text(templatesTabTitle) }
                )
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (selectedTab == SidePanelTab.TREE) {
                    treeContent()
                } else {
                    templatesContent()
                }
            }
        }
    }
}
