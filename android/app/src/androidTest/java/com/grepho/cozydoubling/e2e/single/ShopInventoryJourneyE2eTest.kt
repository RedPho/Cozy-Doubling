package com.grepho.cozydoubling.e2e.single

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.grepho.cozydoubling.core.MainActivity
import com.grepho.cozydoubling.core.di.ServiceLocator
import com.grepho.cozydoubling.e2e.BackendTestHelper
import com.grepho.cozydoubling.e2e.E2eTestHarness
import com.grepho.cozydoubling.e2e.openOasisSubTab
import com.grepho.cozydoubling.e2e.openSettingsFromTopBar
import com.grepho.cozydoubling.e2e.signInWithEmailUi
import com.grepho.cozydoubling.e2e.signOutFromHome
import com.grepho.cozydoubling.e2e.waitUntilNodeDoesNotExist
import com.grepho.cozydoubling.e2e.waitUntilNodeExists
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Single-device E2E covering E2E-07 (Shop & Supporter Subscriptions),
 * E2E-08 (Inventory & Theme Customization), E2E-05 (Journey Stats),
 * and E2E-11 (Theme Persistence & Expiration Webhook Auto-Revert).
 */
@RunWith(AndroidJUnit4::class)
class ShopInventoryJourneyE2eTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        scenario = E2eTestHarness.launchMainActivity()
    }

    @After
    fun tearDown() {
        scenario?.close()
        E2eTestHarness.resetAppState()
    }

    @Test
    fun shopInsufficientLeaves_purchaseTheme_equipUnequip_supporterPass_andJourneyStats() {
        val email = E2eTestHarness.uniqueEmail("shop_econ")
        val password = "CozyPass123!"

        composeRule.signInWithEmailUi(email, password)

        // 1. Check default Inventory state (1 theme: System Default equipped)
        composeRule.openOasisSubTab("Inventory")
        composeRule.waitUntilNodeExists(hasText("Owned Themes"))
        composeRule.waitUntilNodeExists(hasText("1 Themes"))
        composeRule.waitUntilNodeExists(hasTestTag("inventory_equipped_system_default"))

        // 2. Browse seeded Shop catalog
        composeRule.onNodeWithText("Shop").performClick()
        composeRule.waitUntilNodeExists(hasText("Cozy Supporter"))
        composeRule.waitUntilNodeExists(hasText("Monthly"))

        // Scroll to Peach Blossom (100 Leaves)
        composeRule.onNodeWithTag("shop_list")
            .performScrollToNode(hasTestTag("shop_theme_card_theme_peach_blossom"))
        composeRule.waitUntilNodeExists(hasTestTag("shop_theme_buy_theme_peach_blossom"))

        // Edge case: Cancel Unlock dialog does not purchase
        composeRule.onNodeWithTag("shop_theme_buy_theme_peach_blossom").performClick()
        composeRule.waitUntilNodeExists(hasText("Unlock Theme"))
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.waitUntilNodeDoesNotExist(hasText("Unlock Theme"))

        // Edge case: Insufficient leaves (0 < 100) keeps theme locked and balance 0
        composeRule.onNodeWithTag("shop_theme_buy_theme_peach_blossom").performClick()
        composeRule.waitUntilNodeExists(hasText("Unlock Theme"))
        composeRule.onNodeWithText("Unlock").performClick()
        composeRule.waitUntilNodeDoesNotExist(hasText("Unlock Theme"))
        composeRule.onNodeWithTag("shop_theme_buy_theme_peach_blossom").assertIsDisplayed()

        // 3. Earn 102 leaves via real backend /api/sessions/start + /finish (2 sessions x 17 tasks x 3 = 102)
        // Task rewards are capped at 20 per session server-side, so split across two sessions.
        repeat(2) {
            BackendTestHelper.completeRealSessionViaApi(
                email = email,
                password = password,
                tasksCompleted = 17,
                lastTaskText = "Earn leaves for Peach Blossom"
            )
        }
        runBlocking { ServiceLocator.profileRepository.refreshProfile() }
        composeRule.waitUntilNodeExists(hasText("102"))

        // Happy path: Purchase Peach Blossom (100 leaves -> balance becomes 2)
        composeRule.onNodeWithTag("shop_list")
            .performScrollToNode(hasTestTag("shop_theme_card_theme_peach_blossom"))
        composeRule.onNodeWithTag("shop_theme_buy_theme_peach_blossom").performClick()
        composeRule.waitUntilNodeExists(hasText("Unlock Theme"))
        composeRule.onNodeWithText("Unlock").performClick()

        composeRule.waitUntilNodeExists(hasTestTag("shop_theme_owned_theme_peach_blossom"))
        composeRule.waitUntilNodeExists(hasText("2"))

        // 4. Inventory shows 2 themes; equip Peach Blossom and verify persistence across navigation
        composeRule.onNodeWithText("Inventory").performClick()
        composeRule.waitUntilNodeExists(hasText("2 Themes"))
        composeRule.onNodeWithTag("inventory_list")
            .performScrollToNode(hasTestTag("inventory_theme_card_theme_peach_blossom"))
        composeRule.onNodeWithTag("inventory_apply_theme_peach_blossom").performClick()
        composeRule.waitUntilNodeExists(hasTestTag("inventory_equipped_theme_peach_blossom"))

        // Navigate away to Home tab and back to Inventory -> Peach Blossom stays equipped
        composeRule.onNodeWithText("Home").performClick()
        composeRule.waitUntilNodeExists(hasText("Start Focus"))
        composeRule.openOasisSubTab("Inventory")
        composeRule.onNodeWithTag("inventory_list")
            .performScrollToNode(hasTestTag("inventory_theme_card_theme_peach_blossom"))
        composeRule.waitUntilNodeExists(hasTestTag("inventory_equipped_theme_peach_blossom"))

        // Unequip / revert to System Default
        composeRule.onNodeWithTag("inventory_list")
            .performScrollToNode(hasTestTag("inventory_theme_card_system_default"))
        composeRule.onNodeWithTag("inventory_apply_system_default").performClick()
        composeRule.waitUntilNodeExists(hasTestTag("inventory_equipped_system_default"))

        // 5. Purchase Monthly Supporter Pass -> unlocks Midnight Velvet & enables Manage Subscription
        composeRule.onNodeWithText("Shop").performClick()
        composeRule.waitUntilNodeExists(hasText("Monthly"))
        composeRule.onNodeWithText("Monthly").performClick()
        composeRule.waitUntilNodeExists(hasText("Thank you for being a supporter!", substring = true))

        // Verify Midnight Velvet now unlocked in Inventory and can be equipped
        composeRule.onNodeWithText("Inventory").performClick()
        composeRule.waitUntilNodeExists(hasText("3 Themes"))
        composeRule.onNodeWithTag("inventory_list")
            .performScrollToNode(hasTestTag("inventory_theme_card_theme_midnight_velvet"))
        composeRule.onNodeWithTag("inventory_apply_theme_midnight_velvet").performClick()
        composeRule.waitUntilNodeExists(hasTestTag("inventory_equipped_theme_midnight_velvet"))

        // 6. Verify Journey dashboard reflects remaining leaf balance (2) & focus stats
        composeRule.onNodeWithText("Journey").performClick()
        composeRule.waitUntilNodeExists(hasText("Total Leaves"))
        composeRule.waitUntilNodeExists(hasText("Time in Deep Focus"))
        composeRule.waitUntilNodeExists(hasText("2"))

        // 7. Verify Settings displays Manage Subscription & Restore Purchases works
        composeRule.openSettingsFromTopBar()
        composeRule.waitUntilNodeExists(hasText("Manage Subscription"))
        composeRule.onNodeWithText("Restore Purchases").performScrollTo().performClick()
        composeRule.waitUntilNodeExists(hasText("Manage Subscription"))
        composeRule.onNodeWithContentDescription("OK").performClick()
    }

    @Test
    fun shopAndThemeEdgeCases_getPassScroll_yearlyPass_signOutPersistence_andExpirationAutoRevert() {
        val email = E2eTestHarness.uniqueEmail("shop_expire")
        val password = "CozyPass123!"

        composeRule.signInWithEmailUi(email, password)
        val userId = E2eTestHarness.currentUserId()

        // Earn 102 leaves so Peach Blossom stays owned even after supporter expiration
        // Task rewards are capped at 20 per session server-side, so split across two sessions.
        repeat(2) {
            BackendTestHelper.completeRealSessionViaApi(
                email = email,
                password = password,
                tasksCompleted = 17,
                lastTaskText = "Earn leaves before supporter test"
            )
        }
        runBlocking { ServiceLocator.profileRepository.refreshProfile() }

        composeRule.openOasisSubTab("Shop")
        composeRule.waitUntilNodeExists(hasText("102"))

        // Buy & equip Peach Blossom
        composeRule.onNodeWithTag("shop_list")
            .performScrollToNode(hasTestTag("shop_theme_card_theme_peach_blossom"))
        composeRule.onNodeWithTag("shop_theme_buy_theme_peach_blossom").performClick()
        composeRule.waitUntilNodeExists(hasText("Unlock Theme"))
        composeRule.onNodeWithText("Unlock").performClick()
        composeRule.waitUntilNodeExists(hasTestTag("shop_theme_owned_theme_peach_blossom"))

        // Edge case: "Get Pass" on premium Midnight Velvet scrolls Shop back to Cozy Supporter card
        composeRule.onNodeWithTag("shop_list")
            .performScrollToNode(hasTestTag("shop_theme_card_theme_midnight_velvet"))
        composeRule.onNodeWithTag("shop_theme_buy_theme_midnight_velvet").performClick()
        composeRule.waitUntilNodeExists(hasText("Cozy Supporter"))
        composeRule.waitUntilNodeExists(hasText("Yearly"))

        // Equip Peach Blossom -> Sign Out -> Sign back in -> stays equipped
        composeRule.onNodeWithText("Inventory").performClick()
        composeRule.onNodeWithTag("inventory_list")
            .performScrollToNode(hasTestTag("inventory_theme_card_theme_peach_blossom"))
        composeRule.onNodeWithTag("inventory_apply_theme_peach_blossom").performClick()
        composeRule.waitUntilNodeExists(hasTestTag("inventory_equipped_theme_peach_blossom"))

        composeRule.onNodeWithText("Home").performClick()
        composeRule.signOutFromHome()
        composeRule.signInWithEmailUi(email, password)

        composeRule.openOasisSubTab("Inventory")
        composeRule.onNodeWithTag("inventory_list")
            .performScrollToNode(hasTestTag("inventory_theme_card_theme_peach_blossom"))
        composeRule.waitUntilNodeExists(hasTestTag("inventory_equipped_theme_peach_blossom"))

        // Purchase Yearly Supporter Pass and equip Midnight Velvet
        composeRule.onNodeWithText("Shop").performClick()
        composeRule.waitUntilNodeExists(hasText("Yearly"))
        composeRule.onNodeWithText("Yearly").performClick()
        composeRule.waitUntilNodeExists(hasText("Thank you for being a supporter!", substring = true))

        composeRule.onNodeWithText("Inventory").performClick()
        composeRule.waitUntilNodeExists(hasText("3 Themes"))
        composeRule.onNodeWithTag("inventory_list")
            .performScrollToNode(hasTestTag("inventory_theme_card_theme_midnight_velvet"))
        composeRule.onNodeWithTag("inventory_apply_theme_midnight_velvet").performClick()
        composeRule.waitUntilNodeExists(hasTestTag("inventory_equipped_theme_midnight_velvet"))

        // Edge case: RevenueCat EXPIRATION webhook expires pass & auto-reverts equipped premium theme to System Default
        BackendTestHelper.triggerSupporterExpirationWebhook(userId)

        // Navigate Home -> Oasis -> Inventory (nav changes trigger refreshProfile())
        composeRule.onNodeWithText("Home").performClick()
        composeRule.waitUntilNodeExists(hasText("Start Focus"))
        composeRule.openOasisSubTab("Inventory")

        composeRule.waitUntilNodeExists(hasText("2 Themes"))
        composeRule.onNodeWithTag("inventory_theme_card_theme_midnight_velvet").assertDoesNotExist()
        composeRule.waitUntilNodeExists(hasTestTag("inventory_equipped_system_default"))

        // Purchased basic theme (Peach Blossom) remains in Inventory; Manage Subscription disappears in Settings
        composeRule.onNodeWithTag("inventory_list")
            .performScrollToNode(hasTestTag("inventory_theme_card_theme_peach_blossom"))
        composeRule.waitUntilNodeExists(hasTestTag("inventory_apply_theme_peach_blossom"))

        composeRule.openSettingsFromTopBar()
        composeRule.onNodeWithText("Manage Subscription").assertDoesNotExist()
    }
}
