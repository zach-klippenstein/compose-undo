package com.zachklipp.statehistory.demo

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import com.google.common.truth.Truth.assertThat
import com.zachklipp.statehistory.StateHistory
import com.zachklipp.statehistory.redo
import com.zachklipp.statehistory.undo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Test

class StateHistoryTest {

    private val stateHistory = StateHistory()
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun initialFrameCount() {
        assertThat(stateHistory.frameCount).isEqualTo(0)
    }

    @Test
    fun initialCurrentFrame() {
        assertThat(stateHistory.currentFrame).isEqualTo(StateHistory.NoFrame)
    }

    @Test
    fun recordsChanges_whenStartTrackingAfterRecording() {
        scope.launch {
            stateHistory.recordChanges {
                stateHistory.saveFrame()
            }
        }
        val state = mutableStateOf(0)
        stateHistory.startTrackingState(state)
        assertThat(stateHistory.frameCount).isEqualTo(0)

        Snapshot.sendApplyNotifications()
        assertThat(stateHistory.frameCount).isEqualTo(0)

        Snapshot.withMutableSnapshot {
            state.value = 1
        }

        assertThat(stateHistory.frameCount).isEqualTo(1)
        assertThat(stateHistory.currentFrame).isEqualTo(0)
    }

    @Test
    fun recordsChanges_whenStartRecordingAfterTracking() {
        val state = mutableStateOf(0)
        stateHistory.startTrackingState(state)
        assertThat(stateHistory.frameCount).isEqualTo(0)

        Snapshot.sendApplyNotifications()
        assertThat(stateHistory.frameCount).isEqualTo(0)

        scope.launch {
            stateHistory.recordChanges {
                stateHistory.saveFrame()
            }
        }

        Snapshot.withMutableSnapshot {
            state.value = 1
        }

        assertThat(stateHistory.frameCount).isEqualTo(1)
        assertThat(stateHistory.currentFrame).isEqualTo(0)
    }

    @Test
    fun noops_whenChangeCallbackIsNull() {
        scope.launch {
            stateHistory.recordChanges()
        }
        val state = mutableStateOf(0)
        stateHistory.startTrackingState(state)

        Snapshot.withMutableSnapshot {
            state.value = 1
        }

        assertThat(stateHistory.frameCount).isEqualTo(0)
        assertThat(stateHistory.currentFrame).isEqualTo(StateHistory.NoFrame)
    }

    @Test
    fun setCurrentFrameGlobally_restoresState() {
        scope.launch {
            stateHistory.recordChanges {
                stateHistory.saveFrame()
            }
        }
        val state = mutableStateOf(0)
        stateHistory.startTrackingState(state)

        Snapshot.withMutableSnapshot {
            state.value = 1
        }
        Snapshot.withMutableSnapshot {
            state.value = 2
        }
        assertThat(stateHistory.frameCount).isEqualTo(2)
        assertThat(stateHistory.currentFrame).isEqualTo(1)
        assertThat(state.value).isEqualTo(2)

        stateHistory.setCurrentFrameGlobally(0)

        assertThat(stateHistory.frameCount).isEqualTo(2)
        assertThat(stateHistory.currentFrame).isEqualTo(0)
        assertThat(state.value).isEqualTo(1)
    }

    @Test
    fun settingState_afterUndo_clearsPreviousFutureState() {
        scope.launch {
            stateHistory.recordChanges {
                stateHistory.saveFrame()
            }
        }
        val state = mutableStateOf(0)
        stateHistory.startTrackingState(state)

        Snapshot.withMutableSnapshot {
            state.value = 1
        }
        Snapshot.withMutableSnapshot {
            state.value = 2
        }
        assertThat(stateHistory.frameCount).isEqualTo(2)
        assertThat(stateHistory.currentFrame).isEqualTo(1)
        assertThat(state.value).isEqualTo(2)

        // Undo one and change the value.
        stateHistory.setCurrentFrameGlobally(0)
        Snapshot.withMutableSnapshot {
            state.value = 3
        }

        // That should have overwritten the previous frame in which state was 2.
        assertThat(stateHistory.frameCount).isEqualTo(2)
        assertThat(stateHistory.currentFrame).isEqualTo(1)
        assertThat(state.value).isEqualTo(3)

        // But preserve the frame where state is 1.
        stateHistory.setCurrentFrameGlobally(0)
        assertThat(stateHistory.frameCount).isEqualTo(2)
        assertThat(stateHistory.currentFrame).isEqualTo(0)
        assertThat(state.value).isEqualTo(1)
    }

    @Test
    fun tracksMultipleStateChanges() {
        scope.launch {
            stateHistory.recordChanges {
                stateHistory.saveFrame()
            }
        }
        val state1 = mutableStateOf(0)
        stateHistory.startTrackingState(state1)

        Snapshot.withMutableSnapshot {
            state1.value = 1
        }
        assertThat(stateHistory.frameCount).isEqualTo(1)

        val state2 = mutableStateOf(10)
        stateHistory.startTrackingState(state2)

        Snapshot.withMutableSnapshot {
            state2.value = 20
        }
        assertThat(stateHistory.frameCount).isEqualTo(2)
    }

    @Test
    fun restoresMultipleStateChanges() {
        scope.launch {
            stateHistory.recordChanges {
                stateHistory.saveFrame()
            }
        }
        val state1 = mutableStateOf(0)
        stateHistory.startTrackingState(state1)
        Snapshot.withMutableSnapshot {
            state1.value = 1
        }
        val state2 = mutableStateOf(0)
        stateHistory.startTrackingState(state2)
        Snapshot.withMutableSnapshot {
            state2.value = 10
        }
        Snapshot.withMutableSnapshot {
            state2.value = 20
        }

        // Add a frame where both are set at once.
        Snapshot.withMutableSnapshot {
            state1.value = 2
            state2.value = 30
        }

        stateHistory.undo()
        assertThat(state1.value).isEqualTo(1)
        assertThat(state2.value).isEqualTo(20)

        stateHistory.undo()
        assertThat(state1.value).isEqualTo(1)
        assertThat(state2.value).isEqualTo(10)

        stateHistory.redo()
        assertThat(state1.value).isEqualTo(1)
        assertThat(state2.value).isEqualTo(20)
    }

    @Test
    fun stopTracking_whenStateObjectUnregistered() {
        scope.launch {
            stateHistory.recordChanges {
                stateHistory.saveFrame()
            }
        }
        val state = mutableStateOf(0)
        stateHistory.startTrackingState(state)
        Snapshot.withMutableSnapshot {
            state.value = 1
        }
        assertThat(stateHistory.frameCount).isEqualTo(1)
        assertThat(stateHistory.currentFrame).isEqualTo(0)

        stateHistory.stopTrackingState(state)

        Snapshot.withMutableSnapshot {
            state.value = 2
        }
        assertThat(stateHistory.frameCount).isEqualTo(1)
        assertThat(stateHistory.currentFrame).isEqualTo(0)
    }

    @Test
    fun stopTracking_whenRecordingStopped() {
        val job = scope.launch {
            stateHistory.recordChanges {
                stateHistory.saveFrame()
            }
        }
        val state = mutableStateOf(0)
        stateHistory.startTrackingState(state)
        Snapshot.withMutableSnapshot {
            state.value = 1
        }
        assertThat(stateHistory.frameCount).isEqualTo(1)
        assertThat(stateHistory.currentFrame).isEqualTo(0)

        job.cancel()

        Snapshot.withMutableSnapshot {
            state.value = 2
        }
        assertThat(stateHistory.frameCount).isEqualTo(1)
        assertThat(stateHistory.currentFrame).isEqualTo(0)
    }
}