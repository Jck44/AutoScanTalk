package com.andreas_kratzer.ghosttalk.ui.pages.delegates

import org.junit.Assert.assertTrue
import org.junit.Test
import javax.inject.Singleton

class DelegateScopingTest {

    @Test
    fun verifyAllDelegatesAreAnnotatedWithSingleton() {
        val delegates = listOf(
            AiRestructureDelegate::class.java,
            AnalyticsDelegate::class.java,
            ButtonTemplateDelegate::class.java,
            CallManagementDelegate::class.java,
            InteractionDelegate::class.java,
            LayoutWizardDelegate::class.java,
            NavigationDelegate::class.java,
            PageManagementDelegate::class.java,
            PageResolutionDelegate::class.java,
            PageSplitDelegate::class.java,
            ScreenManagementDelegate::class.java,
            SmartIntegrationDelegate::class.java,
            SmartPredictionDelegate::class.java,
            SuggestionsDelegate::class.java,
            TtsPreviewDelegate::class.java
        )

        for (delegateClass in delegates) {
            val hasSingleton = delegateClass.isAnnotationPresent(Singleton::class.java)
            assertTrue(
                "Delegate ${delegateClass.simpleName} must be annotated with @Singleton to prevent state mismatch and transient injection issues in Hilt.",
                hasSingleton
            )
        }
    }
}
