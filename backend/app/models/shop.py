import datetime
from sqlalchemy import Column, String, Integer, Boolean, DateTime, ForeignKey
from sqlalchemy.orm import relationship
from app.database import Base

class ItemCategory(Base):
    __tablename__ = "item_categories"

    id = Column(String, primary_key=True)  # 'theme', 'pass'
    name = Column(String, nullable=False)

    items = relationship("Item", back_populates="category")

class Item(Base):
    __tablename__ = "items"

    id = Column(String, primary_key=True)
    category_id = Column(String, ForeignKey("item_categories.id", ondelete="CASCADE"), nullable=False)
    name = Column(String, nullable=False)
    leaf_price = Column(Integer, default=0, nullable=False)
    is_premium = Column(Boolean, default=False, nullable=False)
    iap_id = Column(String, nullable=True)

    category = relationship("ItemCategory", back_populates="items")
    theme_details = relationship("ThemeDetails", back_populates="item", uselist=False, cascade="all, delete-orphan")

class ThemeDetails(Base):
    __tablename__ = "theme_details"

    item_id = Column(String, ForeignKey("items.id", ondelete="CASCADE"), primary_key=True)
    primary = Column(String, nullable=False)
    on_primary = Column(String, nullable=False)
    primary_container = Column(String, nullable=False)
    on_primary_container = Column(String, nullable=False)
    secondary = Column(String, nullable=False)
    on_secondary = Column(String, nullable=False)
    secondary_container = Column(String, nullable=False)
    on_secondary_container = Column(String, nullable=False)
    tertiary = Column(String, nullable=False)
    on_tertiary = Column(String, nullable=False)
    tertiary_container = Column(String, nullable=False)
    on_tertiary_container = Column(String, nullable=False)
    surface = Column(String, nullable=False)
    on_surface = Column(String, nullable=False)
    background = Column(String, nullable=False)
    on_background = Column(String, nullable=False)
    surface_variant = Column(String, nullable=False)
    on_surface_variant = Column(String, nullable=False)

    item = relationship("Item", back_populates="theme_details")

class UserInventory(Base):
    __tablename__ = "user_inventory"

    user_id = Column(String, ForeignKey("users.id", ondelete="CASCADE"), primary_key=True)
    item_id = Column(String, ForeignKey("items.id", ondelete="CASCADE"), primary_key=True)
    acquired_at = Column(DateTime, default=datetime.datetime.utcnow, nullable=False)

    item = relationship("Item")
