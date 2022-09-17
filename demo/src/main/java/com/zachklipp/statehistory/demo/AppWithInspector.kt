package com.zachklipp.statehistory.demo

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zachklipp.statehistory.StateHistory
import com.zachklipp.statehistory.demo.ui.theme.StateHistoryDemoTheme

@Composable
fun AppWithInspector() {
    val stateHistory = viewModel<StateHistoryViewModel>().stateHistory
    StateHistoryDemoTheme {
        // A surface container using the 'background' color from the theme
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colors.background
        ) {
            StateHistoryInspector(stateHistory) {
                App()
            }
        }
    }
}

class StateHistoryViewModel : ViewModel() {
    val stateHistory = StateHistory()
}