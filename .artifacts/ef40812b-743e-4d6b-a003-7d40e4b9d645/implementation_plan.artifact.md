# Implementation Plan - Diverse Marketplace & Supporter Pass

We will expand the Shop to include "Supporter Passes" and refine the ownership logic. Premium themes will now be "unlocked" by an active pass, while Basic themes remain individual leaf purchases.

## User Review Required

> [!IMPORTANT]
> - **Premium Themes** will no longer show a "Buy" button if the user has a Supporter Pass. They will instantly become available in the Inventory.
> - **Supporter Passes** will be listed at the top of the Shop and purchased with cash (IAP).

## Proposed Changes

### Core: Economy Data

#### [MODIFY] [EconomyRepository.kt](file:///home/emin/repos/android-repos/CozyDoubling/app/src/main/java/com/grepho/cozydoubling/core/economy/EconomyRepository.kt)
- Complete `fetchShopItems()` to map database items to `ShopItemUiState.Theme` or `ShopItemUiState.Pass`.
- Implement `isOwned` logic for both types based on the `user_inventory` table.

### Feature: Shop

#### [MODIFY] [ShopViewModel.kt](file:///home/emin/repos/android-repos/CozyDoubling/app/src/main/java/com/grepho/cozydoubling/features/shop/ShopViewModel.kt)
- Update `themes` flow to `items` (List of `ShopItemUiState`).
- Call `fetchShopItems()` on refresh.

#### [MODIFY] [ShopPage.kt](file:///home/emin/repos/android-repos/CozyDoubling/app/src/main/java/com/grepho/cozydoubling/features/shop/ShopPage.kt)
- Update `ShopPage` to iterate over `ShopItemUiState` list.
- **[NEW] `SupporterPassCard`**: A high-end card for monthly/yearly/lifetime passes.
- **`ThemeShopCard`**: Refine `isAvailable` logic: `isPremium ? isSupporter : isOwned`.

### Feature: Inventory

#### [MODIFY] [InventoryViewModel.kt](file:///home/emin/repos/android-repos/CozyDoubling/app/src/main/java/com/grepho/cozydoubling/features/inventory/InventoryViewModel.kt)
- Use `fetchShopItems()` and filter for `Theme` items only.
- Include Premium themes in the list if `isSupporter` is true.

## Verification Plan

### Manual Verification
- Verify the Shop displays "Supporter Passes" correctly.
- Verify that Basic themes can still be bought with leaves.
- Verify that Premium themes show as "Locked" for non-supporters.
- Test (mocking) that buying a pass instantly populates the Inventory with Premium themes.
