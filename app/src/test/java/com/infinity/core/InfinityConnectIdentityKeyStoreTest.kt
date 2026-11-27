package com.infinity.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InfinityConnectIdentityKeyStoreTest {
    @Test fun acceptsOnlyDocumentedAsciiAliasCharacters() {
        assertTrue(InfinityConnectIdentityKeyStore.isValidAlias("infinity.connect-01_A"))
        assertTrue(InfinityConnectIdentityKeyStore.isValidAlias("A"))
        assertTrue(InfinityConnectIdentityKeyStore.isValidAlias("a".repeat(80)))
    }

    @Test fun rejectsEmptyOverlongAndNonAsciiAliases() {
        assertFalse(InfinityConnectIdentityKeyStore.isValidAlias(""))
        assertFalse(InfinityConnectIdentityKeyStore.isValidAlias("a".repeat(81)))
        assertFalse(InfinityConnectIdentityKeyStore.isValidAlias("identidad-é"))
        assertFalse(InfinityConnectIdentityKeyStore.isValidAlias("身份"))
    }

    @Test fun rejectsWhitespacePathSeparatorsAndControlCharacters() {
        assertFalse(InfinityConnectIdentityKeyStore.isValidAlias("infinity connect"))
        assertFalse(InfinityConnectIdentityKeyStore.isValidAlias("../identity"))
        assertFalse(InfinityConnectIdentityKeyStore.isValidAlias("identity\\nkey"))
        assertFalse(InfinityConnectIdentityKeyStore.isValidAlias("identity/key"))
    }
}
