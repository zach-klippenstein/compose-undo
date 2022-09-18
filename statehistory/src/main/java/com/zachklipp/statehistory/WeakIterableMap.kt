package com.zachklipp.statehistory

import java.lang.ref.WeakReference
import java.util.*

/**
 * A simple key-value store that can be iterated and stores only weak references to its keys.
 * This could be implemented directly by `WeakHashMap` on JVM, but JS's `WeakMap` type is not
 * iterable so it needs a custom implementation. This class is used on the JVM as well for
 * consistency.
 *
 * The implementation is built on three primitives, which exist in both JVM and JS:
 * 1. A weak reference type.
 * 2. A weak map that only supports get/set.
 * 3. A strongly-referenced linked list.
 *
 * The map is weakly-keyed by the actual key type, and values are "value holder" objects that hold
 * both the value and a weak reference to the key. This means all references to keys are weak.
 * Each element of the list stores a weak reference to a value holder.
 *
 * ## Operations
 *
 * - The get operation is supported by simply querying the map.
 * - The set operation checks if the key is already in the map, and:
 *    - If present, updates the value in the value holder.
 *    - Else, adds a new value holder and links it into the list.
 * - Iteration is supported by iterating over the list and ignoring elements whose weak references
 *  to their holder is invalid.
 *
 * ## Cleanup
 *
 * The weak map is kept clean by the underlying implementation – keys that are GC'd are
 * automatically removed from the map. Because the only _strong_ reference to the holder objects is
 * from the map, when a key is GC'd, its value holder is also automatically GC'd.
 *
 * To remove GC'd entries from the list however, there are two processes that amortize the work.
 * - Every time a get or set operation is performed an element from the list is checked for validity
 *  and removed if necessary. This is a constant-time operation, since only a single element is
 *  checked.
 * - Whenever the entire list is walked by [forEach], all elements whose holder has been GC'd are
 *  removed from the list as the traversal is happening. This involves no additional iteration since
 *  the entire list is being traversed anyway.
 *
 * The amortized cleanup is implemented by a single pointer to the next element to check. On every
 * step, either an element is removed, or the pointer is advanced to the next element. When the end
 * of the list is reached, the pointer is reset to the head of the list.
 */
internal class WeakIterableMap<K, V> {

    @PublishedApi
    internal class ValueHolder<K, V>(key: K, var value: V) {
        val keyRef = WeakReference(key)
    }

    @PublishedApi
    internal class ValueHolderList<K, V>(valueHolder: ValueHolder<K, V>) {
        val valueHolderRef = WeakReference(valueHolder)
        var next: ValueHolderList<K, V>? = null
    }

    /** This map is not iterable. */
    private val map = WeakHashMap<K, ValueHolder<K, V>>()

    /** List of [ValueHolder]s to allow iteration. */
    private var values: ValueHolderList<K, V>? = null

    /**
     * Used to gradually walk over the list every time a get/set operation is performed to look for
     * GC'd nodes.
     */
    private var nextCleanup = values

    operator fun get(key: K): V? {
        stepCleanup()
        return map[key]?.value
    }

    operator fun set(key: K, value: V) {
        stepCleanup()
        var valueHolder = map[key]
        if (valueHolder == null) {
            valueHolder = ValueHolder(key, value)
            val listNode = ValueHolderList(valueHolder)
            listNode.next = values
            values = listNode
            map[key] = valueHolder
        } else {
            valueHolder.value = value
        }
    }

    inline fun forEach(block: (K, V) -> Unit) {
        var element = values
        var previousElement: ValueHolderList<K, V>? = null
        while (element != null) {
            val holder = element.valueHolderRef.get()
            val key = holder?.keyRef?.get()
            if (key != null) {
                block(key, holder.value)
                previousElement = element
            } else {
                // The entry has been removed, unlink it.
                if (previousElement != null) {
                    previousElement.next = element.next
                }
            }
            element = element.next
        }
    }

    /** Performs one step of cleanup. */
    private fun stepCleanup() {
        val head = values ?: return

        // Special case: Head needs cleanup.
        if (head.valueHolderRef.get()?.keyRef?.get() == null) {
            values = head.next
            nextCleanup = values
            return
        }

        // Otherwise, keep iterating through the list.
        val element = nextCleanup ?: head
        val nextElement = element.next
        if (nextElement == null) {
            // Reached the end of the list, start over.
            nextCleanup = values
            return
        }
        if (nextElement.valueHolderRef.get()?.keyRef?.get() == null) {
            element.next = nextElement.next
            // Don't update nextCleanup so the next iteration will check nextElement.next.
        } else {
            nextCleanup = nextElement
        }
    }
}