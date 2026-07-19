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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

object EconomyRepository {

    /**
     * Fetches all themes and their color details from Supabase.
     */
    suspend fun fetchThemes(): List<ThemeItemUiState> {
        return try {
            val profile = ProfileRepository.profile.value
            val rawItems = Supabase.client.postgrest["items"]
                .select(Columns.raw("*, theme_details(*)")) {
                    filter { eq("category_id", "theme") }
                }
                .decodeList<ThemeItemDto>()

            return rawItems.map { dto ->
                dto.toUiState(
                    isOwned = checkIfOwned(dto.id),
                    // CHECK: Is this the theme currently in our profile?
                    isEquipped = dto.id == profile?.equippedThemeId
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private suspend fun checkIfOwned(itemId: String): Boolean {
        val myId = Supabase.client.auth.currentUserOrNull()?.id ?: return false
        return try {
            val response = Supabase.client.postgrest["user_inventory"]
                .select {
                    filter {
                        eq("user_id", myId)
                        eq("item_id", itemId)
                    }
                }
            !response.data.isNullOrBlank() && response.data != "[]"
        } catch (e: Exception) {
            false
        }
    }

    suspend fun equipTheme(themeId: String) {
        val myId = Supabase.client.auth.currentUserOrNull()?.id ?: return

        // Update the 'profiles' table with the new theme ID
        Supabase.client.postgrest["profiles"].update(
            mapOf("equipped_theme_id" to themeId)
        ) {
            filter { eq("id", myId) }
        }

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
    @SerialName("theme_details") val details: ThemeDetailsDto
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

fun ThemeItemDto.toUiState(isOwned: Boolean, isEquipped: Boolean): ThemeItemUiState = ThemeItemUiState(
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