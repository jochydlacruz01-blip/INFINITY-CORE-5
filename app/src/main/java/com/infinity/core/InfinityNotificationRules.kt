package com.infinity.core

internal fun infinityNotificationTitle(value: String?): String =
    value.orEmpty().take(300)

internal fun infinityNotificationMessage(value: String?): String =
    value.orEmpty().take(2_000)

internal fun infinityNotificationAppName(value: String?): String =
    value.orEmpty().take(200)

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
