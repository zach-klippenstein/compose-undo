package com.zachklipp.statehistory.demo

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zachklipp.statehistory.*
import kotlin.math.roundToInt

@Composable
fun StateHistoryInspector(
    stateHistory: StateHistory,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(modifier) {
        Box(propagateMinConstraints = true, modifier = Modifier.weight(1f)) {
            CompositionLocalProvider(LocalStateHistory provides stateHistory, content = content)
        }
        HistoryScrubber(stateHistory)
    }
}

@Composable
private fun HistoryScrubber(stateHistory: StateHistory, modifier: Modifier = Modifier) {
    var recording by remember { mutableStateOf(true) }

    if (recording) {
        LaunchedEffect(stateHistory) {
            stateHistory.recordChanges {
                // Save a new frame every time something changes. This could also be done on a
                // timer, or only after significant actions.
                stateHistory.saveFrame()
            }
        }
    }

    Column(modifier.padding(8.dp)) {
        if (stateHistory.frameCount > 1) {
            Text(
                "Frame ${stateHistory.currentFrame} of ${stateHistory.frameCount - 1}",
                modifier = Modifier.align(Alignment.CenterHorizontally),
                style = MaterialTheme.typography.caption,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Slider(
                    value = stateHistory.currentFrame.toFloat(),
                    valueRange = 0f..(stateHistory.frameCount - 1).toFloat(),
                    steps = stateHistory.frameCount - 2,
                    onValueChange = { stateHistory.setCurrentFrameGlobally(it.roundToInt()) },
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                )
            }

            Row(modifier = Modifier.fillMaxWidth()) {
                IconButton(
                    onClick = { stateHistory.undo() },
                    enabled = stateHistory.canUndo,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "undo")
                }
                Button(onClick = { recording = !recording }) {
                    Text(if (recording) "Stop recording" else "Start recording")
                }
                IconButton(
                    onClick = { stateHistory.redo() },
                    enabled = stateHistory.canRedo,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.ArrowForward, contentDescription = "redo")
                }
            }
        }
    }
}