import json
import logging
from typing import Dict, Any, Set, Optional
from fastapi import WebSocket
from app.config import settings

logger = logging.getLogger("room_manager")

class RoomParticipantEntry:
    def __init__(
        self,
        user_id: str,
        presence_key: str,
        websocket: WebSocket,
        presence_data: Dict[str, Any],
        blocked_ids: Set[str]
    ):
        self.user_id = user_id
        self.presence_key = presence_key
        self.websocket = websocket
        self.presence_data = presence_data
        self.blocked_ids = blocked_ids

class FocusRoomManager:
    def __init__(self):
        # presence_key -> RoomParticipantEntry
        self.active_participants: Dict[str, RoomParticipantEntry] = {}

    def is_full(self) -> bool:
        return len(self.active_participants) >= settings.ROOM_CAPACITY_LIMIT

    def clear(self):
        """Used in test teardowns to reset active participants."""
        self.active_participants.clear()

    async def connect(
        self,
        presence_key: str,
        user_id: str,
        websocket: WebSocket,
        initial_presence: Dict[str, Any],
        user_blocked_ids: Set[str]
    ) -> bool:
        if self.is_full() and presence_key not in self.active_participants:
            await websocket.close(code=1008, reason="Room capacity limit reached")
            return False

        await websocket.accept()

        # If a connection with the exact same presence_key already existed, replace it cleanly
        if presence_key in self.active_participants:
            old_entry = self.active_participants.pop(presence_key)
            try:
                await old_entry.websocket.close(code=1000, reason="Replaced by new connection")
            except Exception:
                pass

        entry = RoomParticipantEntry(
            user_id=user_id,
            presence_key=presence_key,
            websocket=websocket,
            presence_data=initial_presence,
            blocked_ids=user_blocked_ids
        )
        self.active_participants[presence_key] = entry

        # 1. Send current participants to newly connected user (filtering blocked participants both ways)
        other_participants = [
            p.presence_data for k, p in list(self.active_participants.items())
            if k != presence_key and p.user_id not in user_blocked_ids and user_id not in p.blocked_ids
        ]
        logger.info(
            f"Connected room socket: {presence_key}. Active: {list(self.active_participants.keys())}. "
            f"Sending state with {len(other_participants)} other participants."
        )
        await websocket.send_text(json.dumps({
            "type": "presence_state",
            "participants": other_participants
        }))

        # 2. Broadcast the new participant to everyone else who hasn't blocked them
        await self.broadcast_update(presence_key, initial_presence, user_id)
        return True

    async def update_presence(self, presence_key: str, presence_data: Dict[str, Any]):
        if presence_key in self.active_participants:
            entry = self.active_participants[presence_key]
            canonical_presence = {**presence_data, "id": presence_key}
            entry.presence_data = canonical_presence
            logger.info(f"Updated presence for {presence_key}: {canonical_presence.get('name')}")
            await self.broadcast_update(presence_key, canonical_presence, entry.user_id)

    async def broadcast_update(self, sender_key: str, presence_data: Dict[str, Any], sender_user_id: str):
        message = json.dumps({
            "type": "presence_update",
            "presence": presence_data
        })
        sender_entry = self.active_participants.get(sender_key)
        sender_blocked = sender_entry.blocked_ids if sender_entry else set()

        disconnected_items = []
        recipients = [
            (key, entry) for key, entry in list(self.active_participants.items())
            if key != sender_key
            and sender_user_id not in entry.blocked_ids
            and entry.user_id not in sender_blocked
        ]

        logger.info(f"Broadcasting presence_update from {sender_key} to {len(recipients)} other participant(s).")

        for key, entry in recipients:
            try:
                await entry.websocket.send_text(message)
            except Exception as e:
                logger.warning(f"Error broadcasting to {key}: {type(e).__name__}: {e}")
                disconnected_items.append((key, entry.websocket))

        for k, ws in disconnected_items:
            await self.disconnect(k, websocket=ws)

    async def disconnect(self, presence_key: str, websocket: Optional[WebSocket] = None):
        if presence_key in self.active_participants:
            current_entry = self.active_participants[presence_key]
            # Ignore disconnect if this is from a replaced/stale socket
            if websocket is not None and current_entry.websocket != websocket:
                logger.info(f"Ignoring stale disconnect event for {presence_key}")
                return

            entry = self.active_participants.pop(presence_key)
            logger.info(f"Disconnected {presence_key}. Remaining active: {list(self.active_participants.keys())}")
            leave_msg = json.dumps({
                "type": "presence_leave",
                "presence_id": entry.presence_data.get("id", presence_key)
            })

            for key, other in list(self.active_participants.items()):
                if entry.user_id in other.blocked_ids or other.user_id in entry.blocked_ids:
                    continue
                try:
                    await other.websocket.send_text(leave_msg)
                except Exception:
                    pass

    async def handle_user_blocked(self, blocker_id: str, blocked_id: str):
        """Immediately separates two users in the room if a block occurs."""
        for key, entry in list(self.active_participants.items()):
            if entry.user_id == blocker_id:
                entry.blocked_ids.add(blocked_id)
                for other_key, other in list(self.active_participants.items()):
                    if other.user_id == blocked_id:
                        try:
                            await entry.websocket.send_text(json.dumps({
                                "type": "presence_leave",
                                "presence_id": other.presence_data.get("id", other_key)
                            }))
                        except Exception:
                            pass
            elif entry.user_id == blocked_id:
                entry.blocked_ids.add(blocker_id)
                for other_key, other in list(self.active_participants.items()):
                    if other.user_id == blocker_id:
                        try:
                            await entry.websocket.send_text(json.dumps({
                                "type": "presence_leave",
                                "presence_id": other.presence_data.get("id", other_key)
                            }))
                        except Exception:
                            pass

focus_room_manager = FocusRoomManager()
