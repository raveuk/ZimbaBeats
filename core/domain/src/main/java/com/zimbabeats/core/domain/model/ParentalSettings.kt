package com.zimbabeats.core.domain.model

data class ParentalSettings(
    val id: Long = 1,
    val isEnabled: Boolean = false,
    val pin: String? = null,  // PIN hash
    val selectedAgeLevel: AgeRating = AgeRating.ALL,  // Selected age level filter
    val maxScreenTimeMinutes: Int = 60,
    val blockedChannels: List<String> = emptyList(),
    val blockedKeywords: List<String> = emptyList(),
    val requirePinForSettings: Boolean = true,
    val requirePinForDownloads: Boolean = false,
    val allowSearch: Boolean = true,
    val bedtimeStart: String? = null,  // HH:mm format
    val bedtimeEnd: String? = null,    // HH:mm format
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        // Keywords blocked for all kids (inappropriate for any child)
        private val BLOCKED_FOR_ALL = listOf(
            // Explicit/Adult content
            "explicit", "nsfw", "18+", "adult only", "xxx", "porn", "porno",
            "sex", "sexy", "sexual", "erotic", "erotica", "sensual",
            "kinky", "kink", "fetish", "bdsm", "bondage", "dominatrix",
            "nude", "naked", "nudity", "topless", "strip", "stripper",
            "onlyfans", "only fans", "camgirl", "cam girl", "webcam girl",
            "milf", "gilf", "dilf", "stepmom", "stepsis", "stepbro",
            "hentai", "rule34", "r34", "lewd", "smut",
            "orgasm", "moan", "climax", "foreplay",
            "hookup", "hook up", "one night stand", "booty call",
            "twerk", "twerking", "lap dance", "pole dance",

            // Drug content
            "drugs", "cocaine", "heroin", "meth", "fentanyl", "overdose",

            // Violence/Gore
            "gore", "death", "murder", "killing", "massacre",
            "horror", "scary", "creepy", "nightmare", "terrifying",
            "violence", "violent", "brutal", "blood", "bloody", "gory",

            // Profanity indicators
            "curse", "cursing", "swear", "profanity",

            // Substance abuse
            "alcohol", "drunk", "beer", "wine", "vodka", "whiskey",
            "smoking", "cigarette", "vape", "weed", "marijuana", "cannabis",

            // Adult industry terms
            "vevo", "brazzers", "pornhub", "xvideos", "xhamster",
            "playboy", "penthouse", "hustler"
        )

        // Additional explicit search terms to block
        private val BLOCKED_SEARCH_TERMS = listOf(
            // Adult performer name patterns
            "turned kinky", "gone wild", "gets naughty", "gets wild",
            "hot wife", "hotwife", "cheating wife", "unfaithful",
            "seduction", "seduce", "seducing", "seduced",
            "affair", "mistress", "escort",
            "massage parlor", "happy ending",
            "lingerie", "bikini try", "try on haul",
            "asmr girlfriend", "asmr boyfriend", "roleplay asmr",
            "joi", "pov girlfriend", "pov boyfriend",
            "thick", "thicc", "curvy model", "glamour model",
            "sugar daddy", "sugar baby", "sugar mommy"
        )

        // Additional keywords blocked for kids under 10
        private val BLOCKED_UNDER_10 = listOf(
            "boyfriend", "girlfriend", "dating", "romance",
            "breakup", "heartbreak", "love song",
            "fight club", "punch", "slap",
            "gun", "shooting", "weapon",
            "ghost story", "haunted", "demon", "devil",
            "prank gone wrong", "dangerous challenge"
        )

        // Additional keywords blocked for kids under 5
        private val BLOCKED_UNDER_5 = listOf(
            "teenager", "high school drama", "college party",
            "crush", "flirt",
            "zombie", "vampire",
            "war movie", "battle royale"
        )

        // Known adult/pop artists to block for young kids
        private val BLOCKED_ARTISTS = listOf(
            "sabrina carpenter", "taylor swift", "ariana grande", "dua lipa",
            "billie eilish", "olivia rodrigo", "doja cat", "cardi b",
            "nicki minaj", "megan thee stallion", "drake", "kanye",
            "eminem", "post malone", "the weeknd", "bad bunny",
            "justin bieber", "selena gomez", "miley cyrus", "rihanna",
            "beyonce", "lady gaga", "katy perry",
            "travis scott", "juice wrld", "xxxtentacion", "lil uzi",
            "playboi carti", "future", "young thug", "gunna",
            "ice spice", "central cee", "stormzy"
        )

        // Kid-friendly indicators - if content contains ANY of these, it's likely safe
        // This is used for ALLOWING content (hybrid approach)
        private val KID_FRIENDLY_INDICATORS = listOf(
            // Age indicators
            "kids", "kid", "children", "child", "toddler", "toddlers",
            "baby", "babies", "infant", "preschool", "pre-school",
            "kindergarten", "elementary", "young learners",

            // Content type indicators
            "nursery", "rhymes", "rhyme", "lullaby", "lullabies",
            "cartoon", "cartoons", "animation", "animated",
            "educational", "learning", "learn", "teach", "teaching",
            "alphabet", "abc", "abcs", "numbers", "counting", "123",
            "colors", "colours", "shapes", "phonics", "reading",
            "sing along", "singalong", "sing-along", "songs for kids",

            // Known kid brands/shows (partial matching works)
            "cocomelon", "coco melon", "pinkfong", "baby shark",
            "little baby bum", "super simple", "bounce patrol",
            "dave and ava", "chu chu", "chutv",
            "sesame street", "elmo", "big bird", "cookie monster",
            "peppa pig", "peppa", "paw patrol", "chase", "marshall",
            "bluey", "bingo", "bandit", "chilli",
            "disney junior", "mickey mouse", "minnie mouse", "doc mcstuffins",
            "nick jr", "dora", "bubble guppies", "blaze",
            "blippi", "ms rachel", "ms. rachel", "rachel for littles",
            "numberblocks", "alphablocks", "hey bear", "sensory",
            "super jojo", "moonbug", "little angel", "babybus", "baby bus",
            "mother goose", "little treehouse",

            // Activity indicators
            "playtime", "play time", "storytime", "story time",
            "bedtime story", "bedtime stories", "read aloud",
            "crafts", "craft", "diy for kids", "art for kids",
            "science for kids", "experiment", "fun facts",

            // Safety indicators
            "family friendly", "kid friendly", "child friendly",
            "safe for kids", "kid safe", "appropriate",
            "parental approved", "clean"
        )

        // Trusted channels - content from these is always allowed
        private val TRUSTED_CHANNELS = listOf(
            "cocomelon", "pinkfong", "little baby bum",
            "super simple songs", "bounce patrol",
            "sesame street", "peppa pig official", "paw patrol official",
            "bluey official", "disney junior", "nick jr",
            "blippi", "ms rachel", "songs for littles",
            "numberblocks", "alphablocks", "hey bear sensory",
            "babybus", "moonbug kids", "little angel"
        )
    }

    fun isWithinBedtime(currentTime: String): Boolean {
        if (bedtimeStart == null || bedtimeEnd == null) return false
        return currentTime >= bedtimeStart && currentTime <= bedtimeEnd
    }

    fun isVideoAllowed(video: Video): Boolean {
        if (!isEnabled) return true
        if (selectedAgeLevel == AgeRating.ALL) return true

        val titleLower = video.title.lowercase()
        val channelLower = video.channelName.lowercase()
        val descLower = (video.description ?: "").lowercase()
        val content = "$titleLower $channelLower $descLower"

        // Check user's custom blocked keywords first
        if (blockedKeywords.any { keyword -> content.contains(keyword.lowercase()) }) {
            return false
        }

        // Check if channel is blocked by user
        if (blockedChannels.any { channel -> channelLower.contains(channel.lowercase()) }) {
            return false
        }

        // Always block explicit content for all age levels
        if (BLOCKED_FOR_ALL.any { keyword -> content.contains(keyword) }) {
            return false
        }

        // Block explicit content patterns
        if (BLOCKED_SEARCH_TERMS.any { term -> content.contains(term) }) {
            return false
        }

        // Check if from a trusted channel (always allowed if not blocked above)
        val isFromTrustedChannel = TRUSTED_CHANNELS.any { channel ->
            channelLower.contains(channel)
        }
        if (isFromTrustedChannel) {
            return true
        }

        // Apply age-specific filters using HYBRID approach
        return when (selectedAgeLevel) {
            AgeRating.FIVE_PLUS -> {
                // Block known adult/pop artists for young kids
                if (BLOCKED_ARTISTS.any { artist -> content.contains(artist) }) {
                    return false
                }
                // Block age-inappropriate content
                if (BLOCKED_UNDER_5.any { keyword -> content.contains(keyword) }) return false
                if (BLOCKED_UNDER_10.any { keyword -> content.contains(keyword) }) return false

                // HYBRID: Allow if content has ANY kid-friendly indicator
                KID_FRIENDLY_INDICATORS.any { indicator -> content.contains(indicator) }
            }
            AgeRating.EIGHT_PLUS -> {
                // Block known adult/pop artists for young kids
                if (BLOCKED_ARTISTS.any { artist -> content.contains(artist) }) {
                    return false
                }
                // Block content not suitable for under 8
                if (BLOCKED_UNDER_10.any { keyword -> content.contains(keyword) }) return false

                // HYBRID: Allow if content has ANY kid-friendly indicator
                KID_FRIENDLY_INDICATORS.any { indicator -> content.contains(indicator) }
            }
            AgeRating.TEN_PLUS -> {
                // Block known adult/pop artists
                if (BLOCKED_ARTISTS.any { artist -> content.contains(artist) }) {
                    return false
                }
                // Block content not suitable for under 10
                if (BLOCKED_UNDER_10.any { keyword -> content.contains(keyword) }) return false

                // HYBRID: Allow if content has ANY kid-friendly indicator
                KID_FRIENDLY_INDICATORS.any { indicator -> content.contains(indicator) }
            }
            AgeRating.TWELVE_PLUS, AgeRating.THIRTEEN_PLUS, AgeRating.FOURTEEN_PLUS, AgeRating.SIXTEEN_PLUS -> {
                // Less strict - block explicit (done above) and artists
                if (BLOCKED_ARTISTS.any { artist -> content.contains(artist) }) {
                    return false
                }
                true
            }
            AgeRating.ALL -> {
                true
            }
        }
    }

    /**
     * Check if a search query should be blocked
     */
    fun isSearchAllowed(query: String): Boolean {
        if (!isEnabled) return true
        if (selectedAgeLevel == AgeRating.ALL) return true

        val queryLower = query.lowercase()

        // Block searches for explicit content
        if (BLOCKED_FOR_ALL.any { keyword -> queryLower.contains(keyword) }) {
            return false
        }

        // Block explicit search patterns
        if (BLOCKED_SEARCH_TERMS.any { term -> queryLower.contains(term) }) {
            return false
        }

        // Block known adult artists/performers for kids
        if (selectedAgeLevel in listOf(AgeRating.FIVE_PLUS, AgeRating.TEN_PLUS)) {
            if (BLOCKED_ARTISTS.any { artist -> queryLower.contains(artist) }) {
                return false
            }
        }

        // Apply age-specific search restrictions
        when (selectedAgeLevel) {
            AgeRating.FIVE_PLUS -> {
                if (BLOCKED_UNDER_5.any { keyword -> queryLower.contains(keyword) }) return false
                if (BLOCKED_UNDER_10.any { keyword -> queryLower.contains(keyword) }) return false
            }
            AgeRating.TEN_PLUS -> {
                if (BLOCKED_UNDER_10.any { keyword -> queryLower.contains(keyword) }) return false
            }
            else -> {}
        }

        return true
    }
}

data class ParentalProfile(
    val id: Long = 0,
    val name: String,
    val pinHash: String,
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
