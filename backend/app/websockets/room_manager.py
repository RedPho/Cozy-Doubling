import json
import logging
from typing import Dict, Any, Set
from fastapi import WebSocket
from app.config import settings

logger = logging.getLogger("room_manager")

class RoomParticipantEntry:
    def __init__(self, user_id: str, presence_key: str, websocket: WebSocket, presence_data: Dict[str, Any]):
        self.user_id = user_id
        self.presence_key = presence_key
        self.websocket = websocket
        self.presence_data = presence_data

class FocusRoomManager:
    def __init__(self):
        # presence_key -> RoomParticipantEntry
        self.active_participants: Dict[str, RoomParticipantEntry] = {}

    def is_full(self) -> bool:
        return len(self.active_participants) >= settings.ROOM_CAPACITY_LIMIT

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
        entry = RoomParticipantEntry(
            user_id=user_id,
            presence_key=presence_key,
            websocket=websocket,
            presence_data=initial_presence
        )
        self.active_participants[presence_key] = entry

        # 1. Send current participants to the newly connected user (filtering blocked participants)
        other_participants = [
            p.presence_data for k, p in self.active_participants.items()
            if k != presence_key and p.user_id not in user_blocked_ids
        ]
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
            entry.presence_data = presence_data
            await self.broadcast_update(presence_key, presence_data, entry.user_id)

    async def broadcast_update(self, sender_key: str, presence_data: Dict[str, Any], sender_user_id: str):
        message = json.dumps({
            "type": "presence_update",
            "presence": presence_data
        })
        disconnected_keys = []
        for key, entry in self.active_participants.items():
            if key == sender_key:
                continue
            try:
                await entry.websocket.send_text(message)
            except Exception as e:
                logger.warning(f"Error broadcasting to {key}: {e}")
                disconnected_keys.append(key)
        
        for k in disconnected_keys:
            self.disconnect(k)

    async def disconnect(self, presence_key: str):
        if presence_key in self.active_participants:
            entry = self.active_participants.pop(presence_key)
            leave_msg = json.dumps({
                "type": "presence_leave",
                "presence_id": entry.presence_data.get("id", presence_key)
            })
            for key, other in list(self.active_participants.items()):
                try:
                    await other.websocket.send_text(leave_msg)
                except Exception:
                    pass

focus_room_manager = FocusRoomManager()
