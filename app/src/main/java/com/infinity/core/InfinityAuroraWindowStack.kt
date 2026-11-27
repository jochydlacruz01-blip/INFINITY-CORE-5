package com.infinity.core

/**
 * Window stacking policy: the active window is rendered last, so Compose draws it above
 * earlier siblings without changing window identity, workspace, or geometry.
 */
internal fun <T> infinityBringItemToFront(
    items: List<T>,
    selected: T,
    key: (T) -> String
): List<T> {
    val selectedKey = key(selected)
    val index = items.indexOfFirst { key(it) == selectedKey }
    if (index < 0) return items
    if (index == items.lastIndex) return items
    return items.toMutableList().also {
        val active = it.removeAt(index)
        it.add(active)
    }
}

/** Shared z-order action used when a window is dragged or resized. */
internal fun <T> infinityRaiseWindowAfterManipulation(
    items: List<T>,
    manipulated: T,
    key: (T) -> String
): List<T> = infinityBringItemToFront(items, manipulated, key)
