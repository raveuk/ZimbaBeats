package com.zimbabeats.di

import com.zimbabeats.core.domain.kidzsafe.engine.KidzSafeEngine
import com.zimbabeats.core.domain.kidzsafe.engine.KidzSafeScorer
import com.zimbabeats.core.domain.kidzsafe.rules.KeywordBlocklistRule
import com.zimbabeats.core.domain.kidzsafe.rules.SuspiciousPatternRule
import com.zimbabeats.core.domain.kidzsafe.rules.TrustedChannelRule
import org.koin.dsl.module

/**
 * ZimbaSafe Content Engine DI Module
 *
 * Provides the ZimbaSafe Content Engine and its components for content filtering.
 */
val zimbaSafeContentModule = module {
    // Content Scorer - calculates Guardian Scores
    single { KidzSafeScorer() }

    // Built-in rules (in priority order)
    factory { TrustedChannelRule() }
    factory { KeywordBlocklistRule() }
    factory { SuspiciousPatternRule() }

    // Safe Content Engine - main facade
    single {
        KidzSafeEngine(
            scorer = get(),
            rules = listOf(
                get<TrustedChannelRule>(),
                get<KeywordBlocklistRule>(),
                get<SuspiciousPatternRule>()
            )
        )
    }
}
