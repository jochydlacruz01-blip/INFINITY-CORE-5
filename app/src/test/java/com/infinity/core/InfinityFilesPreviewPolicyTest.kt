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

    @Test
    fun explicitBinaryMimeTypeOverridesTextLikeExtension() {
        assertFalse(InfinityFilesPreviewPolicy.supportsTextPreview("payload.txt", "application/octet-stream"))
        assertFalse(InfinityFilesPreviewPolicy.supportsTextPreview("payload.json", "image/png"))
    }

    @Test
    fun supportsCommonImageFormats() {
        listOf("photo.png", "photo.jpg", "photo.jpeg", "photo.webp", "photo.bmp")
            .forEach { name ->
                assertTrue("$name should be previewable", InfinityFilesPreviewPolicy.supportsImagePreview(name, null))
            }
    }

    @Test
    fun supportsImageMimeTypesRegardlessOfExtension() {
        assertTrue(InfinityFilesPreviewPolicy.supportsImagePreview("unknown.bin", "image/png"))
        assertTrue(InfinityFilesPreviewPolicy.supportsImagePreview("unknown.bin", "image/jpeg"))
    }

    @Test
    fun rejectsUnsupportedImageMimeTypesAndExtensions() {
        assertFalse(InfinityFilesPreviewPolicy.supportsImagePreview("photo.gif", null))
        assertFalse(InfinityFilesPreviewPolicy.supportsImagePreview("photo.heic", "image/heic"))
        assertFalse(InfinityFilesPreviewPolicy.supportsImagePreview("photo.png", "application/octet-stream"))
    }

    @Test
    fun supportsExtensionFallbackWhenProviderOmitsMimeType() {
        assertTrue(InfinityFilesPreviewPolicy.supportsTextPreview("NOTES.TXT", null))
    }
}
