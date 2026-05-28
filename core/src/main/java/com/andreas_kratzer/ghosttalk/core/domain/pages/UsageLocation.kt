package com.andreas_kratzer.ghosttalk.core.domain.pages

sealed class UsageLocation {
    abstract val id: String
    abstract val name: String
    abstract val buttonLabel: String
    abstract val index: Int

    data class PageUsage(override val id: String, override val name: String, override val buttonLabel: String, override val index: Int) : UsageLocation()
    data class TemplateUsage(override val id: String, override val name: String, override val buttonLabel: String, override val index: Int) : UsageLocation()
}
