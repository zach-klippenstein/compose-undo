# compose-undo ![Maven Central](https://img.shields.io/maven-central/v/com.zachklipp.compose-undo/statehistory)

Track changes to any snapshot state object and restore state from any point in the past.

## Usage

```groovy
implementation 'com.zachklipp.compose-undo:statehistory:{version}'
```

The simplest way to get started is to use the `WithStateHistory` composable:

```kotlin
@Composable
fun App() {
    WithStateHistory { history ->
        var text by remember { mutableStateOf(TextFieldValue("")) }.trackStateChanges()
        TextField(text, onValueChange = { text = it })

        Button(onClick { history.undo() }) {
            Text("Undo")
        }
    }
}
```

The key is to call `trackStateChanges` on every snapshot state object you want to track. If you're
creating state objects outside a composition, call `StateHistory.startTrackingState` yourself.

## Advanced usage

The main API is the `StateHistory` class. See
[its kdoc](/statehistory/src/main/java/com/zachklipp/statehistory/StateHistory.kt) for more detailed
information.

## Demo

This repo includes a demo app you can run and tinker with if you fork the repo. Here's a little
preview:

https://user-images.githubusercontent.com/101754/190877868-160456a2-5bd1-498d-9c76-147fb04958e6.mp4

## How it works

`StateHistory` keeps a set of all the state objects that were registered on it. It registers an
apply listener to the snapshot system, and any time a snapshot is applied to the global snapshot it
checks if any of the objects changed by that snapshot are being tracked. For every tracked changed
object, it makes a copy of its latest state record. It collects all changes to tracked objects in a
map (called a "frame"), then when `saveFrame` is called, it pushes that map onto the list of frames
that represents the history.

When asked to restore states to a particular frame, it goes through every tracked state object and
searches the frame list from the requested frame to find the latest frame that captured a change to
that object. It then asks the snapshot system for a writable record for that object and copies the
saved record back into the writable record, effectively setting the state object's value.

This is a very unconventional and probably unsupported use case of the
[`StateObject`](https://developer.android.com/reference/kotlin/androidx/compose/runtime/snapshots/StateObject)
and 
[`StateRecord`](https://developer.android.com/reference/kotlin/androidx/compose/runtime/snapshots/StateRecord)
APIs, but it allows the library to support any type of state object, even custom third-party ones.
The actual implementation for saving and restoring state values looks something like this
(`stateObject` is a `StateObject`):

```kotlin
// Save a state object's current value
val savedRecord = stateObject.firstStateRecord.create()
stateObject.firstStateRecord.withCurrent { currentRecord ->
    savedRecord.assign(currentRecord)
}

// Restore the value
stateObject.firstStateRecord.writable(stateObject) {
    assign(savedRecord)
}
```
