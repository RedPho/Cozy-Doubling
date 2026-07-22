package com.grepho.cozydoubling.core.economy

import androidx.compose.ui.graphics.Color
import com.grepho.cozydoubling.core.Supabase
import com.grepho.cozydoubling.core.billing.BillingRepository
import com.grepho.cozydoubling.core.network.ConnectionStateManager
import com.grepho.cozydoubling.core.profile.ProfileRepository
import com.grepho.cozydoubling.core.theming.ThemePalette
import com.grepho.cozydoubling.features.shop.ShopItemUiState
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

sealed class ThemeState {
    object Loading : ThemeState()
    object Default : ThemeState()
    data class Custom(val palette: ThemePalette) : ThemeState()
}

object EconomyRepository {
    private val repoScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // 1. Pre-loaded Shop Items (Eagerly starts loading on app start)
    val shopItems: StateFlow<List<ShopItemUiState>> = ProfileRepository.profile
        .filterNotNull()
        .map { 
            println("DEBUG: EconomyRepository - Profile loaded, fetching shop items...")
            fetchShopItems() 
        }
        .stateIn(repoScope, SharingStarted.Eagerly, emptyList())

    // 🚀 2. Processed Shop Items: Groups items and hydrates localized prices in background
    val processedShopItems: StateFlow<List<ShopItemUiState>> = combine(
        shopItems,
        BillingRepository.offerings
    ) { rawItems, offerings ->
        println("DEBUG: EconomyRepository - Processing shop items with ${rawItems.size} raw items...")
        val rcPackages = offerings?.current?.availablePackages ?: emptyList()

        rawItems.map { item ->
            if (item is ShopItemUiState.Pass) {
                val rcPackage = rcPackages.find { it.product.id == item.iapId }
                item.copy(priceString = rcPackage?.product?.price?.formatted ?: "...")
            } else item
        }.let { hydratedList ->
            val themes = hydratedList.filterIsInstance<ShopItemUiState.Theme>()
            val passes = hydratedList.filterIsInstance<ShopItemUiState.Pass>()
                .sortedBy { pass ->
                    when {
                        pass.name.contains("Monthly", ignoreCase = true) -> 1
                        pass.name.contains("Yearly", ignoreCase = true) -> 2
                        pass.name.contains("Lifetime", ignoreCase = true) -> 3
                        else -> 4
                    }
                }

            val finalResult = mutableListOf<ShopItemUiState>()
            if (passes.isNotEmpty()) {
                finalResult.add(ShopItemUiState.SupporterSection(passes))
            }
            finalResult.addAll(themes)
            finalResult
        }
    }
    .flowOn(Dispatchers.Default) // Perform computation off-thread
    .stateIn(repoScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 3. Smart Theme State (Wait for Auth confirm before leaving 'Loading')
    val themeState: StateFlow<ThemeState> = combine(
        Supabase.client.auth.sessionStatus,
        ProfileRepository.profile
    ) { status, profile ->
        println("DEBUG: EconomyRepository - themeState combining. Status: $status")
        when (status) {
            is SessionStatus.Authenticated -> {
                if (profile == null) {
                    println("DEBUG: EconomyRepository - Authenticated but profile is null, Loading.")
                    return@combine ThemeState.Loading
                }
                val themeId = profile.equippedThemeId ?: run {
                    println("DEBUG: EconomyRepository - No theme equipped, Default.")
                    return@combine ThemeState.Default
                }
                println("DEBUG: EconomyRepository - Fetching palette for theme $themeId")
                val palette = fetchThemePalette(themeId)
                if (palette != null) ThemeState.Custom(palette) else ThemeState.Default
            }
            is SessionStatus.NotAuthenticated -> {
                println("DEBUG: EconomyRepository - Not authenticated, Default.")
                ThemeState.Default
            }
            else -> ThemeState.Loading
        }
    }.stateIn(repoScope, SharingStarted.WhileSubscribed(5000), ThemeState.Loading)

    private suspend fun fetchThemePalette(themeId: String): ThemePalette? {
        return try {
            println("DEBUG: fetchThemePalette - Fetching for $themeId")
            Supabase.client.postgrest["theme_details"]
                .select { filter { eq("item_id", themeId) } }
                .decodeSingle<ThemeDetailsDto>()
                .toPalette()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            println("ERROR: fetchThemePalette - Failed: ${e.message}")
            e.printStackTrace()
            ConnectionStateManager.reportServerError()
            null
        }
    }

    suspend fun equipTheme(themeId: String) {
        try {
            println("DEBUG: equipTheme - Equipping $themeId")
            Supabase.client.postgrest.rpc(
                function = "equip_item",
                parameters = mapOf("target_item_id" to themeId)
            )

            // REACTIVE REFRESH: Tell the app to fetch the updated profile
            // so the whole app theme changes instantly!
            ProfileRepository.refreshProfile()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            println("ERROR: equipTheme - Failed: ${e.message}")
            e.printStackTrace()
            ConnectionStateManager.reportServerError()
        }
    }

    suspend fun purchaseWithLeaves(itemId: String) {
        try {
            println("DEBUG: purchaseWithLeaves - Purchasing $itemId")
            Supabase.client.postgrest.rpc(
                function = "buy_item",
                parameters = mapOf("target_item_id" to itemId)
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            println("ERROR: purchaseWithLeaves - Failed: ${e.message}")
            e.printStackTrace()
            ConnectionStateManager.reportServerError()
        }
    }

    suspend fun fetchShopItems(): List<ShopItemUiState> {
        val myId = Supabase.client.auth.currentUserOrNull()?.id ?: run {
            println("WARNING: fetchShopItems - No current user ID")
            return emptyList()
        }
        val profile = ProfileRepository.profile.value

        return try {
            println("DEBUG: fetchShopItems - Fetching items for $myId")
            val ownedIds = Supabase.client.postgrest["user_inventory"]
                .select { filter { eq("user_id", myId) } }
                .decodeList<UserInventoryDto>()
                .map { it.itemId }
                .toSet()

            println("DEBUG: fetchShopItems - User owns ${ownedIds.size} items")

            val rawItems = Supabase.client.postgrest["items"]
                .select(Columns.raw("*, theme_details(*)"))
                .decodeList<ThemeItemDto>()

            println("DEBUG: fetchShopItems - Total raw items in shop: ${rawItems.size}")

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
            if (e is CancellationException) throw e
            println("ERROR: fetchShopItems - Failed: ${e.message}")
            e.printStackTrace()
            ConnectionStateManager.reportServerError()
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
    @SerialName("on_tertiary_container") val onTertiaryContainer: String,
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
    onTertiaryContainer = Color(android.graphics.Color.parseColor(onTertiaryContainer)),
    surface = Color(android.graphics.Color.parseColor(surface)),
    onSurface = Color(android.graphics.Color.parseColor(onSurface)),
    background = Color(android.graphics.Color.parseColor(background)),
    onBackground = Color(android.graphics.Color.parseColor(onBackground)),
    surfaceVariant = Color(android.graphics.Color.parseColor(surfaceVariant)),
    onSurfaceVariant = Color(android.graphics.Color.parseColor(onSurfaceVariant))
)
@Serializable
data class UserInventoryDto(@SerialName("item_id") val itemId: String)
