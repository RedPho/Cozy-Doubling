from typing import List
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session, joinedload

from app.database import get_db
from app.models.user import User
from app.models.profile import Profile
from app.models.shop import Item, ThemeDetails, UserInventory
from app.schemas.shop import (
    ShopItemResponse,
    BuyItemRequest,
    ThemePaletteSchema,
)
from app.services.auth import get_current_user_and_profile

router = APIRouter(prefix="/api/shop", tags=["shop"])

@router.get("/items", response_model=List[ShopItemResponse])
def list_shop_items(
    user_and_profile: tuple[User, Profile] = Depends(get_current_user_and_profile),
    db: Session = Depends(get_db)
):
    _, profile = user_and_profile
    my_id = profile.id

    owned_rows = db.query(UserInventory.item_id).filter(UserInventory.user_id == my_id).all()
    owned_ids = {r[0] for r in owned_rows}
    owned_ids.add("system_default")

    items = db.query(Item).options(joinedload(Item.theme_details)).all()

    response: List[ShopItemResponse] = []
    for item in items:
        is_owned = item.id in owned_ids or (item.is_premium and profile.is_supporter)
        is_equipped = (item.id == profile.equipped_theme_id)

        palette_schema = None
        if item.theme_details:
            td = item.theme_details
            palette_schema = ThemePaletteSchema(
                primary=td.primary,
                on_primary=td.on_primary,
                primary_container=td.primary_container,
                on_primary_container=td.on_primary_container,
                secondary=td.secondary,
                on_secondary=td.on_secondary,
                secondary_container=td.secondary_container,
                on_secondary_container=td.on_secondary_container,
                tertiary=td.tertiary,
                on_tertiary=td.on_tertiary,
                tertiary_container=td.tertiary_container,
                on_tertiary_container=td.on_tertiary_container,
                surface=td.surface,
                on_surface=td.on_surface,
                background=td.background,
                on_background=td.on_background,
                surface_variant=td.surface_variant,
                on_surface_variant=td.on_surface_variant
            )

        response.append(ShopItemResponse(
            id=item.id,
            name=item.name,
            category_id=item.category_id,
            is_premium=item.is_premium,
            leaf_price=item.leaf_price,
            is_owned=is_owned,
            is_equipped=is_equipped,
            iap_id=item.iap_id,
            palette=palette_schema
        ))

    return response

@router.post("/buy")
def buy_item(
    req: BuyItemRequest,
    user_and_profile: tuple[User, Profile] = Depends(get_current_user_and_profile),
    db: Session = Depends(get_db)
):
    _, profile = user_and_profile
    my_id = profile.id

    item = db.query(Item).filter(Item.id == req.item_id).first()
    if not item:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Item not found"
        )

    # 1. Premium items cannot be bought with leaves
    if item.is_premium:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Premium items are unlocked via a Supporter Pass and cannot be bought with leaves."
        )

    # 2. Check already owned
    existing_inv = db.query(UserInventory).filter(
        (UserInventory.user_id == my_id) & (UserInventory.item_id == req.item_id)
    ).first()
    if existing_inv:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="You already own this item."
        )

    # 3. Balance check
    if profile.leaves < item.leaf_price:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Not enough leaves."
        )

    # 4. Atomic deduct + add to inventory
    profile.leaves -= item.leaf_price
    db.add(UserInventory(user_id=my_id, item_id=item.id))
    db.commit()

    return {
        "ok": True,
        "message": "Item purchased successfully",
        "new_balance": profile.leaves
    }
