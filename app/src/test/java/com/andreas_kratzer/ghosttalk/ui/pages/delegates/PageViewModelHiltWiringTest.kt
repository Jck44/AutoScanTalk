package com.andreas_kratzer.ghosttalk.ui.pages.delegates

import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.inject.Inject

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, manifest = Config.NONE, sdk = [35])
class PageViewModelHiltWiringTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var pageManagementDelegate1: PageManagementDelegate

    @Inject
    lateinit var pageManagementDelegate2: PageManagementDelegate

    @Inject
    lateinit var navigationDelegate: NavigationDelegate

    @Inject
    lateinit var pageResolutionDelegate: PageResolutionDelegate

    @Before
    fun init() {
        hiltRule.inject()
    }

    @Test
    fun verifyHiltInjectsSameSingletonInstances() {
        // Assert that Hilt injects the exact same instances for our scoped delegates
        assertSame(
            "PageManagementDelegate must be a Singleton so all components share the same state flow",
            pageManagementDelegate1,
            pageManagementDelegate2
        )

        val field = NavigationDelegate::class.java.getDeclaredField("pageManagementDelegate")
        field.isAccessible = true
        val pageManagementDelegateInNav = field.get(navigationDelegate)

        assertSame(
            "PageManagementDelegate in NavigationDelegate must be the same Singleton instance",
            pageManagementDelegate1,
            pageManagementDelegateInNav
        )
    }
}
