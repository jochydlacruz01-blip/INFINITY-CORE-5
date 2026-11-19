package com.infinity.core

/** Limits and removes non-printable control characters from notification text. */
private fun sanitizeInfinityNotificationText(value: String?, limit: Int): String =
    value.orEmpty().filter { it == '\\n' || it == '\\t' || !it.isISOControl() }.take(limit)

internal fun infinityNotificationTitle(value: String?): String =
    sanitizeInfinityNotificationText(value, 300)

internal fun infinityNotificationMessage(value: String?): String =
    sanitizeInfinityNotificationText(value, 2_000)

internal fun infinityNotificationAppName(value: String?): String =
    sanitizeInfinityNotificationText(value, 200)

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
