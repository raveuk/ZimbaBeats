package com.zimbabeats.core.data.mapper

import com.zimbabeats.core.data.local.entity.BlockedContentEntity
import com.zimbabeats.core.data.local.entity.ParentalProfileEntity
import com.zimbabeats.core.data.local.entity.ParentalSettingsEntity
import com.zimbabeats.core.domain.model.AgeRating
import com.zimbabeats.core.domain.model.BlockedContent
import com.zimbabeats.core.domain.model.BlockedContentType
import com.zimbabeats.core.domain.model.ParentalProfile
import com.zimbabeats.core.domain.model.ParentalSettings
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

fun ParentalSettingsEntity.toDomain(): ParentalSettings = ParentalSettings(
    id = id,
    isEnabled = isEnabled,
    pin = pin,
    selectedAgeLevel = parseAgeRating(selectedAgeLevel),
    maxScreenTimeMinutes = maxScreenTimeMinutes,
    blockedChannels = parseStringList(blockedChannels),
    blockedKeywords = parseStringList(blockedKeywords),
    requirePinForSettings = requirePinForSettings,
    requirePinForDownloads = requirePinForDownloads,
    allowSearch = allowSearch,
    bedtimeStart = bedtimeStart,
    bedtimeEnd = bedtimeEnd,
    updatedAt = updatedAt
)

fun ParentalSettings.toEntity(): ParentalSettingsEntity = ParentalSettingsEntity(
    id = id,
    isEnabled = isEnabled,
    pin = pin,
    selectedAgeLevel = selectedAgeLevel.name,
    maxScreenTimeMinutes = maxScreenTimeMinutes,
    blockedChannels = formatStringList(blockedChannels),
    blockedKeywords = formatStringList(blockedKeywords),
    requirePinForSettings = requirePinForSettings,
    requirePinForDownloads = requirePinForDownloads,
    allowSearch = allowSearch,
    bedtimeStart = bedtimeStart,
    bedtimeEnd = bedtimeEnd,
    updatedAt = System.currentTimeMillis() // Always use current timestamp when saving
)

private fun parseAgeRating(value: String): AgeRating {
    return try {
        AgeRating.valueOf(value)
    } catch (e: Exception) {
        AgeRating.ALL
    }
}

fun ParentalProfileEntity.toDomain(): ParentalProfile = ParentalProfile(
    id = id,
    name = name,
    pinHash = pin,
    isDefault = isDefault,
    createdAt = createdAt
)

fun ParentalProfile.toEntity(): ParentalProfileEntity = ParentalProfileEntity(
    id = id,
    name = name,
    pin = pinHash,
    isDefault = isDefault,
    createdAt = createdAt
)

fun BlockedContentEntity.toDomain(): BlockedContent = BlockedContent(
    id = id,
    contentId = contentId,
    contentType = contentType.toBlockedContentType(),
    reason = reason,
    blockedAt = blockedAt,
    blockedBy = blockedBy
)

fun BlockedContent.toEntity(): BlockedContentEntity = BlockedContentEntity(
    id = id,
    contentId = contentId,
    contentType = contentType.name,
    reason = reason,
    blockedAt = blockedAt,
    blockedBy = blockedBy
)

fun String.toBlockedContentType(): BlockedContentType = try {
    BlockedContentType.valueOf(this)
} catch (e: IllegalArgumentException) {
    BlockedContentType.VIDEO
}

private fun parseStringList(value: String?): List<String> =
    value?.split(",")?.filter { it.isNotBlank() } ?: emptyList()

private fun formatStringList(list: List<String>): String =
    list.joinToString(",")

private fun parseAgeRatingsList(value: String?): List<AgeRating> =
    parseStringList(value).map { it.toAgeRating() }

private fun formatAgeRatingsList(list: List<AgeRating>): String =
    list.joinToString(",") { it.toEntityString() }
