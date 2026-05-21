package com.andreas_kratzer.ghosttalk.core.database

import androidx.room.Embedded
import androidx.room.Relation
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.util.GridUtils

data class PageWithButtons(
    @Embedded val page: Page,
    @Relation(
        parentColumn = "id",
        entityColumn = "pageId"
    )
    val buttons: List<ButtonEntity>
) {
    fun toDomainModel(): Page {
        val buttonConfigs = MutableList<ButtonConfig?>(GridUtils.TOTAL_SLOTS) { null }
        buttons.forEach { entity ->
            if (entity.globalIndex in 0 until GridUtils.TOTAL_SLOTS) {
                buttonConfigs[entity.globalIndex] = ButtonConfig(
                    id = entity.id,
                    label = entity.label,
                    spokenText = entity.spokenText,
                    spokenTextMode = entity.spokenTextMode,
                    audioFileName = entity.audioFileName,
                    auditoryCue = entity.auditoryCue,
                    isActive = entity.isActive,
                    playActionAsAuditoryCue = entity.playActionAsAuditoryCue,
                    buttonAction = entity.buttonAction
                )
            }
        }
        
        return page.copy(buttonConfigs = buttonConfigs)
    }
}

fun Page.toButtonEntities(): List<ButtonEntity> {
    return buttonConfigs.mapIndexedNotNull { index, config ->
        config?.let {
            ButtonEntity(
                id = it.id,
                pageId = this.id,
                globalIndex = index,
                label = it.label,
                spokenText = it.spokenText,
                spokenTextMode = it.spokenTextMode,
                audioFileName = it.audioFileName,
                auditoryCue = it.auditoryCue,
                buttonAction = it.buttonAction,
                isActive = it.isActive,
                playActionAsAuditoryCue = it.playActionAsAuditoryCue
            )
        }
    }
}
