import re

import pytest
from sqlalchemy import create_engine
from sqlalchemy.orm import Session

from app.database import Base
from app.models.shop import Item, ThemeDetails
from app.seed import COLOR_ROLES, sync_catalog

# The eleven 1.1.0 themes keep their Supabase item IDs so imported ownership carries over.
LEGACY_THEME_IDS = {
    "1e1b0991-5e8a-431f-a83d-3cfa967ece5a",  # Arctic Frost
    "e2bf4131-ebd3-47ed-ac89-ab5260f45841",  # Cozy Night
    "c8c7d699-944c-4420-8925-8ee29aa62e0b",  # Forest Moss
    "a83e6b69-3c81-4d30-b4a4-b9401d5c2ddf",  # Lavender Mist
    "6564ed5c-7ba7-4b90-be9a-c663716172cb",  # Midnight Blue
    "967fdafd-1f29-44a2-995e-c51192083d8f",  # Ocean Deep
    "75fc58cd-c8ce-41f7-ae2d-8f6e36972df2",  # Peach Sorbet
    "0a1dcbc1-4f25-4c15-afe2-c5a0b072d895",  # Rainy Cafe
    "6ea7ad34-1e36-407b-aee2-0c2d1f2d21eb",  # Rose Garden
    "ee724c76-32b3-4f36-8b34-275719c7e8b4",  # Sunlit Library
    "b019bbd5-d904-4ae8-be16-927a1f3b28c3",  # Sunset Glow
}


@pytest.fixture()
def db():
    engine = create_engine("sqlite:///:memory:")
    Base.metadata.create_all(bind=engine)
    with Session(engine) as session:
        sync_catalog(session)
        yield session


def test_pass_product_ids_match_google_play(db):
    """iap_ids must equal the RevenueCat product IDs live since 1.1.0, or the Shop hides the passes."""
    ids = {item.id: item.iap_id for item in db.query(Item).filter(Item.category_id == "pass")}
    assert ids == {
        "pass_monthly": "cozy_supporter:monthly",
        "pass_yearly": "cozy_supporter:yearly",
        "pass_lifetime": "cozy_supporter_lifetime",
    }


def test_legacy_themes_are_back_with_full_palettes(db):
    themes = {item.id: item for item in db.query(Item).filter(Item.category_id == "theme")}
    assert LEGACY_THEME_IDS <= set(themes)
    assert {"system_default", "theme_peach_blossom", "theme_midnight_velvet"} <= set(themes)
    assert len(themes) == 14
    for theme_id in themes:
        details = db.get(ThemeDetails, theme_id)
        assert details is not None, theme_id
        for role in COLOR_ROLES:
            assert re.fullmatch(r"#[0-9A-F]{6}", getattr(details, role)), (theme_id, role)


def test_sync_updates_an_existing_catalog_without_duplicates(db):
    monthly = db.get(Item, "pass_monthly")
    monthly.iap_id = "cozy_supporter_monthly"  # the wrong ID an older database was seeded with
    db.get(ThemeDetails, "system_default").primary = "#000000"
    db.commit()

    sync_catalog(db)
    sync_catalog(db)

    assert db.get(Item, "pass_monthly").iap_id == "cozy_supporter:monthly"
    assert db.get(ThemeDetails, "system_default").primary == "#7A8471"
    assert db.query(Item).count() == 17  # 14 themes + 3 passes
