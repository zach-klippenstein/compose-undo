package com.zachklipp.statehistory.demo

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.zachklipp.statehistory.trackStateHistory

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun AppPreview() {
    App()
}

@Composable
fun App() {
    var textValue by remember { mutableStateOf(TextFieldValue("")) }.trackStateHistory()
    val valueList = remember { mutableStateListOf<String>() }.trackStateHistory()

    fun addValueToList() {
        valueList += textValue.text
        textValue = TextFieldValue("")
    }

    Column(Modifier.padding(8.dp)) {
        TextField(
            modifier = Modifier.fillMaxWidth(),
            value = textValue,
            onValueChange = { textValue = it },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions { addValueToList() }
        )
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = { addValueToList() }
        ) {
            Text("Add to list")
        }

        LazyColumn(
            reverseLayout = true,
            modifier = Modifier
                .border(1.dp, Color.Black)
                .fillMaxWidth()
        ) {
            items(valueList) {
                Text(it, modifier = Modifier.padding(16.dp))
            }
        }
    }
}