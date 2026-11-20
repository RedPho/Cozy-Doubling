package com.grepho.cozydoubling.data.fake

import androidx.compose.ui.graphics.Color
import com.grepho.cozydoubling.core.economy.EconomyRepository
import com.grepho.cozydoubling.core.economy.ThemeState
import com.grepho.cozydoubling.core.profile.ProfileRepository
import com.grepho.cozydoubling.core.theming.ThemePalette
import com.grepho.cozydoubling.features.shop.ShopItemUiState
import com.grepho.cozydoubling.ui.theme.CozyPalettes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FakeEconomyRepository(
    private val profileRepository: ProfileRepository
) : EconomyRepository {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    companion object {
        val PeachBlossomPalette = ThemePalette(
            primary = Color(0xFFC0583E),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFFFDBD1),
            onPrimaryContainer = Color(0xFF3B0900),
            secondary = Color(0xFF77574E),
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFFFDBD1),
            onSecondaryContainer = Color(0xFF2C150F),
            tertiary = Color(0xFF6C5D2F),
            onTertiary = Color(0xFFFFFFFF),
            tertiaryContainer = Color(0xFFF6E1A6),
            onTertiaryContainer = Color(0xFF231B00),
            background = Color(0xFFFFF8F6),
            onBackground = Color(0xFF231917),
            surface = Color(0xFFFFF8F6),
            onSurface = Color(0xFF231917),
            surfaceVariant = Color(0xFFF5DED8),
            onSurfaceVariant = Color(0xFF53433F)
        )

        val MidnightVelvetPalette = ThemePalette(
            primary = Color(0xFFBAC3FF),
            onPrimary = Color(0xFF08218A),
            primaryContainer = Color(0xFF293CA0),
            onPrimaryContainer = Color(0xFFDEE0FF),
            secondary = Color(0xFFC5C4DD),
            onSecondary = Color(0xFF2E2F42),
            secondaryContainer = Color(0xFF454559),
            onSecondaryContainer = Color(0xFFE2E0F9),
            tertiary = Color(0xFFE6BAD7),
            onTertiary = Color(0xFF45263E),
            tertiaryContainer = Color(0xFF5D3C55),
            onTertiaryContainer = Color(0xFFFFD7F1),
            background = Color(0xFF131318),
            onBackground = Color(0xFFE4E1E9),
            surface = Color(0xFF1B1B22),
            onSurface = Color(0xFFE4E1E9),
            surfaceVariant = Color(0xFF46464F),
            onSurfaceVariant = Color(0xFFC7C5D0)
        )
    }

    private val _ownedThemeIds = MutableStateFlow<Set<String>>(setOf("system_default"))

    private val availableThemes = listOf(
        ShopItemUiState.Theme(
            id = "system_default",
            name = "System Default",
            palette = CozyPalettes.SystemDefault,
            leafPrice = 0,
            isPremium = false,
            isOwned = true,
            isEquipped = true
        ),
        ShopItemUiState.Theme(
            id = "theme_peach_blossom",
            name = "Peach Blossom",
            palette = PeachBlossomPalette,
            leafPrice = 100,
            isPremium = false,
            isOwned = false,
            isEquipped = false
        ),
        ShopItemUiState.Theme(
            id = "theme_midnight_velvet",
            name = "Midnight Velvet",
            palette = MidnightVelvetPalette,
            leafPrice = 0,
            isPremium = true,
            isOwned = false,
            isEquipped = false
        )
    )

    private val availablePasses = listOf(
        ShopItemUiState.Pass(
            id = "pass_monthly",
            name = "Monthly Supporter",
            isPremium = true,
            isOwned = false,
            iapId = "pass_monthly",
            priceString = "$4.99"
        ),
        ShopItemUiState.Pass(
            id = "pass_yearly",
            name = "Yearly Supporter",
            isPremium = true,
            isOwned = false,
            iapId = "pass_yearly",
            priceString = "$39.99"
        ),
        ShopItemUiState.Pass(
            id = "pass_lifetime",
            name = "Lifetime Supporter",
            isPremium = true,
            isOwned = false,
            iapId = "pass_lifetime",
            priceString = "$79.99"
        )
    )

    private fun computeShopItems(): List<ShopItemUiState> {
        val ownedIds = _ownedThemeIds.value
        val profile = profileRepository.profile.value
        val isSupporter = profile?.isSupporter ?: false
        val equippedId = profile?.equippedThemeId

        val mappedThemes = availableThemes.map { theme ->
            val isOwned = ownedIds.contains(theme.id) || (theme.isPremium && isSupporter)
            val isEquipped = if (theme.id == "system_default") {
                equippedId == null || equippedId == "system_default"
            } else {
                equippedId == theme.id
            }
            theme.copy(isOwned = isOwned, isEquipped = isEquipped)
        }

        val mappedPasses = availablePasses.map { pass ->
            pass.copy(isOwned = isSupporter)
        }

        return mappedPasses + mappedThemes
    }

    private fun computeThemeState(): ThemeState {
        val equippedId = profileRepository.profile.value?.equippedThemeId
        return if (equippedId == null || equippedId == "system_default") {
            ThemeState.Default
        } else {
            val foundTheme = availableThemes.find { it.id == equippedId }
            if (foundTheme != null) ThemeState.Custom(foundTheme.palette) else ThemeState.Default
        }
    }

    private val _shopItems = MutableStateFlow(computeShopItems())
    override val shopItems: StateFlow<List<ShopItemUiState>> = _shopItems.asStateFlow()

    private val _themeState = MutableStateFlow(computeThemeState())
    override val themeState: StateFlow<ThemeState> = _themeState.asStateFlow()

    init {
        scope.launch {
            profileRepository.profile.collect { profile ->
                if (profile == null) {
                    _ownedThemeIds.value = setOf("system_default")
                }
                syncState()
            }
        }
    }

    private fun syncState() {
        _shopItems.value = computeShopItems()
        _themeState.value = computeThemeState()
    }

    override suspend fun equipTheme(themeId: String) {
        profileRepository.setEquippedTheme(themeId)
        syncState()
    }

    override suspend fun unequipTheme() {
        profileRepository.setEquippedTheme(null)
        syncState()
    }

    override suspend fun purchaseWithLeaves(itemId: String) {
        val theme = availableThemes.find { it.id == itemId } ?: return
        val currentProfile = profileRepository.profile.value ?: return
        if (currentProfile.leaves >= theme.leafPrice) {
            profileRepository.updateLeaves(-theme.leafPrice.toLong())
            _ownedThemeIds.value = _ownedThemeIds.value + itemId
            syncState()
        }
    }

    override suspend fun fetchShopItems(): List<ShopItemUiState> {
        syncState()
        return shopItems.value
    }
}
