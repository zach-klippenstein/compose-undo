package com.zachklipp.statehistory.demo

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.zachklipp.statehistory.StateHistory
import com.zachklipp.statehistory.WithStateHistory
import com.zachklipp.statehistory.trackStateHistory
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StateHistoryTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun withStateHistory_recordsChanges() {
        val state = mutableStateOf(0)
        lateinit var stateHistory: StateHistory
        rule.setContent {
            WithStateHistory {
                stateHistory = it
                state.trackStateHistory()
            }
        }

        rule.runOnIdle {
            assertThat(stateHistory.frameCount).isEqualTo(0)
            state.value = 1
        }

        rule.runOnIdle {
            assertThat(stateHistory.frameCount).isEqualTo(1)
        }
    }
}