import json
import logging
from typing import Dict, Set
from fastapi import WebSocket

logger = logging.getLogger("safety_manager")

class SafetyManager:
    def __init__(self):
        # user_id -> set of active WebSockets
        self.user_connections: Dict[str, Set[WebSocket]] = {}

    def clear(self):
        """Used in test teardowns to reset active safety sockets."""
        self.user_connections.clear()

    async def connect(self, user_id: str, websocket: WebSocket):
        await websocket.accept()
        if user_id not in self.user_connections:
            self.user_connections[user_id] = set()
        self.user_connections[user_id].add(websocket)

    def disconnect(self, user_id: str, websocket: WebSocket):
        if user_id in self.user_connections:
            self.user_connections[user_id].discard(websocket)
            if not self.user_connections[user_id]:
                del self.user_connections[user_id]

    async def notify_block_change(self, user_id: str, event_data: dict):
        """
        Pushes a real-time event to the specified user's open sockets.
        """
        if user_id in self.user_connections:
            msg = json.dumps(event_data)
            dead_sockets = set()
            for ws in list(self.user_connections[user_id]):
                try:
                    await ws.send_text(msg)
                except Exception:
                    dead_sockets.add(ws)
            for dead in dead_sockets:
                self.disconnect(user_id, dead)

safety_manager = SafetyManager()
