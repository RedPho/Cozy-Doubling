package com.grepho.cozydoubling.core.economy

import androidx.compose.ui.graphics.Color
import com.grepho.cozydoubling.core.Supabase
import com.grepho.cozydoubling.core.profile.ProfileRepository
import com.grepho.cozydoubling.core.theming.ThemePalette
import com.grepho.cozydoubling.features.shop.ThemeItemUiState
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

    /**
     * Fetches all themes and their color details from Supabase.
     */
    suspend fun fetchThemes(): List<ThemeItemUiState> {
        println("DEBUG: EconomyRepo - Starting fetchThemes...")
        val myId = Supabase.client.auth.currentUserOrNull()?.id
        if (myId == null) {
            println("DEBUG: EconomyRepo - USER ID IS NULL. Aborting.")
            return emptyList()
        }


        val profile = ProfileRepository.profile.value
        println("DEBUG: EconomyRepo - User ID: $myId, Profile Loaded: ${profile != null}")


        return try {
            val ownedIds = Supabase.client.postgrest["user_inventory"]
                .select { filter { eq("user_id", myId) } }
                .decodeList<UserInventoryDto>()
                .map { it.itemId }
                .toSet()

            val rawItems = Supabase.client.postgrest["items"]
                .select(Columns.raw("*, theme_details(*)")) {
                    filter { eq("category_id", "theme") }
                }
                .decodeList<ThemeItemDto>()

            println("DEBUG: EconomyRepo - Found ${rawItems.size} themes in database.")

            rawItems.mapNotNull { dto ->
                val details = dto.details

                if (details == null) {
                    // If an item has no colors, skip it
                    null
                } else {
                    // Otherwise, convert it to a UI state
                    dto.toUiState(
                        details = details,
                        isOwned = ownedIds.contains(dto.id),
                        isEquipped = dto.id == profile?.equippedThemeId
                    )
                }
            }
        } catch (e: Exception) {
            println("DEBUG: EconomyRepo Error: ${e.message}")
            e.printStackTrace()
            emptyList()
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

@Serializable
data class ThemeDetailsDto(
    @SerialName("primary_color") val primary: String,
    @SerialName("on_primary") val onPrimary: String,
    @SerialName("primary_container") val primaryContainer: String,
    @SerialName("on_primary_container") val onPrimaryContainer: String,
    @SerialName("secondary_container") val secondaryContainer: String,
    @SerialName("on_secondary_container") val onSecondaryContainer: String,
    @SerialName("surface_variant") val surfaceVariant: String,
    @SerialName("on_surface_variant") val onSurfaceVariant: String,
    @SerialName("on_surface") val onSurface: String,
    val background: String
)

// =========================================================
// MAPPING LOGIC
// =========================================================

fun ThemeItemDto.toUiState(details: ThemeDetailsDto, isOwned: Boolean, isEquipped: Boolean): ThemeItemUiState = ThemeItemUiState(
    id = id,
    name = name,
    palette = details.toPalette(),
    leafPrice = leafPrice,
    iapPrice = "...", // Will be hydrated by RevenueCat later
    isPremium = isPremium,
    isOwned = isOwned,
    isEquipped = isEquipped
)

fun ThemeDetailsDto.toPalette(): ThemePalette = ThemePalette(
    primary = Color(android.graphics.Color.parseColor(primary)),
    onPrimary = Color(android.graphics.Color.parseColor(onPrimary)),
    primaryContainer = Color(android.graphics.Color.parseColor(primaryContainer)),
    onPrimaryContainer = Color(android.graphics.Color.parseColor(onPrimaryContainer)),
    secondaryContainer = Color(android.graphics.Color.parseColor(secondaryContainer)),
    onSecondaryContainer = Color(android.graphics.Color.parseColor(onSecondaryContainer)),
    surfaceVariant = Color(android.graphics.Color.parseColor(surfaceVariant)),
    onSurfaceVariant = Color(android.graphics.Color.parseColor(onSurfaceVariant)),
    onSurface = Color(android.graphics.Color.parseColor(onSurface)),
    background = Color(android.graphics.Color.parseColor(background))
)

@Serializable
data class UserInventoryDto(@SerialName("item_id") val itemId: String)