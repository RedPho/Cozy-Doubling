package com.grepho.cozydoubling.data.remote

import com.grepho.cozydoubling.core.economy.EconomyRepository
import com.grepho.cozydoubling.core.economy.ThemeState
import com.grepho.cozydoubling.core.profile.ProfileRepository
import com.grepho.cozydoubling.features.shop.ShopItemUiState
import com.grepho.cozydoubling.ui.theme.CozyPalettes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString

class RemoteEconomyRepository(
    private val apiClient: ApiClient,
    private val profileRepository: ProfileRepository
) : EconomyRepository {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val defaultShopItems = listOf(
        ShopItemUiState.Theme(
            id = "system_default",
            name = "System Default",
            palette = CozyPalettes.SystemDefault,
            leafPrice = 0,
            isPremium = false,
            isOwned = true,
            isEquipped = true
        ),
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

    private val _shopItems = MutableStateFlow<List<ShopItemUiState>>(defaultShopItems)
    override val shopItems: StateFlow<List<ShopItemUiState>> = _shopItems.asStateFlow()

    private val _themeState = MutableStateFlow<ThemeState>(ThemeState.Default)
    override val themeState: StateFlow<ThemeState> = _themeState.asStateFlow()

    init {
        scope.launch {
            profileRepository.profile.collect {
                fetchShopItems()
                updateThemeState()
            }
        }
    }

    private fun updateThemeState() {
        val equippedId = profileRepository.profile.value?.equippedThemeId
        if (equippedId == null || equippedId == "system_default") {
            _themeState.value = ThemeState.Default
        } else {
            val matchingTheme = _shopItems.value
                .filterIsInstance<ShopItemUiState.Theme>()
                .find { it.id == equippedId }
            if (matchingTheme != null) {
                _themeState.value = ThemeState.Custom(matchingTheme.palette)
            } else {
                _themeState.value = ThemeState.Default
            }
        }
    }

    override suspend fun equipTheme(themeId: String) {
        profileRepository.setEquippedTheme(themeId)
        fetchShopItems()
    }

    override suspend fun unequipTheme() {
        profileRepository.setEquippedTheme(null)
        fetchShopItems()
    }

    override suspend fun purchaseWithLeaves(itemId: String) {
        val body = apiClient.json.encodeToString(BuyItemRequestDto(itemId = itemId))
        val result = apiClient.post("/api/shop/buy", body) { }
        if (result.isSuccess) {
            profileRepository.refreshProfile()
            fetchShopItems()
        }
    }

    override suspend fun fetchShopItems(): List<ShopItemUiState> {
        val result = apiClient.get("/api/shop/items") { body ->
            apiClient.json.decodeFromString<List<ShopItemDto>>(body)
        }

        return result.fold(
            onSuccess = { dtoList ->
                val mapped = dtoList.map { dto ->
                    if (dto.categoryId == "pass") {
                        ShopItemUiState.Pass(
                            id = dto.id,
                            name = dto.name,
                            isPremium = dto.isPremium,
                            isOwned = dto.isOwned,
                            iapId = dto.iapId ?: dto.id,
                            priceString = when (dto.id) {
                                "pass_monthly" -> "$4.99"
                                "pass_yearly" -> "$39.99"
                                "pass_lifetime" -> "$79.99"
                                else -> "$4.99"
                            }
                        )
                    } else {
                        ShopItemUiState.Theme(
                            id = dto.id,
                            name = dto.name,
                            isPremium = dto.isPremium,
                            isOwned = dto.isOwned,
                            palette = dto.palette?.toThemePalette() ?: CozyPalettes.SystemDefault,
                            leafPrice = dto.leafPrice,
                            isEquipped = dto.isEquipped
                        )
                    }
                }
                _shopItems.value = mapped
                updateThemeState()
                mapped
            },
            onFailure = {
                _shopItems.value
            }
        )
    }
}
