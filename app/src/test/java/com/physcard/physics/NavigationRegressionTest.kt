package com.physcard.physics

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression coverage for bottom-bar navigation:
 * Home -> "公式库" entry -> bottom "首页" must return to the home destination.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = PhysicsApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class NavigationRegressionTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun waitForHome() {
        compose.waitUntil(timeoutMillis = 60_000) {
            compose.onAllNodesWithText("开始学习").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForText(text: String) {
        compose.waitUntil(timeoutMillis = 20_000) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun homeLibraryEntryThenBottomHomeReturnsHome() {
        waitForHome()

        compose.onNodeWithText("全部").performScrollTo().performClick()
        waitForText("搜索公式、章节或符号")

        compose.onNodeWithTag("tab-home").performClick()
        waitForHome()
        compose.onNodeWithText("开始学习").assertIsDisplayed()

        compose.onNodeWithTag("tab-stats").performClick()
        waitForText("学习统计")
        compose.onNodeWithTag("tab-settings").performClick()
        waitForText("目标保持率")
        compose.onNodeWithTag("tab-home").performClick()
        waitForHome()
        compose.onNodeWithText("开始学习").assertIsDisplayed()
    }
}
