package com.zachklipp.statehistory

import androidx.compose.runtime.*
import androidx.compose.runtime.collection.mutableVectorOf
import androidx.compose.runtime.snapshots.*
import kotlinx.coroutines.awaitCancellation
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

private const val DEBUG = true
private const val TAG = "StateHistory"

val LocalStateHistory: ProvidableCompositionLocal<StateHistory?> = staticCompositionLocalOf { null }

/**
 * Convenience function for simple usage of [StateHistory].
 *
 * Inside [content], call [MutableState.trackStateHistory], [SnapshotStateList.trackStateHistory],
 * and [SnapshotStateMap.trackStateHistory] to register state objects for change tracking, or pass
 * them to [StateHistory.startTrackingState] and [StateHistory.stopTrackingState]. Then use the
 * [StateHistory.undo], [StateHistory.redo], and [StateHistory.setCurrentFrameGlobally] methods
 * to restore state.
 */
@Composable
fun WithStateHistory(content: @Composable (StateHistory) -> Unit) {
    val stateHistory = remember { StateHistory() }

    LaunchedEffect(stateHistory) {
        stateHistory.recordChanges {
            stateHistory.saveFrame()
        }
    }

    CompositionLocalProvider(LocalStateHistory provides stateHistory) {
        content(stateHistory)
    }
}

/**
 * Records changes to state objects that are registered via [startTrackingState] (or
 * [trackStateHistory]) and allows restoring state to previous values.
 *
 * ## Usage
 *
 * ### Simple
 *
 * 1. Wrap your composition in [WithStateHistory].
 * 2. Call [MutableState.trackStateHistory] on the states you want to track. (See also
 *  [SnapshotStateList.trackStateHistory], [SnapshotStateMap.trackStateHistory].)
 * 3. Call [StateHistory.undo] to undo changes.
 *
 * ### Advanced
 *
 * 1. Create an instance of this that will live across recompositions (e.g. use remember or store
 *  near the top of your DI object graph).
 * 1. Provide the instance to your composition through the [LocalStateHistory] composition local.
 * 1. Call [recordChanges] from a coroutine to start recording changes. To save a new history frame
 *  every time some state changes are applied, pass a callback that calls [saveFrame].
 * 1. Register states you want to track with [startTrackingState]. Make sure to call
 *  [stopTrackingState] when finished with the state object to allow it to be garbage-collected.
 *  If creating the state directly in a composition (e.g. with [remember]), you can also pass the
 *  state object to [trackStateHistory] to automatically stop tracking it when it leaves the
 *  composition.
 * 1. Call [setCurrentFrameGlobally] to restore the state of all tracked objects to the states they
 *  had at the given frame, or call the convenience methods [undo]/[redo] to move the frame by one.
 *
 * E.g.:
 * @sample WithStateHistory
 */
@Stable
class StateHistory {

    /**
     * Used to guard access to [frames], [trackedStates], and [nextFrame].
     */
    private val lock = Any()

    /**
     * The set of all state objects we're currently tracking via [startTrackingState].
     */
    private val trackedStates = mutableSetOf<StateObject>()
    private var handle: ObserverHandle? = null

    /**
     * By storing and restoring [StateRecord]s directly, we don't have to care what types the state
     * objects are or even what data they hold. The records already implement all the operations
     * we need.
     */
    private val frames = mutableVectorOf<MutableMap<StateObject, StateRecord>>()

    /**
     * We "start" recording the next frame as soon as the previous one is committed to the [frames]
     * list. This way we can eagerly record the initial value of newly-tracked objects from
     * [startTrackingState] so that their initial values will be saved on the next frame, even if
     * they're not recorded as changed. Maybe unnecessary?
     */
    private var nextFrame = mutableMapOf<StateObject, StateRecord>()

    private var onTrackStateApplied: (() -> Unit)? = null

    /**
     * The total number of history frames that have been recorded.
     */
    var frameCount: Int by mutableStateOf(0)
        private set

    /**
     * The index of frame that represents the current state. When the global snapshot is advanced
     * with changes to tracked state, this value will be incremented. It can also be set to a lower
     * value by calling [setCurrentFrameGlobally].
     */
    var currentFrame: Int by mutableStateOf(NoFrame)
        private set

    /**
     * Starts recording changes and suspends until cancelled.
     *
     * @param onTrackStateApplied Called every time a snapshot is applied to the global snapshot
     * that changes any of the state objects tracked via [startTrackingState]. Call [saveFrame] from
     * this callback to save every change to the history.
     */
    suspend fun recordChanges(onTrackStateApplied: (() -> Unit)? = null) {
        if (!startRecording(onTrackStateApplied)) return
        try {
            awaitCancellation()
        } finally {
            stopRecording(clearOnTrackStateApplied = true)
        }
    }

    /**
     * Saves any changes to tracked state objects since the last call to this method to the history.
     *
     * If [currentFrame] is not the last frame, any frames after it will be removed.
     */
    fun saveFrame() {
        Snapshot.global {
            Snapshot.withMutableSnapshot {
                // Take the lock inside the snapshot so we don't conflict with the lock in the
                // apply observer when the snapshot is applied.
                synchronized(lock) {
                    if (DEBUG) println("[$TAG] Recording frame ${currentFrame + 1}: $nextFrame")
                    // If committing a new frame while peeking into the history, clear the
                    // frames after this point and start recording fresh.
                    if (frames.lastIndex > currentFrame) {
                        frames.removeRange(currentFrame + 1, frames.size)
                    }

                    frames += nextFrame
                    nextFrame = mutableMapOf()
                    frameCount = frames.size
                    currentFrame = frames.lastIndex
                }
            }
        }
    }

    /**
     * Starts tracking changes [stateObject]. The current value of the object will be recorded in
     * the next frame, i.e. the next time the global snapshot is applied while
     * [recording][startRecording].
     *
     * All objects passed to this method must eventually be passed to [stopTrackingState] to avoid
     * leaking.
     *
     * @param stateObject Must be a snapshot state object, such as a [MutableState],
     * [SnapshotStateList], etc. The concrete object must implement [StateObject].
     */
    fun <S : Any> startTrackingState(stateObject: S) {
        ensureValidStateObject(stateObject)
        // Record the initial state of the object in the next frame to be committed.
        val initialRecord = stateObject.copyCurrentRecord()
        synchronized(lock) {
            trackedStates += stateObject
            nextFrame[stateObject] = initialRecord
        }
        if (DEBUG) println(
            "[$TAG] Starting to track object $stateObject with initial record $initialRecord"
        )
    }

    /**
     * Stops tracking changes to [stateObject].
     */
    fun stopTrackingState(stateObject: Any) {
        stateObject as StateObject
        if (DEBUG) println("[$TAG] Stopping tracking object $stateObject")
        synchronized(lock) {
            trackedStates -= stateObject
            nextFrame -= stateObject
            // Remove all references to the state object from the history so it can be GC'd.
            // TODO Figure out a way to do this without iterating through the entire history.
            //  WeakRefs?
            frames.forEach {
                it -= stateObject
            }
        }
    }

    /**
     * Restores the values of all tracked state objects to the values they had at the frame at
     * [frameIndex]. Calling this method will not change [frameCount] immediately, although the next
     * time a tracked state is changed and applied to the global snapshot, all frames between
     * [currentFrame] and [frameCount] will be removed and replaced with a new frame.
     *
     * The [frameIndex] will be coerced into the valid frame range, and this function will do
     * nothing if equal to [currentFrame].
     */
    fun setCurrentFrameGlobally(frameIndex: Int) {
        if (frameIndex == currentFrame) return

        // Stop recording so we don't record the application of the mutable snapshot below as a
        // new frame. Use the return value instead of isRecording to avoid a race.
        // Don't clear the callback so we can re-use it after.
        val wasRecording = stopRecording(clearOnTrackStateApplied = false)
        if (DEBUG) println(
            "[$TAG] Pausing recording to set current frame (wasRecording=$wasRecording)"
        )

        // Peeking in non-global snapshots is not currently supported.
        Snapshot.global {
            Snapshot.withMutableSnapshot {
                synchronized(lock) {
                    @Suppress("NAME_SHADOWING")
                    val frameIndex = frameIndex.coerceIn(0, frames.lastIndex)
                    if (DEBUG) println("[$TAG] Setting current frame to $frameIndex")
                    // Check again after coercing.
                    if (frameIndex == currentFrame) return
                    currentFrame = frameIndex
                    trackedStates.forEach { stateObject ->
                        // Find the latest frame containing the state object, if any.
                        var readFrameIndex = frameIndex
                        while (readFrameIndex >= 0 && stateObject !in frames[readFrameIndex]) {
                            readFrameIndex--
                        }
                        if (readFrameIndex >= 0) {
                            val frame = frames[readFrameIndex]
                            val record = frame.getValue(stateObject)
                            if (DEBUG) println("[$TAG] Restoring $stateObject to $record")
                            stateObject.restoreFrom(record)
                        } else if (DEBUG) {
                            println("[$TAG] Couldn't find value for $stateObject in history")
                        }
                    }

                    // When the mutableSnapshot is applied, we'll synchronously get an apply
                    // observer callback - or we would, if we hadn't stopped recording. After that,
                    // it's safe to stop recording again.
                }
            }
        }

        if (wasRecording) {
            if (DEBUG) println("[$TAG] Resuming recording after setting current frame")
            // Keep using the old callback.
            startRecording(onTrackStateApplied)
        }
    }

    /**
     * Starts recording state changes. This is a no-op if recording has already been started.
     *
     * @return True if recording was successfully started, false if already recording.
     */
    private fun startRecording(onTrackStateApplied: (() -> Unit)?): Boolean {
        if (handle != null) {
            if (DEBUG) println("[$TAG] Recording already started, ignoring start request")
            return false
        }
        if (DEBUG) println("[$TAG] Starting recording")
        synchronized(lock) {
            if (handle != null) return false
            this.onTrackStateApplied = onTrackStateApplied
            handle = Snapshot.registerApplyObserver(this::onApply)
            return true
        }
    }

    /**
     * Stops recording state changes.
     *
     * @return True if recording was stopped, false if it was not started.
     */
    private fun stopRecording(clearOnTrackStateApplied: Boolean): Boolean {
        if (DEBUG) println("[$TAG] Stopping recording")
        if (handle == null) return false
        synchronized(lock) {
            if (handle == null) return false
            handle!!.dispose()
            handle = null
            if (clearOnTrackStateApplied) {
                onTrackStateApplied = null
            }
            return true
        }
    }

    private fun onApply(changedStates: Set<Any>, snapshot: Snapshot) {
        // Read values inside the snapshot to avoid race conditions.
        val readSnapshot = snapshot.takeNestedSnapshot()
        var atLeastOneTrackedStateChanged = false
        try {
            readSnapshot.enter {
                synchronized(lock) {
                    changedStates.forEach { stateObject ->
                        stateObject as StateObject
                        if (stateObject in trackedStates) {
                            atLeastOneTrackedStateChanged = true
                            nextFrame[stateObject] = stateObject.copyCurrentRecord()
                        }
                    }
                }
            }
        } finally {
            readSnapshot.dispose()
        }

        if (atLeastOneTrackedStateChanged) {
            if (DEBUG) println("[$TAG] Tracked state changes were applied, invoking callback")
            onTrackStateApplied?.invoke()
        }
    }

    companion object {
        const val NoFrame: Int = -1
    }
}

@Composable
fun <S : Any> trackStateHistory(stateObject: S): S {
    ensureValidStateObject(stateObject)
    val stateHistory = LocalStateHistory.current ?: return stateObject
    DisposableEffect(stateHistory, stateObject) {
        stateHistory.startTrackingState(stateObject)
        onDispose {
            stateHistory.stopTrackingState(stateObject)
        }
    }
    return stateObject
}

val StateHistory.canUndo: Boolean get() = currentFrame > 0
val StateHistory.canRedo: Boolean get() = currentFrame < frameCount - 1

/**
 * Sets the current frame to the previous frame.
 */
fun StateHistory.undo() {
    setCurrentFrameGlobally(currentFrame - 1)
}

/**
 * Sets the current frame to the next frame.
 */
fun StateHistory.redo() {
    setCurrentFrameGlobally(currentFrame + 1)
}

@Suppress("NOTHING_TO_INLINE")
@Composable
inline fun <T> MutableState<T>.trackStateHistory(): MutableState<T> =
    trackStateHistory(this)

@Suppress("NOTHING_TO_INLINE")
@Composable
inline fun <T> SnapshotStateList<T>.trackStateHistory(): SnapshotStateList<T> =
    trackStateHistory(this)

@Suppress("NOTHING_TO_INLINE")
@Composable
inline fun <K, V> SnapshotStateMap<K, V>.trackStateHistory(): SnapshotStateMap<K, V> =
    trackStateHistory(this)

/**
 * Returns a copy of this object's current readable record, without recording a read. The record
 * is a copy since the original record could be reused at any point in the future.
 */
private fun StateObject.copyCurrentRecord(): StateRecord {
    val newRecord = firstStateRecord.create()
    firstStateRecord.withCurrent { current ->
        newRecord.assign(current)
    }
    return newRecord
}

/**
 * Copies [record] into the write record for this state object in the current snapshot.
 */
private fun StateObject.restoreFrom(record: StateRecord) {
    firstStateRecord.writable(this) {
        assign(record)
    }
}

@OptIn(ExperimentalContracts::class)
private fun ensureValidStateObject(stateObject: Any) {
    contract { returns() implies (stateObject is StateObject) }
    require(stateObject is StateObject) {
        "Can only track objects that are instance of StateObject, which this is not: $stateObject"
    }
}