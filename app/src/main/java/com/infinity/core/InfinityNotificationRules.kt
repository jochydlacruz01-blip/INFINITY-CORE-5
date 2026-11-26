package com.infinity.core

/** Removes directional formatting controls that can visually reorder or disguise notification text. */
private fun isInfinityUnsafeBidiControl(value: Char): Boolean =
    value == '\u061C' || value == '\u200E' || value == '\u200F' ||
        value in '\u202A'..'\u202E' || value in '\u2066'..'\u2069'

/** Limits and removes non-printable control characters from notification text. */
private fun sanitizeInfinityNotificationText(value: String?, limit: Int): String {
    if (limit <= 0 || value.isNullOrEmpty()) return ""

    val result = StringBuilder(minOf(value.length, limit))
    var index = 0
    while (index < value.length && result.length < limit) {
        val current = value[index]
        when {
            Character.isHighSurrogate(current) -> {
                // Keep only complete UTF-16 surrogate pairs; discard malformed input.
                if (index + 1 < value.length &&
                    Character.isLowSurrogate(value[index + 1]) &&
                    result.length + 2 <= limit
                ) {
                    result.append(current)
                    result.append(value[index + 1])
                    index += 2
                } else {
                    index++
                }
            }
            Character.isLowSurrogate(current) -> index++
            current == '\n' || current == '\t' ||
                (!current.isISOControl() && !isInfinityUnsafeBidiControl(current)) -> {
                result.append(current)
                index++
            }
            else -> index++
        }
    }
    return result.toString()
}

internal fun infinityNotificationTitle(value: String?): String =
    sanitizeInfinityNotificationText(value, 300)

internal fun infinityNotificationMessage(value: String?): String =
    sanitizeInfinityNotificationText(value, 2_000)

internal fun infinityNotificationAppName(value: String?): String =
    sanitizeInfinityNotificationText(value, 200)

/** Uses a sanitized package identifier when Android cannot provide an app label. */
internal fun infinityNotificationAppNameOrFallback(value: String?, fallback: String?): String =
    infinityNotificationAppName(value).ifBlank {
        infinityNotificationAppName(fallback).ifBlank { "Aplicación" }
    }

/**
 * Returns notification text suitable for the in-memory feed.
 * When privacy mode is enabled, the original text is never retained.
 */
internal fun infinityNotificationContent(
    title: String?,
    message: String?,
    hideContent: Boolean
): Pair<String, String> {
    if (hideContent) return "" to ""
    return infinityNotificationTitle(title) to infinityNotificationMessage(message)
}

/** Updates the in-memory notification feed by key and caps retained items. */
internal fun infinityNotificationFeedUpdate(current: List<InfinityNotification>, incoming: InfinityNotification, maximum: Int = 100): List<InfinityNotification> {
    if (maximum <= 0 || incoming.key.isBlank()) return current.distinctBy { it.key }.take(maximum.coerceAtLeast(0))
    // A newly posted or updated notification is the most recent activity.
    // Remove every stale duplicate before promoting the canonical entry.
    return buildList(minOf(maximum, current.size + 1)) {
        add(incoming)
        current.asSequence()
            .filterNot { it.key == incoming.key }
            .distinctBy { it.key }
            .take(maximum - 1)
            .forEach(::add)
    }
}


/** Derives a truthful notification-listener state from Android permission and service connection. */
internal fun infinityNotificationAccessEnabled(granted: Boolean, connected: Boolean): Boolean =
    granted && connected


/**
 * Applies privacy mode to notifications already retained in the in-memory feed.
 * Redaction is irreversible for that feed snapshot: disabling privacy later must
 * not reconstruct text that was deliberately erased.
 */
internal fun infinityNotificationApplyPrivacy(
    feed: List<InfinityNotification>,
    hideContent: Boolean
): List<InfinityNotification> = if (!hideContent) {
    feed
} else {
    feed.map { notification ->
        if (notification.title.isEmpty() && notification.message.isEmpty()) notification
        else notification.copy(title = "", message = "")
    }
}
