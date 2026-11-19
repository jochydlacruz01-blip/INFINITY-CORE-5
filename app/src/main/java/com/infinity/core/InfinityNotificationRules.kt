package com.infinity.core

internal fun infinityNotificationTitle(value: String?): String =
    value.orEmpty().take(300)

internal fun infinityNotificationMessage(value: String?): String =
    value.orEmpty().take(2_000)

internal fun infinityNotificationAppName(value: String?): String =
    value.orEmpty().take(200)
