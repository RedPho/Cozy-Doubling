from typing import List
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session, joinedload

from app.database import get_db
from app.models.user import User
from app.models.profile import Profile
from app.models.shop import Item, UserInventory
from app.schemas.shop import (
    ShopItemResponse,
    EquipItemRequest,
    ThemePaletteSchema,
)
from app.services.auth import get_current_user_and_profile

router = APIRouter(prefix="/api/inventory", tags=["inventory"])

@router.get("", response_model=List[ShopItemResponse])
def list_owned_themes(
    user_and_profile: tuple[User, Profile] = Depends(get_current_user_and_profile),
    db: Session = Depends(get_db)
):
    _, profile = user_and_profile
    my_id = profile.id

    owned_rows = db.query(UserInventory.item_id).filter(UserInventory.user_id == my_id).all()
    owned_ids = {r[0] for r in owned_rows}
    owned_ids.add("system_default")

    all_themes = db.query(Item).options(joinedload(Item.theme_details)).filter(Item.category_id == "theme").all()

    owned_themes: List[ShopItemResponse] = []
    for item in all_themes:
        is_owned = (item.id in owned_ids) or (item.is_premium and profile.is_supporter)
        if not is_owned:
            continue

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

        owned_themes.append(ShopItemResponse(
            id=item.id,
            name=item.name,
            category_id=item.category_id,
            is_premium=item.is_premium,
            leaf_price=item.leaf_price,
            is_owned=True,
            is_equipped=(item.id == profile.equipped_theme_id),
            palette=palette_schema
        ))

    return owned_themes

@router.post("/equip")
def equip_theme(
    req: EquipItemRequest,
    user_and_profile: tuple[User, Profile] = Depends(get_current_user_and_profile),
    db: Session = Depends(get_db)
):
    _, profile = user_and_profile
    my_id = profile.id
    target_id = req.theme_id

    item = db.query(Item).filter((Item.id == target_id) & (Item.category_id == "theme")).first()
    if not item:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Theme not found")

    existing_inv = db.query(UserInventory).filter(
        (UserInventory.user_id == my_id) & (UserInventory.item_id == target_id)
    ).first()
    is_in_inv = existing_inv is not None or target_id == "system_default"
    is_supporter_unlocked = item.is_premium and profile.is_supporter

    if not is_in_inv and not is_supporter_unlocked:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="You do not own this item or have an active Supporter Pass."
        )

    profile.equipped_theme_id = target_id
    db.commit()
    return {"ok": True, "equipped_theme_id": target_id}

@router.post("/unequip")
def unequip_theme(
    user_and_profile: tuple[User, Profile] = Depends(get_current_user_and_profile),
    db: Session = Depends(get_db)
):
    _, profile = user_and_profile
    profile.equipped_theme_id = "system_default"
    db.commit()
    return {"ok": True, "equipped_theme_id": "system_default"}
