"""The shop catalog, kept in sync with the database on every start.

``sync_catalog`` inserts missing categories, items and theme palettes and updates changed ones, so an
existing database (production included) picks up catalog changes. It never deletes an item: users may own it.

The eleven themes from 1.1.0 keep their 1.1.0 item IDs, so the Supabase import carries ownership and
equipped themes over unchanged. Pass ``iap_id``s are the live RevenueCat product IDs.
"""
from dataclasses import dataclass
from typing import Optional

from sqlalchemy.orm import Session

from app.models.shop import Item, ItemCategory, ThemeDetails

# Material 3 colour roles, in the order the palettes below list them.
COLOR_ROLES = (
    "primary", "on_primary", "primary_container", "on_primary_container",
    "secondary", "on_secondary", "secondary_container", "on_secondary_container",
    "tertiary", "on_tertiary", "tertiary_container", "on_tertiary_container",
    "surface", "on_surface", "background", "on_background", "surface_variant", "on_surface_variant",
)


def _palette(*colors: str) -> dict[str, str]:
    assert len(colors) == len(COLOR_ROLES)
    return dict(zip(COLOR_ROLES, colors))


@dataclass(frozen=True)
class Theme:
    id: str
    name: str
    leaf_price: int
    is_premium: bool
    palette: dict[str, str]


@dataclass(frozen=True)
class Pass:
    id: str
    name: str
    iap_id: str


CATEGORIES = {"theme": "Themes", "pass": "Supporter Passes"}

THEMES: tuple[Theme, ...] = (
    # 2.0 themes
    Theme("system_default", "System Default", leaf_price=0, is_premium=False, palette=_palette(
        "#7A8471", "#FFFFFF", "#CCD4C5", "#2D3428", "#8C7B70", "#FFFFFF",
        "#E8DDD5", "#382E26", "#A88B68", "#FFFFFF", "#F2E4D0", "#FFFFFF",
        "#FFFFFF", "#2C2C2A", "#F9F6F0", "#2C2C2A", "#ECE8E1", "#5C6455",
    )),
    Theme("theme_peach_blossom", "Peach Blossom", leaf_price=100, is_premium=False, palette=_palette(
        "#C0583E", "#FFFFFF", "#FFDBD1", "#3B0900", "#77574E", "#FFFFFF",
        "#FFDBD1", "#2C150F", "#6C5D2F", "#FFFFFF", "#F6E1A6", "#231B00",
        "#FFF8F6", "#231917", "#FFF8F6", "#231917", "#F5DED8", "#53433F",
    )),
    Theme("theme_midnight_velvet", "Midnight Velvet", leaf_price=0, is_premium=True, palette=_palette(
        "#BAC3FF", "#08218A", "#293CA0", "#DEE0FF", "#C5C4DD", "#2E2F42",
        "#454559", "#E2E0F9", "#E6BAD7", "#45263E", "#5D3C55", "#FFD7F1",
        "#1B1B22", "#E4E1E9", "#131318", "#E4E1E9", "#46464F", "#C7C5D0",
    )),
    # 1.1.0 themes (IDs and colours from the 1.1.0 catalog)
    Theme("1e1b0991-5e8a-431f-a83d-3cfa967ece5a", "Arctic Frost", leaf_price=0, is_premium=True, palette=_palette(
        "#0288D1", "#FFFFFF", "#B3E5FC", "#001F28", "#00BCD4", "#000000",
        "#E0F7FA", "#001F24", "#78909C", "#FFFFFF", "#ECEFF1", "#01579B",
        "#FFFFFF", "#191C1E", "#F1F9FF", "#191C1E", "#DCE3E9", "#41484D",
    )),
    Theme("e2bf4131-ebd3-47ed-ac89-ab5260f45841", "Cozy Night", leaf_price=3000, is_premium=False, palette=_palette(
        "#B08F3B", "#000000", "#251F10", "#FFD54F", "#795548", "#FFFFFF",
        "#4E342E", "#D7CCC8", "#FBC02D", "#000000", "#FFF9C4", "#FFD54F",
        "#121212", "#E0E0E0", "#000000", "#FFFFFF", "#1E1E1E", "#BDBDBD",
    )),
    Theme("c8c7d699-944c-4420-8925-8ee29aa62e0b", "Forest Moss", leaf_price=3000, is_premium=False, palette=_palette(
        "#558B2F", "#FFFFFF", "#DCEDC8", "#1B2E0E", "#79573F", "#FFFFFF",
        "#E8F5C8", "#33691E", "#765A05", "#FFFFFF", "#B08F3B", "#253000",
        "#FFFFFF", "#1B1B1B", "#F7FBF1", "#1A1C1A", "#EEF5E4", "#495B3D",
    )),
    Theme("a83e6b69-3c81-4d30-b4a4-b9401d5c2ddf", "Lavender Mist", leaf_price=0, is_premium=True, palette=_palette(
        "#673AB7", "#FFFFFF", "#E1BEE7", "#21005D", "#9575CD", "#FFFFFF",
        "#D1C4E9", "#311B92", "#E91E63", "#FFFFFF", "#F8BBD0", "#4A0072",
        "#FFFFFF", "#1C1B1F", "#F3E5F5", "#1D1B20", "#E7E0EC", "#49454F",
    )),
    Theme("6564ed5c-7ba7-4b90-be9a-c663716172cb", "Midnight Blue", leaf_price=0, is_premium=True, palette=_palette(
        "#7986CB", "#000000", "#283593", "#E8EAF6", "#5C6BC0", "#FFFFFF",
        "#1A237E", "#C5CAE9", "#FFAB40", "#000000", "#3E2723", "#FFD180",
        "#0D1117", "#E6EDF3", "#05070A", "#FFFFFF", "#1C2128", "#768390",
    )),
    Theme("967fdafd-1f29-44a2-995e-c51192083d8f", "Ocean Deep", leaf_price=0, is_premium=True, palette=_palette(
        "#00796B", "#FFFFFF", "#B2DFDB", "#00201A", "#0097A7", "#FFFFFF",
        "#E0F2F1", "#00363A", "#3F51B5", "#FFFFFF", "#C5CAE9", "#002B36",
        "#FFFFFF", "#191C1C", "#E0F2F1", "#191C1C", "#DAE5E3", "#3F4947",
    )),
    Theme("75fc58cd-c8ce-41f7-ae2d-8f6e36972df2", "Peach Sorbet", leaf_price=0, is_premium=True, palette=_palette(
        "#FF8A65", "#FFFFFF", "#FFCCBC", "#3E1000", "#D84315", "#FFFFFF",
        "#FBE9E7", "#4E342E", "#FBC02D", "#000000", "#FFF9C4", "#BF360C",
        "#FFFFFF", "#201A19", "#FFF3E0", "#201A19", "#F4DDDB", "#534341",
    )),
    Theme("0a1dcbc1-4f25-4c15-afe2-c5a0b072d895", "Rainy Cafe", leaf_price=0, is_premium=True, palette=_palette(
        "#607D8B", "#FFFFFF", "#CFD8DC", "#1A1C1E", "#455A64", "#FFFFFF",
        "#ECEFF1", "#263238", "#9E9E9E", "#000000", "#F5F5F5", "#263238",
        "#FFFFFF", "#1A1C1E", "#ECEFF1", "#1A1C1E", "#E0E2E5", "#43474E",
    )),
    Theme("6ea7ad34-1e36-407b-aee2-0c2d1f2d21eb", "Rose Garden", leaf_price=0, is_premium=True, palette=_palette(
        "#AD1457", "#FFFFFF", "#F8BBD0", "#3D001D", "#6A1B9A", "#FFFFFF",
        "#E1BEE7", "#2A004A", "#2E7D32", "#FFFFFF", "#C8E6C9", "#1B5E20",
        "#FFFFFF", "#201A1B", "#FCE4EC", "#201A1B", "#F4DDE0", "#514346",
    )),
    Theme("ee724c76-32b3-4f36-8b34-275719c7e8b4", "Sunlit Library", leaf_price=0, is_premium=True, palette=_palette(
        "#FFB300", "#000000", "#FFECB3", "#261900", "#795548", "#FFFFFF",
        "#D7CCC8", "#2D1600", "#827717", "#FFFFFF", "#F0F4C3", "#3E2723",
        "#FFFFFF", "#1E1B16", "#FFF8E1", "#1E1B16", "#ECE1D0", "#4E4639",
    )),
    Theme("b019bbd5-d904-4ae8-be16-927a1f3b28c3", "Sunset Glow", leaf_price=3000, is_premium=False, palette=_palette(
        "#FF6D00", "#FFFFFF", "#FFECB3", "#2B1700", "#D84315", "#FFFFFF",
        "#FFCCBC", "#3E1000", "#FFAB00", "#000000", "#FFE57F", "#FFFFFF",
        "#FFFFFF", "#201A19", "#FFF3E0", "#201A17", "#F4DDDB", "#534341",
    )),
)

PASSES: tuple[Pass, ...] = (
    Pass("pass_monthly", "Monthly Supporter Pass", "cozy_supporter:monthly"),
    Pass("pass_yearly", "Yearly Supporter Pass", "cozy_supporter:yearly"),
    Pass("pass_lifetime", "Lifetime Supporter Pass", "cozy_supporter_lifetime"),
)


def _sync_item(db: Session, item_id: str, category_id: str, name: str, leaf_price: int,
               is_premium: bool, iap_id: Optional[str]) -> None:
    values = dict(category_id=category_id, name=name, leaf_price=leaf_price, is_premium=is_premium, iap_id=iap_id)
    item = db.get(Item, item_id)
    if item is None:
        db.add(Item(id=item_id, **values))
    else:
        for key, value in values.items():
            setattr(item, key, value)


def sync_catalog(db: Session) -> None:
    for category_id, name in CATEGORIES.items():
        category = db.get(ItemCategory, category_id)
        if category is None:
            db.add(ItemCategory(id=category_id, name=name))
        else:
            category.name = name
    db.flush()

    for theme in THEMES:
        _sync_item(db, theme.id, "theme", theme.name, theme.leaf_price, theme.is_premium, None)
        db.flush()
        details = db.get(ThemeDetails, theme.id)
        if details is None:
            db.add(ThemeDetails(item_id=theme.id, **theme.palette))
        else:
            for role, color in theme.palette.items():
                setattr(details, role, color)

    for p in PASSES:
        _sync_item(db, p.id, "pass", p.name, 0, True, p.iap_id)

    db.commit()
