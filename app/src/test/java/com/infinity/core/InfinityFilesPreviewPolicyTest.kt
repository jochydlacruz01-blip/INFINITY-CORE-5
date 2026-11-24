package com.infinity.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InfinityFilesPreviewPolicyTest {
    @Test
    fun supportsCommonTextFormats() {
        listOf("notes.txt", "readme.md", "data.csv", "config.json", "screen.xml", "build.gradle.kts")
            .forEach { name ->
                assertTrue("$name should be previewable", InfinityFilesPreviewPolicy.supportsTextPreview(name, null))
            }
    }

    @Test
    fun supportsTextMimeTypesRegardlessOfExtension() {
        assertTrue(InfinityFilesPreviewPolicy.supportsTextPreview("unknown.bin", "text/plain; charset=utf-8"))
        assertTrue(InfinityFilesPreviewPolicy.supportsTextPreview("unknown.bin", "application/json"))
    }

    @Test
    fun rejectsBinaryFormatsWithoutTextMimeType() {
        listOf("photo.jpg", "movie.mp4", "archive.zip", "document.pdf")
            .forEach { name ->
                assertFalse("$name should use an external viewer", InfinityFilesPreviewPolicy.supportsTextPreview(name, null))
            }
    }
}
