package com.zimbabeats.core.domain.model

data class BlockedContent(
    val id: Long = 0,
    val contentId: String,
    val contentType: BlockedContentType,
    val reason: String?,
    val blockedAt: Long,
    val blockedBy: Long?  // Profile ID
)

enum class BlockedContentType {
    VIDEO,
    CHANNEL
}
