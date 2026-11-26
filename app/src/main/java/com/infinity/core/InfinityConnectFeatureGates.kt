package com.infinity.core

/**
 * Remote capability kill-switches.
 * Keep false until the Samsung tablet / Windows PC interoperability test is completed
 * with an installable build and the corresponding security checklist.
 */
internal object InfinityConnectFeatureGates {
    const val REMOTE_DESKTOP_VALIDATED = false
    const val FILE_TRANSFER_VALIDATED = false
}
