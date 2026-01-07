package com.zimbabeats.core.domain.filter

import com.zimbabeats.core.domain.model.VideoCategory

object FilterConfig {

    val ageConfigs: Map<AgeGroup, AgeConfig> = mapOf(
        AgeGroup.UNDER_5 to AgeConfig(
            maxDuration = 900,  // 15 min (increased from 10)
            strictMode = true,
            allowedCategories = setOf(
                VideoCategory.EDUCATION,
                VideoCategory.ENTERTAINMENT,
                VideoCategory.ANIMATION,
                VideoCategory.MUSIC,
                VideoCategory.KIDS_SONGS,
                VideoCategory.STORIES
            ),
            maxTitleLength = 100
        ),
        AgeGroup.UNDER_8 to AgeConfig(
            maxDuration = 900,  // 15 min
            strictMode = true,
            allowedCategories = setOf(
                VideoCategory.EDUCATION,
                VideoCategory.ENTERTAINMENT,
                VideoCategory.ANIMATION,
                VideoCategory.MUSIC,
                VideoCategory.KIDS_SONGS,
                VideoCategory.STORIES,
                VideoCategory.LEARNING
            ),
            maxTitleLength = 120
        ),
        AgeGroup.UNDER_10 to AgeConfig(
            maxDuration = 1200,  // 20 min
            strictMode = true,
            allowedCategories = setOf(
                VideoCategory.EDUCATION,
                VideoCategory.ENTERTAINMENT,
                VideoCategory.ANIMATION,
                VideoCategory.MUSIC,
                VideoCategory.KIDS_SONGS,
                VideoCategory.STORIES,
                VideoCategory.SCIENCE,
                VideoCategory.ART_CRAFTS,
                VideoCategory.LEARNING
            ),
            maxTitleLength = 150
        ),
        AgeGroup.UNDER_12 to AgeConfig(
            maxDuration = 1800,  // 30 min
            strictMode = false,
            allowedCategories = setOf(
                VideoCategory.EDUCATION,
                VideoCategory.ENTERTAINMENT,
                VideoCategory.ANIMATION,
                VideoCategory.MUSIC,
                VideoCategory.KIDS_SONGS,
                VideoCategory.STORIES,
                VideoCategory.SCIENCE,
                VideoCategory.ART_CRAFTS,
                VideoCategory.LEARNING,
                VideoCategory.GAMES
            ),
            maxTitleLength = 200
        ),
        AgeGroup.UNDER_13 to AgeConfig(
            maxDuration = 2100,  // 35 min
            strictMode = false,
            allowedCategories = setOf(
                VideoCategory.EDUCATION,
                VideoCategory.ENTERTAINMENT,
                VideoCategory.ANIMATION,
                VideoCategory.MUSIC,
                VideoCategory.KIDS_SONGS,
                VideoCategory.STORIES,
                VideoCategory.SCIENCE,
                VideoCategory.ART_CRAFTS,
                VideoCategory.LEARNING,
                VideoCategory.GAMES
            ),
            maxTitleLength = 220
        ),
        AgeGroup.UNDER_14 to AgeConfig(
            maxDuration = 2700,  // 45 min
            strictMode = false,
            allowedCategories = VideoCategory.entries.toSet(),
            maxTitleLength = 250
        ),
        AgeGroup.UNDER_16 to AgeConfig(
            maxDuration = 3600,  // 60 min
            strictMode = false,
            allowedCategories = VideoCategory.entries.toSet(),
            maxTitleLength = 300
        )
    )

    val whitelistedChannels: Map<AgeGroup, Set<String>> = mapOf(
        AgeGroup.UNDER_5 to setOf(
            "UCBnZ16ahKA2DZ_T5W0FPUXg", // CoComelon
            "UC4NALVCmcmL5ntpKx19zoJQ", // Pinkfong
            "UCkQO3QsgTpNTsOw6ujimT5Q", // Blippi
            "UC-Gm4EN7nNNR3k67J8ywF4A", // Super Simple Songs
            "UCeirmJxuV9HM9VdG0ykDJhg", // Little Baby Bum
            "UCPPJnlbQSvdXhFOZXWqangg"  // BabyBus
        ),
        AgeGroup.UNDER_8 to setOf(
            "UCBnZ16ahKA2DZ_T5W0FPUXg", // CoComelon
            "UC4NALVCmcmL5ntpKx19zoJQ", // Pinkfong
            "UCkQO3QsgTpNTsOw6ujimT5Q", // Blippi
            "UC-Gm4EN7nNNR3k67J8ywF4A", // Super Simple Songs
            "UCeirmJxuV9HM9VdG0ykDJhg", // Little Baby Bum
            "UC295-Dw_tDNtZXFeAPAQKEw"  // National Geographic Kids
        ),
        AgeGroup.UNDER_10 to setOf(
            "UCBnZ16ahKA2DZ_T5W0FPUXg",
            "UC4NALVCmcmL5ntpKx19zoJQ",
            "UCX6OQ3DkcsbYNE6H8uQQuVA", // Crash Course
            "UC295-Dw_tDNtZXFeAPAQKEw", // National Geographic Kids
            "UCvGQ8CnL3u7bE5BO8E0ZXzA"  // SciShow Kids
        ),
        AgeGroup.UNDER_12 to setOf(
            "UCX6OQ3DkcsbYNE6H8uQQuVA",
            "UC295-Dw_tDNtZXFeAPAQKEw",
            "UCsooa4yRKGN_zEE8iknghZA", // TED-Ed
            "UC7_YxT-KID8kRbqZo7MyscQ"  // Markiplier (clean gaming)
        ),
        AgeGroup.UNDER_13 to setOf(
            "UCX6OQ3DkcsbYNE6H8uQQuVA", // Crash Course
            "UC295-Dw_tDNtZXFeAPAQKEw", // National Geographic Kids
            "UCsooa4yRKGN_zEE8iknghZA", // TED-Ed
            "UCvGQ8CnL3u7bE5BO8E0ZXzA", // SciShow Kids
            "UC7_YxT-KID8kRbqZo7MyscQ"  // Markiplier (clean gaming)
        ),
        AgeGroup.UNDER_14 to setOf(
            "UCsooa4yRKGN_zEE8iknghZA", // TED-Ed
            "UC7_YxT-KID8kRbqZo7MyscQ", // Markiplier (clean gaming)
            "UCX6OQ3DkcsbYNE6H8uQQuVA"  // Crash Course
        ),
        AgeGroup.UNDER_16 to setOf(
            "UCsooa4yRKGN_zEE8iknghZA", // TED-Ed
            "UCBcRF18a7Qf58cCRy5xuWwQ", // SpaceX
            "UCsXVk37bltHxD1rDPwtNM8Q"  // Kurzgesagt
        )
    )

    val blocklistKeywords: Map<AgeGroup, Set<String>> = mapOf(
        AgeGroup.UNDER_5 to setOf(
            "scary", "horror", "blood", "kill", "death", "gun", "weapon", "fight", "war", "drug",
            "alcohol", "beer", "wine", "smoking", "cigarette", "sex", "nude", "adult", "mature",
            "violence", "murder", "suicide", "abuse", "prank gone wrong", "creepy", "nightmare",
            "demon", "evil", "hell", "satan", "curse", "damn", "crap", "stupid", "idiot", "hate",
            "r-rated", "pg-13", "explicit", "uncensored", "18+", "nsfw", "gone sexual", "clickbait",
            "slender", "fnaf", "jumpscare", "haunted", "ghost", "zombie", "monster", "scream",
            "disturbing", "graphic", "bloody", "gory", "violent", "dark", "twisted", "insane"
        ),
        AgeGroup.UNDER_8 to setOf(
            "horror", "blood", "kill", "death", "gun", "weapon", "fight", "war", "drug",
            "alcohol", "smoking", "sex", "nude", "adult", "mature", "violence", "murder",
            "suicide", "abuse", "creepy", "nightmare", "demon", "evil", "satan",
            "r-rated", "explicit", "uncensored", "18+", "nsfw", "gone sexual",
            "slender", "fnaf", "jumpscare", "haunted", "ghost", "zombie", "monster",
            "disturbing", "graphic", "bloody", "gory", "violent", "dark"
        ),
        AgeGroup.UNDER_10 to setOf(
            "horror", "blood", "kill", "death", "gun", "weapon", "drug", "alcohol", "smoking",
            "sex", "nude", "adult", "mature", "violence", "murder", "suicide", "abuse",
            "demon", "satan", "r-rated", "explicit", "uncensored", "18+", "nsfw", "gone sexual",
            "slender", "jumpscare", "disturbing", "graphic", "bloody", "gory",
            "how to watch adult", "bypass age", "age restriction", "restricted content"
        ),
        AgeGroup.UNDER_12 to setOf(
            "horror", "blood", "gore", "drug", "alcohol", "sex", "nude", "adult", "mature",
            "murder", "suicide", "r-rated", "explicit", "18+", "nsfw", "gone sexual",
            "disturbing", "graphic content",
            "how to watch adult", "bypass age", "age restriction", "restricted content"
        ),
        AgeGroup.UNDER_13 to setOf(
            "horror", "gore", "drug", "sex", "nude", "adult", "mature",
            "suicide", "r-rated", "explicit", "18+", "nsfw", "gone sexual",
            "graphic content",
            "how to watch adult", "bypass age", "age restriction", "restricted content"
        ),
        AgeGroup.UNDER_14 to setOf(
            "sex", "nude", "porn", "adult", "adult only", "explicit content", "explicit",
            "18+", "nsfw", "xxx", "mature", "r-rated", "x-rated",
            "gore", "torture", "drug use", "suicide method", "self harm",
            "how to watch adult", "bypass age", "age restriction", "restricted content"
        ),
        AgeGroup.UNDER_16 to setOf(
            "sex", "nude", "porn", "adult only", "explicit content",
            "18+", "nsfw", "xxx", "x-rated",
            "torture", "drug use", "suicide method", "self harm"
        )
    )

    val safeKeywords: Set<String> = setOf(
        // Age indicators
        "kids", "kid", "children", "child", "toddler", "baby", "babies",
        "preschool", "kindergarten", "elementary",
        // Content type
        "nursery", "rhymes", "lullaby", "lullabies",
        "cartoon", "cartoons", "animation", "animated",
        "educational", "learning", "learn", "teach",
        "alphabet", "abc", "numbers", "counting", "123",
        "colors", "shapes", "phonics",
        "sing along", "singalong", "songs for kids",
        // Known brands
        "disney", "pixar", "nickelodeon", "nick jr", "pbs kids",
        "sesame street", "cocomelon", "blippi", "peppa pig", "paw patrol",
        "baby shark", "super simple", "little baby bum",
        "bluey", "dora", "bubble guppies",
        "numberblocks", "alphablocks", "hey bear",
        "babybus", "moonbug", "little angel",
        // Activity indicators
        "playtime", "storytime", "bedtime story", "read aloud",
        "crafts", "diy for kids", "art for kids", "science for kids",
        "fun facts", "animals",
        // Safety indicators
        "family friendly", "kid friendly", "child friendly", "safe for kids"
    )

    // Suspicious patterns - often associated with inappropriate "kid" content (Elsagate-style)
    val suspiciousKeywords: Set<String> = setOf(
        // Elsagate-style patterns
        "surprise", "surprises", "surprise egg", "surprise toys",
        "wrong heads", "wrong head", "bad baby", "bad babies",
        "learn colors with", "learning colors with",
        "finger family", "daddy finger",
        "johny johny", "johnny johnny",
        "crying baby", "baby crying", "babies crying",
        "injection", "needle", "doctor baby", "baby doctor",
        "pregnant", "pregnancy", "baby born",
        "poop", "pooping", "potty", "toilet humor",
        "fart", "farting", "burp", "burping",
        "spider", "spiders", "snake", "snakes",
        "joker", "elsa", "spiderman", "hulk", "frozen",  // Character mashups often problematic
        "real life", "in real life", "irl",
        "challenge", "challenges",
        "mukbang", "eating show", "asmr eating",
        "slime", "satisfying",
        "prank", "pranks", "pranking",
        "scary", "creepy", "monster",
        "spaghetti", "noodle", "food face", "messy",
        "weird", "strange", "odd", "creepy",
        "compilation", "try not to laugh"
    )

    // Strong kid-safe indicators (trusted content)
    val strongSafeIndicators: Set<String> = setOf(
        "cocomelon", "pinkfong", "super simple", "little baby bum",
        "sesame street", "pbs kids", "nick jr", "disney junior",
        "numberblocks", "alphablocks", "bluey", "peppa pig",
        "paw patrol", "blippi", "ms rachel", "hey bear sensory",
        "nursery rhymes", "abc song", "alphabet song",
        "phonics", "counting", "shapes and colors"
    )

    val trustedChannelNames: Set<String> = setOf(
        // Major kid content creators
        "cocomelon", "coco melon",
        "pinkfong", "baby shark",
        "little baby bum",
        "super simple songs", "super simple",
        "blippi",
        "ms rachel", "songs for littles",
        "dave and ava",
        "bounce patrol",
        "chu chu tv", "chutv",
        // Educational
        "sesame street",
        "pbs kids",
        "national geographic kids", "nat geo kids",
        "ted-ed", "teded",
        "crash course kids",
        "scishow kids",
        "numberblocks", "alphablocks",
        // Kids entertainment
        "peppa pig official",
        "paw patrol official",
        "bluey official",
        "disney junior",
        "nick jr",
        "cartoon network",
        "dreamworks tv",
        // Sensory/baby content
        "hey bear sensory",
        "babybus", "baby bus",
        "little angel",
        "moonbug kids",
        "super jojo",
        "mother goose club",
        "little treehouse"
    )

    val channelReputation: Map<String, Int> = mapOf(
        "UCBnZ16ahKA2DZ_T5W0FPUXg" to 100, // CoComelon
        "UC4NALVCmcmL5ntpKx19zoJQ" to 100, // Pinkfong
        "UCkQO3QsgTpNTsOw6ujimT5Q" to 95,  // Blippi
        "UC-Gm4EN7nNNR3k67J8ywF4A" to 95,  // Super Simple Songs
        "UCeirmJxuV9HM9VdG0ykDJhg" to 95,  // Little Baby Bum
        "UCPPJnlbQSvdXhFOZXWqangg" to 90,  // BabyBus
        "UCX6OQ3DkcsbYNE6H8uQQuVA" to 90,  // Crash Course
        "UC295-Dw_tDNtZXFeAPAQKEw" to 85,  // National Geographic Kids
        "UCvGQ8CnL3u7bE5BO8E0ZXzA" to 85,  // SciShow Kids
        "UCsooa4yRKGN_zEE8iknghZA" to 85,  // TED-Ed
        "UCsXVk37bltHxD1rDPwtNM8Q" to 80   // Kurzgesagt
    )
}
