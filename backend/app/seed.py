from sqlalchemy.orm import Session
from app.models.shop import ItemCategory, Item, ThemeDetails

def seed_catalog_if_empty(db: Session):
    if db.query(ItemCategory).first() is not None:
        return

    # Categories
    theme_cat = ItemCategory(id="theme", name="Themes")
    pass_cat = ItemCategory(id="pass", name="Supporter Passes")
    db.add_all([theme_cat, pass_cat])
    db.flush()

    # Themes
    system_default = Item(
        id="system_default",
        category_id="theme",
        name="System Default",
        leaf_price=0,
        is_premium=False
    )
    system_default_details = ThemeDetails(
        item_id="system_default",
        primary="#7A8471",
        on_primary="#FFFFFF",
        primary_container="#CCD4C5",
        on_primary_container="#2D3428",
        secondary="#8C7B70",
        on_secondary="#FFFFFF",
        secondary_container="#E8DDD5",
        on_secondary_container="#382E26",
        tertiary="#A88B68",
        on_tertiary="#FFFFFF",
        tertiary_container="#F2E4D0",
        on_tertiary_container="#FFFFFF",
        surface="#FFFFFF",
        on_surface="#2C2C2A",
        background="#F9F6F0",
        on_background="#2C2C2A",
        surface_variant="#ECE8E1",
        on_surface_variant="#5C6455"
    )

    peach_blossom = Item(
        id="theme_peach_blossom",
        category_id="theme",
        name="Peach Blossom",
        leaf_price=100,
        is_premium=False
    )
    peach_blossom_details = ThemeDetails(
        item_id="theme_peach_blossom",
        primary="#C0583E",
        on_primary="#FFFFFF",
        primary_container="#FFDBD1",
        on_primary_container="#3B0900",
        secondary="#77574E",
        on_secondary="#FFFFFF",
        secondary_container="#FFDBD1",
        on_secondary_container="#2C150F",
        tertiary="#6C5D2F",
        on_tertiary="#FFFFFF",
        tertiary_container="#F6E1A6",
        on_tertiary_container="#231B00",
        surface="#FFF8F6",
        on_surface="#231917",
        background="#FFF8F6",
        on_background="#231917",
        surface_variant="#F5DED8",
        on_surface_variant="#53433F"
    )

    midnight_velvet = Item(
        id="theme_midnight_velvet",
        category_id="theme",
        name="Midnight Velvet",
        leaf_price=0,
        is_premium=True
    )
    midnight_velvet_details = ThemeDetails(
        item_id="theme_midnight_velvet",
        primary="#BAC3FF",
        on_primary="#08218A",
        primary_container="#293CA0",
        on_primary_container="#DEE0FF",
        secondary="#C5C4DD",
        on_secondary="#2E2F42",
        secondary_container="#454559",
        on_secondary_container="#E2E0F9",
        tertiary="#E6BAD7",
        on_tertiary="#45263E",
        tertiary_container="#5D3C55",
        on_tertiary_container="#FFD7F1",
        surface="#1B1B22",
        on_surface="#E4E1E9",
        background="#131318",
        on_background="#E4E1E9",
        surface_variant="#46464F",
        on_surface_variant="#C7C5D0"
    )

    # Passes
    pass_monthly = Item(
        id="pass_monthly",
        category_id="pass",
        name="Monthly Supporter Pass",
        leaf_price=0,
        is_premium=True,
        iap_id="cozy_supporter_monthly"
    )
    pass_yearly = Item(
        id="pass_yearly",
        category_id="pass",
        name="Yearly Supporter Pass",
        leaf_price=0,
        is_premium=True,
        iap_id="cozy_supporter_yearly"
    )
    pass_lifetime = Item(
        id="pass_lifetime",
        category_id="pass",
        name="Lifetime Supporter Pass",
        leaf_price=0,
        is_premium=True,
        iap_id="cozy_supporter_lifetime"
    )

    db.add_all([
        system_default, system_default_details,
        peach_blossom, peach_blossom_details,
        midnight_velvet, midnight_velvet_details,
        pass_monthly, pass_yearly, pass_lifetime
    ])
    db.commit()
