package com.grepho.cozydoubling.core.economy

import androidx.compose.ui.graphics.Color
import com.grepho.cozydoubling.core.Supabase
import com.grepho.cozydoubling.core.profile.ProfileRepository
import com.grepho.cozydoubling.core.theming.ThemePalette
import com.grepho.cozydoubling.features.shop.ShopItemUiState
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


object EconomyRepository {
    private val repoScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val activePalette: StateFlow<ThemePalette?> = ProfileRepository.profile
        .map { profile ->
            val themeId = profile?.equippedThemeId ?: return@map null
            fetchThemePalette(themeId)
        }
        .stateIn(
            scope = repoScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    private suspend fun fetchThemePalette(themeId: String): ThemePalette? {
        return try {
            Supabase.client.postgrest["theme_details"]
                .select { filter { eq("item_id", themeId) } }
                .decodeSingle<ThemeDetailsDto>()
                .toPalette()
        } catch (e: Exception) {
            null
        }
    }

    suspend fun equipTheme(themeId: String) {
        val myId = Supabase.client.auth.currentUserOrNull()?.id ?: return

        Supabase.client.postgrest.rpc(
            function = "equip_item",
            parameters = mapOf("target_item_id" to themeId)
        )

        // REACTIVE REFRESH: Tell the app to fetch the updated profile
        // so the whole app theme changes instantly!
        ProfileRepository.refreshProfile()
    }

    suspend fun purchaseWithLeaves(itemId: String) {
        Supabase.client.postgrest.rpc(
            function = "buy_item",
            parameters = mapOf("target_item_id" to itemId)
        )
    }

    suspend fun fetchShopItems(): List<ShopItemUiState> {
        val myId = Supabase.client.auth.currentUserOrNull()?.id ?: return emptyList()
        val profile = ProfileRepository.profile.value

        return try {
            val ownedIds = Supabase.client.postgrest["user_inventory"]
                .select { filter { eq("user_id", myId) } }
                .decodeList<UserInventoryDto>()
                .map { it.itemId }
                .toSet()

            val rawItems = Supabase.client.postgrest["items"]
                .select(Columns.raw("*, theme_details(*)"))
                .decodeList<ThemeItemDto>()

            rawItems.map { dto ->
                val isOwned = ownedIds.contains(dto.id)
                val details = dto.details

                if (details == null) {
                    // No details means it's a Pass
                    ShopItemUiState.Pass(
                        id = dto.id,
                        name = dto.name,
                        isPremium = true,
                        isOwned = isOwned,
                        iapId = dto.iapId ?: "",
                        priceString = "..." // To be hydrated by Play Billing
                    )
                } else {
                    // Has details means it's a Theme
                    ShopItemUiState.Theme(
                        id = dto.id,
                        name = dto.name,
                        isPremium = dto.isPremium,
                        isOwned = isOwned,
                        palette = details.toPalette(),
                        leafPrice = dto.leafPrice,
                        isEquipped = dto.id == profile?.equippedThemeId
                    )
                }
            }
        } catch (e: Exception) {
            println("DEBUG: Shop Error: ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }
}

// =========================================================
// DATA TRANSFER OBJECTS (DTOs)
// =========================================================

@Serializable
data class ThemeItemDto(
    val id: String,
    val name: String,
    @SerialName("leaf_price") val leafPrice: Int,
    @SerialName("is_premium") val isPremium: Boolean,
    @SerialName("iap_id") val iapId: String?,
    @SerialName("theme_details") val details: ThemeDetailsDto? = null
)

// =========================================================
// MAPPING LOGIC
// =========================================================


@Serializable
data class ThemeDetailsDto(
    @SerialName("primary") val primary: String, // Matches DB column
    @SerialName("on_primary") val onPrimary: String,
    @SerialName("primary_container") val primaryContainer: String,
    @SerialName("on_primary_container") val onPrimaryContainer: String,
    @SerialName("secondary") val secondary: String,
    @SerialName("on_secondary") val onSecondary: String,
    @SerialName("secondary_container") val secondaryContainer: String,
    @SerialName("on_secondary_container") val onSecondaryContainer: String,
    @SerialName("tertiary") val tertiary: String,
    @SerialName("on_tertiary") val onTertiary: String,
    @SerialName("tertiary_container") val tertiaryContainer: String,
    @SerialName("surface") val surface: String,
    @SerialName("on_background") val onBackground: String,
    @SerialName("surface_variant") val surfaceVariant: String,
    @SerialName("on_surface_variant") val onSurfaceVariant: String,
    @SerialName("on_surface") val onSurface: String,
    @SerialName("background") val background: String
)

fun ThemeDetailsDto.toPalette(): ThemePalette = ThemePalette(
    primary = Color(android.graphics.Color.parseColor(primary)),
    onPrimary = Color(android.graphics.Color.parseColor(onPrimary)),
    primaryContainer = Color(android.graphics.Color.parseColor(primaryContainer)),
    onPrimaryContainer = Color(android.graphics.Color.parseColor(onPrimaryContainer)),
    secondary = Color(android.graphics.Color.parseColor(secondary)),
    onSecondary = Color(android.graphics.Color.parseColor(onSecondary)),
    secondaryContainer = Color(android.graphics.Color.parseColor(secondaryContainer)),
    onSecondaryContainer = Color(android.graphics.Color.parseColor(onSecondaryContainer)),
    tertiary = Color(android.graphics.Color.parseColor(tertiary)),
    onTertiary = Color(android.graphics.Color.parseColor(onTertiary)),
    tertiaryContainer = Color(android.graphics.Color.parseColor(tertiaryContainer)),
    surface = Color(android.graphics.Color.parseColor(surface)),
    onSurface = Color(android.graphics.Color.parseColor(onSurface)),
    background = Color(android.graphics.Color.parseColor(background)),
    onBackground = Color(android.graphics.Color.parseColor(onBackground)),
    surfaceVariant = Color(android.graphics.Color.parseColor(surfaceVariant)),
    onSurfaceVariant = Color(android.graphics.Color.parseColor(onSurfaceVariant))
)
@Serializable
data class UserInventoryDto(@SerialName("item_id") val itemId: String)