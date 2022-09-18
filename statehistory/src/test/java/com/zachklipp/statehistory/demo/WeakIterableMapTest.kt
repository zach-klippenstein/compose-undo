package com.zachklipp.statehistory.demo

import com.google.common.truth.Truth.assertThat
import com.zachklipp.statehistory.WeakIterableMap
import org.junit.Test

class WeakIterableMapTest {

    private val map = WeakIterableMap<String, String>()

    @Test
    fun setOnceAndGet() {
        map["foo"] = "bar"
        assertThat(map["foo"]).isEqualTo("bar")
    }

    @Test
    fun setOnceAndIterate() {
        map["foo"] = "bar"
        assertThat(map.entries()).containsExactly("foo" to "bar")
    }

    @Test
    fun setSameTwiceAndGet() {
        map["foo"] = "bar"
        map["foo"] = "baz"
        assertThat(map["foo"]).isEqualTo("baz")
    }

    @Test
    fun setSameTwiceAndIterate() {
        map["foo"] = "bar"
        map["foo"] = "baz"
        assertThat(map.entries()).containsExactly("foo" to "baz")
    }

    @Test
    fun setDifferentAndGet() {
        map["a"] = "foo"
        map["b"] = "bar"
        assertThat(map["a"]).isEqualTo("foo")
        assertThat(map["b"]).isEqualTo("bar")
    }

    @Test
    fun setDifferentAndIterate() {
        map["a"] = "foo"
        map["b"] = "bar"
        assertThat(map.entries()).containsExactly("a" to "foo", "b" to "bar")
    }

    @Test
    fun getNonExistent() {
        assertThat(map["nope"]).isNull()
    }

    @Test
    fun setManyAndGet() {
        val map = WeakIterableMap<Int, Int>()
        val keys = List(1000) { key ->
            map[key] = key * 2
            return@List key
        }

        keys.forEach { key ->
            assertThat(map[key]).isEqualTo((key * 2))
        }
    }

    @Test
    fun setManyAndIterate() {
        val map = WeakIterableMap<Int, Int>()
        val keys = List(1000) { key ->
            map[key] = key * 2
            return@List key
        }

        assertThat(map.entries()).containsExactlyElementsIn(keys.map { Pair(it, it * 2) })
    }

    // TODO figure out how to unit test removals.

    private fun <K, V> WeakIterableMap<K, V>.entries() = map { key, value -> key to value }

    private inline fun <K, V, R> WeakIterableMap<K, V>.map(block: (K, V) -> R): List<R> =
        buildList {
            this@map.forEach { key, value ->
                add(block(key, value))
            }
        }
}