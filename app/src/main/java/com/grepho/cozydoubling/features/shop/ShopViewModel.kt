package com.grepho.cozydoubling.features.shop

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// NOTE: mock data for now. When Supabase is wired up, swap loadThemes()
// for a call into SupabaseThemeRepository.fetchThemes() + fetchOwnedThemeIds(),
// mapping ThemeDto -> ThemeItemUiState via ThemeDto.colors.toPalette()
// (see ThemeModels.kt / SupabaseThemeRepository.kt). The ThemePalette shape
// below is already the real, grep-verified role set the whole app consumes,
// so no changes should be needed here beyond swapping the data source.
class ShopViewModel : ViewModel() {

    private val _themes = MutableStateFlow<List<ThemeItemUiState>>(emptyList())
    val themes: StateFlow<List<ThemeItemUiState>> = _themes.asStateFlow()

    private val _userState = MutableStateFlow(UserMonetizationState(isSupporter = false, hasCozyPass = false))
    val userState: StateFlow<UserMonetizationState> = _userState.asStateFlow()

    init {
        loadThemes()
    }

    private fun loadThemes() {
        // TODO: Replace this with Supabase call later
        _themes.value = listOf(
            ThemeItemUiState(
                id = "1",
                name = "Matcha Green",
                palette = ThemePalette(
                    primary = Color(0xFF558B2F),
                    onPrimary = Color(0xFFFFFFFF),
                    primaryContainer = Color(0xFFDCEDC8),
                    onPrimaryContainer = Color(0xFF1B2E0E),
                    secondaryContainer = Color(0xFFE8F5C8),
                    onSecondaryContainer = Color(0xFF33691E),
                    surfaceVariant = Color(0xFFEEF5E4),
                    onSurfaceVariant = Color(0xFF495B3D),
                    onSurface = Color(0xFF1B1B1B),
                    background = Color(0xFFF7FBF1)
                ),
                leafPrice = 1000,
                iapPrice = "$0.99",
                isPremium = false,
                isOwned = true
            ),
            ThemeItemUiState(
                id = "2",
                name = "Midnight Blue",
                palette = ThemePalette(
                    primary = Color(0xFF7986CB),
                    onPrimary = Color(0xFF0D1230),
                    primaryContainer = Color(0xFF283093),
                    onPrimaryContainer = Color(0xFFE8EAF6),
                    secondaryContainer = Color(0xFF3949AB),
                    onSecondaryContainer = Color(0xFFE8EAF6),
                    surfaceVariant = Color(0xFF232A55),
                    onSurfaceVariant = Color(0xFFC5CAE9),
                    onSurface = Color(0xFFE8EAF6),
                    background = Color(0xFF0D1230)
                ),
                leafPrice = 3000,
                iapPrice = "$1.99",
                isPremium = true,
                isOwned = false
            ),
            ThemeItemUiState(
                id = "3",
                name = "Sunset Glow",
                palette = ThemePalette(
                    primary = Color(0xFFE64A19),
                    onPrimary = Color(0xFFFFFFFF),
                    primaryContainer = Color(0xFFFFCC80),
                    onPrimaryContainer = Color(0xFF3E2200),
                    secondaryContainer = Color(0xFFFFE0B2),
                    onSecondaryContainer = Color(0xFF5C3200),
                    surfaceVariant = Color(0xFFFFF0DC),
                    onSurfaceVariant = Color(0xFF7A5230),
                    onSurface = Color(0xFF3E2200),
                    background = Color(0xFFFFF8EE)
                ),
                leafPrice = 3000,
                iapPrice = "$1.99",
                isPremium = true,
                isOwned = false
            )
        )
    }

    // 2. Handle Actions
    fun onBuyWithLeavesClicked(themeId: String) {
        // TODO: deduct leaves, then mark owned. Mocked for now.
        _themes.update { list ->
            list.map { if (it.id == themeId) it.copy(isOwned = true) else it }
        }
    }

    fun onBuyWithCashClicked(themeId: String) {
        // TODO: Logic to trigger Google Play Billing goes here
    }
}