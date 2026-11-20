from typing import Optional, List
from pydantic import BaseModel

class ThemePaletteSchema(BaseModel):
    primary: str
    on_primary: str
    primary_container: str
    on_primary_container: str
    secondary: str
    on_secondary: str
    secondary_container: str
    on_secondary_container: str
    tertiary: str
    on_tertiary: str
    tertiary_container: str
    on_tertiary_container: str
    surface: str
    on_surface: str
    background: str
    on_background: str
    surface_variant: str
    on_surface_variant: str

class ShopItemResponse(BaseModel):
    id: str
    name: str
    category_id: str
    is_premium: bool
    leaf_price: int
    is_owned: bool
    is_equipped: bool
    iap_id: Optional[str] = None
    palette: Optional[ThemePaletteSchema] = None

class BuyItemRequest(BaseModel):
    item_id: str

class EquipItemRequest(BaseModel):
    theme_id: str
