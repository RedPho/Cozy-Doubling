from sqlalchemy import create_engine
from sqlalchemy.orm import Session

from app.database import Base
from app.models.shop import Item
from app.seed import seed_catalog_if_empty


def test_pass_product_ids_match_google_play():
    """iap_ids must equal the RevenueCat product IDs live since 1.1.0, or the Shop hides the passes."""
    engine = create_engine("sqlite:///:memory:")
    Base.metadata.create_all(bind=engine)
    with Session(engine) as db:
        seed_catalog_if_empty(db)
        db.commit()
        ids = {item.id: item.iap_id for item in db.query(Item).filter(Item.category_id == "pass")}
    assert ids == {
        "pass_monthly": "cozy_supporter:monthly",
        "pass_yearly": "cozy_supporter:yearly",
        "pass_lifetime": "cozy_supporter_lifetime",
    }
