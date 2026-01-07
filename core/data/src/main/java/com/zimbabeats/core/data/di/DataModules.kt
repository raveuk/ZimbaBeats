package com.zimbabeats.core.data.di

import org.koin.core.module.Module

/**
 * Loads all data layer modules
 * Following MarelikayBeats's pattern of module loading
 */
fun getDataModules(): List<Module> = listOf(
    databaseModule,
    repositoryModule,
    musicModule
)
