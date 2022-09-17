package com.zachklipp.statehistory.demo

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DemoTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun typingUpdatesFrameCount() {
        rule.setContent {
            AppWithInspector()
        }

        rule.onNode(hasSetTextAction()).performTextInput("a")
        rule.onNodeWithText("Frame 1 of 1").assertIsDisplayed()

        rule.onNode(hasSetTextAction()).performTextInput("b")
        rule.onNodeWithText("Frame 2 of 2").assertIsDisplayed()
    }

    @Test
    fun addingToListUpdatesFrameCount() {
        rule.setContent {
            AppWithInspector()
        }

        rule.onNode(hasSetTextAction()).performTextInput("a")
        rule.onNodeWithText("Add to list").performClick()

        rule.onNodeWithText("Frame 2 of 2").assertIsDisplayed()
    }

    @Test
    fun sliderScrubsHistory() {
        rule.setContent {
            AppWithInspector()
        }

        rule.onNode(hasSetTextAction()).performTextInput("a")
        rule.onNode(hasSetTextAction()).performTextInput("b")
        rule.onNode(hasSetTextAction()).assertTextEquals("ab")

        rule.onNode(hasSetProgressAction()).assertProgressValue(2f)
        rule.onNode(hasSetProgressAction()).performSetProgress(1f)

        rule.onNode(hasSetTextAction()).assertTextEquals("a")
        rule.onNodeWithText("Frame 1 of 2").assertIsDisplayed()
    }

    private fun hasSetProgressAction() = SemanticsMatcher("has setProgress action") {
        it.config.contains(SemanticsActions.SetProgress)
    }

    private fun SemanticsNodeInteraction.assertProgressValue(value: Float) {
        assertThat(
            fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current
        ).isEqualTo(value)
    }

    private fun SemanticsNodeInteraction.performSetProgress(value: Float) {
        fetchSemanticsNode().config[SemanticsActions.SetProgress].action!!.invoke(value)
    }
}