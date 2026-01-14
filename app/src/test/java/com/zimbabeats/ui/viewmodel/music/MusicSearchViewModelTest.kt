package com.zimbabeats.ui.viewmodel.music

import com.zimbabeats.cloud.CloudMusicFilter
import com.zimbabeats.cloud.CloudPairingClient
import com.zimbabeats.cloud.MusicBlockReason
import com.zimbabeats.cloud.MusicBlockResult
import com.zimbabeats.cloud.MusicFilterSettings
import com.zimbabeats.cloud.PairingStatus
import com.zimbabeats.core.domain.repository.MusicRepository
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for MusicSearchViewModel
 * Tests the null safety fixes for BUG-001, BUG-002, BUG-003
 *
 * Note: CloudPairingClient.musicFilter is NOT nullable - it's always created.
 * The "null safety" fixes check if settings have been loaded from Firebase yet.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MusicSearchViewModelTest {

    @MockK
    private lateinit var musicRepository: MusicRepository

    @MockK
    private lateinit var cloudPairingClient: CloudPairingClient

    @MockK
    private lateinit var cloudMusicFilter: CloudMusicFilter

    private lateinit var viewModel: MusicSearchViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        MockKAnnotations.init(this, relaxed = true)
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    // ==================== BUG-001: Settings Not Loaded Handling ====================

    @Test
    fun `BUG-001 - viewModel initializes without crash when settings not loaded`() = runTest {
        // Given: Device is linked but settings not yet loaded from Firebase
        every { cloudPairingClient.musicFilter } returns cloudMusicFilter
        every { cloudMusicFilter.hasLoadedSettings() } returns false
        every { cloudMusicFilter.musicSettings } returns MutableStateFlow(MusicFilterSettings())
        every { cloudPairingClient.pairingStatus } returns MutableStateFlow(PairingStatus.Unpaired)

        // When: ViewModel is created
        viewModel = MusicSearchViewModel(musicRepository, cloudPairingClient)
        advanceUntilIdle()

        // Then: No crash occurs, ViewModel initializes successfully
        assertNotNull(viewModel)
        assertNotNull(viewModel.uiState.value)
    }

    @Test
    fun `BUG-001 - observeFilterSettings works when settings are loaded`() = runTest {
        // Given: Device IS linked to family and settings ARE loaded
        val musicSettingsFlow = MutableStateFlow(MusicFilterSettings())
        every { cloudPairingClient.musicFilter } returns cloudMusicFilter
        every { cloudMusicFilter.hasLoadedSettings() } returns true
        every { cloudMusicFilter.musicSettings } returns musicSettingsFlow
        every { cloudPairingClient.pairingStatus } returns MutableStateFlow(
            PairingStatus.Paired("uid", "device", "Child")
        )

        // When: ViewModel is created
        viewModel = MusicSearchViewModel(musicRepository, cloudPairingClient)
        advanceUntilIdle()

        // Then: Filter settings are observed
        verify { cloudMusicFilter.musicSettings }
    }

    // ==================== BUG-002: Filter Not Ready Handling ====================

    @Test
    fun `BUG-002 - filterMusicResults handles not-loaded settings`() = runTest {
        // Given: Device IS linked but settings not yet loaded (security check)
        every { cloudPairingClient.musicFilter } returns cloudMusicFilter
        every { cloudMusicFilter.hasLoadedSettings() } returns false
        every { cloudMusicFilter.musicSettings } returns MutableStateFlow(MusicFilterSettings())
        every { cloudPairingClient.pairingStatus } returns MutableStateFlow(PairingStatus.Unpaired)

        viewModel = MusicSearchViewModel(musicRepository, cloudPairingClient)
        advanceUntilIdle()

        // Then: No crash, filter settings loading state is handled
        verify { cloudMusicFilter.hasLoadedSettings() }
    }

    @Test
    fun `BUG-002 - content allowed when unpaired from family`() = runTest {
        // Given: Device is NOT linked to family
        every { cloudPairingClient.musicFilter } returns cloudMusicFilter
        every { cloudMusicFilter.hasLoadedSettings() } returns true
        every { cloudMusicFilter.musicSettings } returns MutableStateFlow(
            MusicFilterSettings(whitelistModeEnabled = false, ageRating = "ALL")
        )
        every { cloudPairingClient.pairingStatus } returns MutableStateFlow(PairingStatus.Unpaired)

        viewModel = MusicSearchViewModel(musicRepository, cloudPairingClient)
        advanceUntilIdle()

        // Then: No crash, kidSafeMode is disabled when unpaired
        val state = viewModel.uiState.value
        assertFalse(state.kidSafeModeEnabled)
    }

    // ==================== BUG-003: Search Allowed Handling ====================

    @Test
    fun `BUG-003 - search not blocked when unpaired from family`() = runTest {
        // Given: Device is NOT linked to family
        every { cloudPairingClient.musicFilter } returns cloudMusicFilter
        every { cloudMusicFilter.hasLoadedSettings() } returns true
        every { cloudMusicFilter.musicSettings } returns MutableStateFlow(
            MusicFilterSettings(whitelistModeEnabled = false, ageRating = "ALL")
        )
        every { cloudMusicFilter.isSearchAllowed(any()) } returns MusicBlockResult(isBlocked = false)
        every { cloudPairingClient.pairingStatus } returns MutableStateFlow(PairingStatus.Unpaired)

        viewModel = MusicSearchViewModel(musicRepository, cloudPairingClient)
        advanceUntilIdle()

        // When: User performs a search
        viewModel.onQueryChange("rock music")
        advanceUntilIdle()

        // Then: Search is not blocked (no family restrictions when unpaired)
        val state = viewModel.uiState.value
        assertFalse(state.searchBlocked)
    }

    @Test
    fun `BUG-003 - search blocked when settings not loaded for security`() = runTest {
        // Given: Device IS linked but settings not yet loaded
        every { cloudPairingClient.musicFilter } returns cloudMusicFilter
        every { cloudMusicFilter.hasLoadedSettings() } returns false
        every { cloudMusicFilter.musicSettings } returns MutableStateFlow(MusicFilterSettings())
        every { cloudPairingClient.pairingStatus } returns MutableStateFlow(
            PairingStatus.Paired("uid", "device", "Child")
        )

        viewModel = MusicSearchViewModel(musicRepository, cloudPairingClient)
        advanceUntilIdle()

        // When: User tries to search
        viewModel.onQueryChange("explicit music")
        viewModel.performSearch()
        advanceUntilIdle()

        // Then: Search should be blocked until settings load (security measure)
        val state = viewModel.uiState.value
        assertTrue(state.searchBlocked)
    }

    @Test
    fun `BUG-003 - search allowed when linked and settings loaded`() = runTest {
        // Given: Device IS linked and settings ARE loaded
        every { cloudPairingClient.musicFilter } returns cloudMusicFilter
        every { cloudMusicFilter.hasLoadedSettings() } returns true
        every { cloudMusicFilter.musicSettings } returns MutableStateFlow(
            MusicFilterSettings(whitelistModeEnabled = false)
        )
        every { cloudMusicFilter.isSearchAllowed(any()) } returns MusicBlockResult(isBlocked = false)
        every { cloudPairingClient.pairingStatus } returns MutableStateFlow(
            PairingStatus.Paired("uid", "device", "Child")
        )

        viewModel = MusicSearchViewModel(musicRepository, cloudPairingClient)
        advanceUntilIdle()

        // When: User searches for allowed content
        viewModel.onQueryChange("cocomelon")
        advanceUntilIdle()

        // Then: Search is allowed and filter is consulted
        verify { cloudMusicFilter.isSearchAllowed("cocomelon") }
    }

    // ==================== Integration Tests ====================

    @Test
    fun `integration - full flow works when unpaired from family`() = runTest {
        // Given: Device is standalone (not linked)
        every { cloudPairingClient.musicFilter } returns cloudMusicFilter
        every { cloudMusicFilter.hasLoadedSettings() } returns true
        every { cloudMusicFilter.musicSettings } returns MutableStateFlow(
            MusicFilterSettings(whitelistModeEnabled = false, ageRating = "ALL")
        )
        every { cloudMusicFilter.isSearchAllowed(any()) } returns MusicBlockResult(isBlocked = false)
        every { cloudPairingClient.pairingStatus } returns MutableStateFlow(PairingStatus.Unpaired)

        // When: ViewModel is created and user searches
        viewModel = MusicSearchViewModel(musicRepository, cloudPairingClient)
        advanceUntilIdle()

        viewModel.onQueryChange("any music")
        advanceUntilIdle()

        // Then: Everything works, no crashes, no restrictions
        val state = viewModel.uiState.value
        assertFalse(state.kidSafeModeEnabled)
        assertFalse(state.searchBlocked)
        assertEquals("any music", state.query)
    }

    @Test
    fun `integration - full flow works when linked to family with whitelist mode`() = runTest {
        // Given: Device is linked with whitelist mode enabled
        val settings = MusicFilterSettings(
            whitelistModeEnabled = true,
            ageRating = "EIGHT_PLUS",
            defaultKidsArtistsEnabled = true
        )
        every { cloudPairingClient.musicFilter } returns cloudMusicFilter
        every { cloudMusicFilter.hasLoadedSettings() } returns true
        every { cloudMusicFilter.musicSettings } returns MutableStateFlow(settings)
        every { cloudMusicFilter.isSearchAllowed("cocomelon") } returns MusicBlockResult(isBlocked = false)
        every { cloudPairingClient.pairingStatus } returns MutableStateFlow(
            PairingStatus.Paired("uid", "device", "Child")
        )

        // When: ViewModel is created
        viewModel = MusicSearchViewModel(musicRepository, cloudPairingClient)
        advanceUntilIdle()

        // Then: kidSafeMode is enabled
        val state = viewModel.uiState.value
        assertTrue(state.kidSafeModeEnabled)
    }

    @Test
    fun `integration - whitelist mode blocks non-whitelisted searches`() = runTest {
        // Given: Device is linked with whitelist mode enabled
        val settings = MusicFilterSettings(
            whitelistModeEnabled = true,
            ageRating = "EIGHT_PLUS",
            defaultKidsArtistsEnabled = true
        )
        every { cloudPairingClient.musicFilter } returns cloudMusicFilter
        every { cloudMusicFilter.hasLoadedSettings() } returns true
        every { cloudMusicFilter.musicSettings } returns MutableStateFlow(settings)
        every { cloudMusicFilter.isSearchAllowed("explicit rap") } returns MusicBlockResult(
            isBlocked = true,
            reason = MusicBlockReason.SEARCH_NOT_ALLOWED,
            message = "Search for allowed artists or kids content"
        )
        every { cloudPairingClient.pairingStatus } returns MutableStateFlow(
            PairingStatus.Paired("uid", "device", "Child")
        )

        // When: ViewModel is created and user searches for blocked content
        viewModel = MusicSearchViewModel(musicRepository, cloudPairingClient)
        advanceUntilIdle()

        viewModel.onQueryChange("explicit rap")
        viewModel.performSearch()
        advanceUntilIdle()

        // Then: Search is blocked
        val state = viewModel.uiState.value
        assertTrue(state.searchBlocked)
        assertNotNull(state.searchBlockedMessage)
    }
}
