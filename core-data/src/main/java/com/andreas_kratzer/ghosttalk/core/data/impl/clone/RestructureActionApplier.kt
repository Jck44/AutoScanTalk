package com.andreas_kratzer.ghosttalk.core.data.impl.clone

import com.andreas_kratzer.ghosttalk.core.database.ButtonEntity
import com.andreas_kratzer.ghosttalk.core.model.Page
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RestructureActionApplier @Inject constructor() {
    fun applyRestructureActions(
        actions: List<com.andreas_kratzer.ghosttalk.core.model.RestructureAction>,
        mutablePages: MutableList<MutablePageWithButtons>,
        targetBookId: String
    ) {
        actions.forEach { action ->
            when (action.type) {
                "MOVE_BUTTON" -> {
                    val srcPageName = action.sourcePageName ?: return@forEach
                    val targetPageName = action.targetPageName ?: return@forEach
                    val btnLabel = action.buttonLabel ?: return@forEach

                    val srcPage = mutablePages.find { it.page.name.equals(srcPageName, ignoreCase = true) }
                    val destPage = mutablePages.find { it.page.name.equals(targetPageName, ignoreCase = true) }

                    if (srcPage != null && destPage != null) {
                        val btnIndex = srcPage.buttons.indexOfFirst { it.label.equals(btnLabel, ignoreCase = true) }
                        if (btnIndex != -1) {
                            val buttonToMove = srcPage.buttons.removeAt(btnIndex)

                            val displaceLabel = action.displaceButtonLabel
                            val displaceTargetName = action.displaceTargetPageName
                            var targetSlot = -1

                            if (!displaceLabel.isNullOrBlank()) {
                                val displaceIndex = destPage.buttons.indexOfFirst { it.label.equals(displaceLabel, ignoreCase = true) }
                                if (displaceIndex != -1) {
                                    val buttonToDisplace = destPage.buttons.removeAt(displaceIndex)
                                    targetSlot = buttonToDisplace.globalIndex

                                    val displaceDestPage = if (!displaceTargetName.isNullOrBlank()) {
                                        mutablePages.find { it.page.name.equals(displaceTargetName, ignoreCase = true) }
                                    } else null

                                    if (displaceDestPage != null) {
                                        val displaceOccupied = displaceDestPage.buttons.map { it.globalIndex }.toSet()
                                        val displaceSlot = CloneHelpers.findFreeSlot(displaceOccupied)
                                        val maxDisplaceSlots = displaceDestPage.page.rows * displaceDestPage.page.columns
                                        if (displaceSlot >= maxDisplaceSlots) {
                                            val (newRows, newCols) = CloneHelpers.expandGridByOne(displaceDestPage.page.rows, displaceDestPage.page.columns)
                                            displaceDestPage.page = displaceDestPage.page.copy(rows = newRows, columns = newCols)
                                        }
                                        displaceDestPage.buttons.add(buttonToDisplace.copy(
                                            pageId = displaceDestPage.page.id,
                                            globalIndex = displaceSlot
                                        ))
                                    } else {
                                        val destOccupied = destPage.buttons.map { it.globalIndex }.toSet()
                                        val freeSlot = CloneHelpers.findFreeSlot(destOccupied, targetSlot)
                                        val maxDestSlots = destPage.page.rows * destPage.page.columns
                                        if (freeSlot >= maxDestSlots) {
                                            val (newRows, newCols) = CloneHelpers.expandGridByOne(destPage.page.rows, destPage.page.columns)
                                            destPage.page = destPage.page.copy(rows = newRows, columns = newCols)
                                        }
                                        destPage.buttons.add(buttonToDisplace.copy(
                                            isActive = false,
                                            globalIndex = freeSlot
                                        ))
                                    }
                                }
                            }

                            if (targetSlot == -1) {
                                val destOccupiedIndices = destPage.buttons.map { it.globalIndex }.toSet()
                                targetSlot = CloneHelpers.findFreeSlot(destOccupiedIndices)
                            }

                            val maxSlots = destPage.page.rows * destPage.page.columns
                            if (targetSlot >= maxSlots) {
                                val (newRows, newCols) = CloneHelpers.expandGridByOne(destPage.page.rows, destPage.page.columns)
                                destPage.page = destPage.page.copy(rows = newRows, columns = newCols)
                            }

                            val movedButton = buttonToMove.copy(
                                pageId = destPage.page.id,
                                globalIndex = targetSlot
                            )
                            destPage.buttons.add(movedButton)
                        }
                    }
                }
                "DEACTIVATE_BUTTON" -> {
                    val srcPageName = action.sourcePageName ?: return@forEach
                    val btnLabel = action.buttonLabel ?: return@forEach

                    val srcPage = mutablePages.find { it.page.name.equals(srcPageName, ignoreCase = true) }
                    if (srcPage != null) {
                        val btnIndex = srcPage.buttons.indexOfFirst { it.label.equals(btnLabel, ignoreCase = true) }
                        if (btnIndex != -1) {
                            srcPage.buttons[btnIndex] = srcPage.buttons[btnIndex].copy(isActive = false)
                        }
                    }
                }
                "SPLIT_PAGE" -> {
                    val pageName = action.sourcePageName ?: return@forEach
                    val newCategories = action.newCategories ?: return@forEach

                    val parentPage = mutablePages.find { it.page.name.equals(pageName, ignoreCase = true) }
                    if (parentPage != null) {
                        newCategories.forEach { category ->
                            val catPageId = UUID.randomUUID().toString()
                            
                            val buttonCount = category.buttonLabels.size
                            val (catRows, catCols) = CloneHelpers.optimalGridUpTo5(buttonCount)

                            val newSubpage = Page(
                                id = catPageId,
                                bookId = targetBookId,
                                name = category.name,
                                rows = catRows,
                                columns = catCols,
                                createdAt = System.currentTimeMillis()
                            )
                            val subpageButtons = mutableListOf<ButtonEntity>()
                            val subpageWrapper = MutablePageWithButtons(newSubpage, subpageButtons)

                            category.buttonLabels.forEachIndexed { index, label ->
                                val parentBtnIndex = parentPage.buttons.indexOfFirst { it.label.equals(label, ignoreCase = true) }
                                if (parentBtnIndex != -1) {
                                    val buttonToMove = parentPage.buttons.removeAt(parentBtnIndex)
                                    subpageButtons.add(buttonToMove.copy(
                                        pageId = catPageId,
                                        globalIndex = index
                                    ))
                                }
                            }

                            mutablePages.add(subpageWrapper)

                            val parentOccupiedIndices = parentPage.buttons.map { it.globalIndex }.toSet()
                            val targetNavSlot = CloneHelpers.findFreeSlot(parentOccupiedIndices)

                            val navButtonId = UUID.randomUUID().toString()
                            val navButton = CloneHelpers.createNavButton(
                                id = navButtonId,
                                pageId = parentPage.page.id,
                                targetPageId = catPageId,
                                label = category.name,
                                slot = targetNavSlot,
                                isActive = true
                            )
                            parentPage.buttons.add(navButton)
                        }
                    }
                }
            }
        }
    }
}
