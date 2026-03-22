package com.andreas_kratzer.ghosttalk.domain.pages

sealed class UsageLocation {
    abstract val id: String
    abstract val name: String
    abstract val buttonLabel: String

    data class PageUsage(override val id: String, override val name: String, override val buttonLabel: String) : UsageLocation()
    data class TemplateUsage(override val id: String, override val name: String, override val buttonLabel: String) : UsageLocation()
}
