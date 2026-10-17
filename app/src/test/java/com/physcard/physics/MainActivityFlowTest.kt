package com.physcard.physics

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = PhysicsApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MainActivityFlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun appStartsSeedsAndOpensAStudySession() {
        compose.waitUntil(timeoutMillis = 60_000) {
            compose.onAllNodesWithText("开始学习").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("开始学习").assertIsDisplayed()
        save("app-home")

        compose.onNodeWithText("开始学习").performClick()
        compose.waitUntil(timeoutMillis = 30_000) {
            compose.onAllNodesWithText("开始作答").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("开始作答").assertIsDisplayed()
        save("app-study-intro")

        compose.onNodeWithText("开始作答").performClick()
        compose.waitUntil(timeoutMillis = 30_000) {
            compose.onAllNodesWithText("选择一个答案").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("选择一个答案").assertIsDisplayed()
        save("app-study-question")

        compose.onNodeWithTag("option-0").performClick()
        compose.waitUntil(timeoutMillis = 20_000) {
            compose.onAllNodesWithText("这次回忆有多轻松？").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("这次回忆有多轻松？").assertIsDisplayed()
        save("app-study-feedback")

        compose.onNodeWithTag("rating-GOOD").performClick()
        compose.waitUntil(timeoutMillis = 20_000) {
            compose.onAllNodesWithText("选择一个答案").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("选择一个答案").assertIsDisplayed()
    }

    private fun save(name: String) {
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(
            view.width.coerceAtLeast(1),
            view.height.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        view.draw(Canvas(bitmap))
        val output = File("build/screenshots/$name.png")
        output.parentFile?.mkdirs()
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
