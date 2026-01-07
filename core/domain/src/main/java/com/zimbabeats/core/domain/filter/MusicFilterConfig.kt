package com.zimbabeats.core.domain.filter

/**
 * Configuration for music content filtering
 * Contains age-specific settings, trusted artists, and keyword lists
 */
object MusicFilterConfig {

    /**
     * Age-specific music filtering configuration
     */
    val ageConfigs: Map<AgeGroup, MusicAgeConfig> = mapOf(
        AgeGroup.UNDER_5 to MusicAgeConfig(
            maxDuration = 300,          // 5 minutes max
            strictMode = true,
            blockExplicit = true
        ),
        AgeGroup.UNDER_10 to MusicAgeConfig(
            maxDuration = 420,          // 7 minutes max
            strictMode = true,
            blockExplicit = true
        ),
        AgeGroup.UNDER_12 to MusicAgeConfig(
            maxDuration = 600,          // 10 minutes max
            strictMode = false,
            blockExplicit = true
        ),
        AgeGroup.UNDER_14 to MusicAgeConfig(
            maxDuration = 900,          // 15 minutes max
            strictMode = false,
            blockExplicit = true
        ),
        AgeGroup.UNDER_16 to MusicAgeConfig(
            maxDuration = 1200,         // 20 minutes max
            strictMode = false,
            blockExplicit = true        // Still block explicit for under 16
        )
    )

    /**
     * Whitelisted artist IDs by age group (YouTube Music artist browseIds)
     */
    val whitelistedArtists: Map<AgeGroup, Set<String>> = mapOf(
        AgeGroup.UNDER_5 to setOf(
            // Kid-specific music artists
            "UCBnZ16ahKA2DZ_T5W0FPUXg", // CoComelon
            "UC4NALVCmcmL5ntpKx19zoJQ", // Pinkfong
            "UC-Gm4EN7nNNR3k67J8ywF4A", // Super Simple Songs
            "UCeirmJxuV9HM9VdG0ykDJhg", // Little Baby Bum
            "UCPPJnlbQSvdXhFOZXWqangg"  // BabyBus
        ),
        AgeGroup.UNDER_10 to setOf(
            "UCBnZ16ahKA2DZ_T5W0FPUXg", // CoComelon
            "UC4NALVCmcmL5ntpKx19zoJQ", // Pinkfong
            "UC-Gm4EN7nNNR3k67J8ywF4A", // Super Simple Songs
            "UCeirmJxuV9HM9VdG0ykDJhg"  // Little Baby Bum
        ),
        AgeGroup.UNDER_12 to setOf(
            // Expand to more general family-friendly artists
        ),
        AgeGroup.UNDER_14 to setOf(),
        AgeGroup.UNDER_16 to setOf()
    )

    /**
     * Trusted artist names (case-insensitive matching)
     */
    val trustedArtistNames: Set<String> = setOf(
        // Nursery rhymes & kids music
        "cocomelon", "coco melon",
        "pinkfong", "baby shark",
        "little baby bum",
        "super simple songs", "super simple",
        "dave and ava",
        "bounce patrol",
        "chu chu tv", "chutv",
        "babybus", "baby bus",
        "little angel",
        "moonbug kids",
        "super jojo",
        "mother goose club",
        "kids songs",
        "nursery rhymes",
        "hey bear sensory",
        "ms rachel",
        "songs for littles",

        // Disney & family
        "disney",
        "pixar",
        "dreamworks",
        "nickelodeon",

        // Classical & instrumental (safe for all ages)
        "mozart",
        "beethoven",
        "bach",
        "classical baby",
        "lullaby",
        "baby einstein",

        // Family-friendly pop (generally safe)
        "kidz bop",
        "the wiggles",
        "raffi",
        "laurie berkner",
        "sesame street"
    )

    /**
     * Blocked keywords by age group
     */
    val blocklistKeywords: Map<AgeGroup, Set<String>> = mapOf(
        AgeGroup.UNDER_5 to setOf(
            // Violence & scary themes
            "kill", "death", "dead", "murder", "blood", "gun", "weapon", "war",
            "scary", "horror", "nightmare", "monster", "demon", "devil", "hell", "satan",
            "violence", "violent", "fight", "fighting",

            // Adult themes
            "sex", "sexy", "sexual", "nude", "naked", "porn", "xxx",
            "drug", "drugs", "cocaine", "weed", "marijuana", "alcohol", "drunk", "beer", "wine",
            "smoking", "cigarette", "vape",

            // Profanity & inappropriate language
            "fuck", "shit", "bitch", "ass", "damn", "crap", "bastard",
            "nigga", "nigger", "hoe", "whore", "slut",

            // Mature themes
            "suicide", "suicidal", "depression", "depressed",
            "abuse", "abusive", "trauma",
            "explicit", "uncensored", "18+", "mature", "adult",

            // Inappropriate relationships
            "breakup", "heartbreak", "cheating", "affair",
            "twerk", "twerking", "stripper", "strip club",

            // Dark themes
            "hate", "hatred", "revenge", "angry", "rage"
        ),
        AgeGroup.UNDER_10 to setOf(
            "kill", "death", "murder", "blood", "gun", "weapon", "war",
            "horror", "demon", "devil", "hell", "satan",
            "sex", "sexy", "sexual", "nude", "naked", "porn", "xxx",
            "drug", "drugs", "cocaine", "weed", "marijuana", "alcohol", "drunk",
            "fuck", "shit", "bitch", "nigga", "nigger", "hoe", "whore", "slut",
            "suicide", "suicidal", "abuse",
            "explicit", "uncensored", "18+", "mature", "adult",
            "twerk", "stripper", "strip club"
        ),
        AgeGroup.UNDER_12 to setOf(
            "murder", "blood", "gun", "weapon",
            "sex", "sexy", "sexual", "nude", "naked", "porn", "xxx",
            "drug", "drugs", "cocaine", "weed", "marijuana",
            "fuck", "shit", "nigga", "nigger", "hoe", "whore", "slut",
            "suicide", "suicidal",
            "explicit", "18+", "adult only",
            "twerk", "stripper"
        ),
        AgeGroup.UNDER_14 to setOf(
            "sex", "sexual", "nude", "naked", "porn", "xxx",
            "drug", "cocaine", "weed",
            "fuck", "nigga", "nigger", "whore", "slut",
            "suicide method", "self harm",
            "explicit", "18+", "adult only"
        ),
        AgeGroup.UNDER_16 to setOf(
            "porn", "xxx", "nude",
            "cocaine", "heroin", "meth",
            "nigger",
            "suicide method", "self harm",
            "18+", "adult only"
        )
    )

    /**
     * Safe music keywords that boost trust score
     */
    val safeMusicKeywords: Set<String> = setOf(
        // Age indicators
        "kids", "kid", "children", "child", "baby", "babies", "toddler",
        "preschool", "kindergarten",

        // Content type
        "nursery", "rhyme", "rhymes", "lullaby", "lullabies",
        "sing along", "singalong", "sing-along",
        "educational", "learning", "learn",
        "alphabet", "abc", "numbers", "counting", "123",
        "colors", "shapes", "animals",

        // Known safe brands
        "disney", "pixar", "nickelodeon",
        "sesame street", "cocomelon", "pinkfong",
        "baby shark", "super simple", "little baby bum",
        "kidz bop", "wiggles",

        // Music types (generally safe)
        "classical", "instrumental", "piano", "acoustic",
        "relaxing", "calm", "peaceful", "sleep", "bedtime",
        "meditation", "nature sounds",

        // Activity indicators
        "playtime", "dance", "fun", "happy", "cheerful",
        "storytime", "adventure",

        // Safety indicators
        "family friendly", "kid friendly", "clean", "radio edit"
    )

    /**
     * Inappropriate music themes (for penalty scoring, not outright blocking)
     */
    val inappropriateMusicThemes: Set<String> = setOf(
        // Relationship themes (inappropriate for young kids)
        "love song", "romance", "romantic", "girlfriend", "boyfriend",
        "kiss", "kissing", "making love",
        "heartbreak", "breakup", "broken heart", "cheating",

        // Party/club themes
        "club", "clubbing", "party anthem", "turn up",
        "drink", "drinking", "shots", "champagne",
        "twerk", "grinding", "booty",

        // Dark/sad themes
        "sad", "cry", "crying", "tears", "pain", "hurt",
        "lonely", "alone", "depressed", "anxiety",
        "dark", "darkness", "nightmare",

        // Aggressive themes
        "fight", "beef", "diss", "hater", "haters",
        "gang", "gangster", "thug", "hood",
        "money", "cash", "rich", "flex", "drip",

        // Suggestive
        "body", "curves", "thick", "bad girl", "bad boy",
        "naughty", "freaky", "wild"
    )

    /**
     * Artist reputation scores (0-100)
     */
    val artistReputation: Map<String, Int> = mapOf(
        // Perfect score - verified kid content
        "UCBnZ16ahKA2DZ_T5W0FPUXg" to 100, // CoComelon
        "UC4NALVCmcmL5ntpKx19zoJQ" to 100, // Pinkfong
        "UC-Gm4EN7nNNR3k67J8ywF4A" to 100, // Super Simple Songs
        "UCeirmJxuV9HM9VdG0ykDJhg" to 100, // Little Baby Bum
        "UCPPJnlbQSvdXhFOZXWqangg" to 95,  // BabyBus

        // High score - family friendly
        // Add more artist IDs as needed
    )
}

/**
 * Music-specific age configuration
 */
data class MusicAgeConfig(
    val maxDuration: Int,           // Max track duration in seconds
    val strictMode: Boolean,        // Require positive kid-friendly indicators
    val blockExplicit: Boolean      // Block tracks marked as explicit
)
