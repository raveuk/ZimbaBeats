package com.zimbabeats.family.ipc

import android.os.Parcel
import android.os.Parcelable

/**
 * Current parental control restriction state.
 */
data class RestrictionState(
    val isEnabled: Boolean,
    val isScreenTimeLimitActive: Boolean,
    val screenTimeUsedMinutes: Int,
    val screenTimeLimitMinutes: Int,
    val screenTimeRemainingMinutes: Int,
    val isBedtimeActive: Boolean,
    val bedtimeStart: String?,
    val bedtimeEnd: String?,
    val isBedtimeCurrentlyBlocking: Boolean,
    val selectedAgeLevel: Int,
    val searchAllowed: Boolean,
    val downloadPinRequired: Boolean
) : Parcelable {

    constructor(parcel: Parcel) : this(
        isEnabled = parcel.readByte() != 0.toByte(),
        isScreenTimeLimitActive = parcel.readByte() != 0.toByte(),
        screenTimeUsedMinutes = parcel.readInt(),
        screenTimeLimitMinutes = parcel.readInt(),
        screenTimeRemainingMinutes = parcel.readInt(),
        isBedtimeActive = parcel.readByte() != 0.toByte(),
        bedtimeStart = parcel.readString(),
        bedtimeEnd = parcel.readString(),
        isBedtimeCurrentlyBlocking = parcel.readByte() != 0.toByte(),
        selectedAgeLevel = parcel.readInt(),
        searchAllowed = parcel.readByte() != 0.toByte(),
        downloadPinRequired = parcel.readByte() != 0.toByte()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeByte(if (isEnabled) 1 else 0)
        parcel.writeByte(if (isScreenTimeLimitActive) 1 else 0)
        parcel.writeInt(screenTimeUsedMinutes)
        parcel.writeInt(screenTimeLimitMinutes)
        parcel.writeInt(screenTimeRemainingMinutes)
        parcel.writeByte(if (isBedtimeActive) 1 else 0)
        parcel.writeString(bedtimeStart)
        parcel.writeString(bedtimeEnd)
        parcel.writeByte(if (isBedtimeCurrentlyBlocking) 1 else 0)
        parcel.writeInt(selectedAgeLevel)
        parcel.writeByte(if (searchAllowed) 1 else 0)
        parcel.writeByte(if (downloadPinRequired) 1 else 0)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<RestrictionState> {
        override fun createFromParcel(parcel: Parcel): RestrictionState = RestrictionState(parcel)
        override fun newArray(size: Int): Array<RestrictionState?> = arrayOfNulls(size)

        // Age level constants
        const val AGE_LEVEL_ALL = 0
        const val AGE_LEVEL_UNDER_5 = 5
        const val AGE_LEVEL_UNDER_8 = 8
        const val AGE_LEVEL_UNDER_10 = 10
        const val AGE_LEVEL_UNDER_12 = 12
        const val AGE_LEVEL_UNDER_13 = 13
        const val AGE_LEVEL_UNDER_14 = 14
        const val AGE_LEVEL_UNDER_16 = 16

        /**
         * Create an unrestricted state (no companion app or controls disabled)
         */
        fun unrestricted() = RestrictionState(
            isEnabled = false,
            isScreenTimeLimitActive = false,
            screenTimeUsedMinutes = 0,
            screenTimeLimitMinutes = 0,
            screenTimeRemainingMinutes = Int.MAX_VALUE,
            isBedtimeActive = false,
            bedtimeStart = null,
            bedtimeEnd = null,
            isBedtimeCurrentlyBlocking = false,
            selectedAgeLevel = AGE_LEVEL_ALL,
            searchAllowed = true,
            downloadPinRequired = false
        )
    }
}
