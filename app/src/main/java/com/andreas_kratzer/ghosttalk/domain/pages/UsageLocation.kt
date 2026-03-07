package com.andreas_kratzer.ghosttalk.domain.pages

sealed class UsageLocation {
    abstract val id: String
    abstract val name: String

    data class PageUsage(override val id: String, override val name: String) : UsageLocation()
    data class TemplateUsage(override val id: String, override val name: String) : UsageLocation()
}
