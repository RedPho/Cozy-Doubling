import logging
from typing import List

logger = logging.getLogger("notifications")

async def send_friend_focus_notification(sender_name: str, friend_fcm_tokens: List[str]):
    """
    Dispatches push notifications to accepted friends upon entering the focus room.
    """
    if not friend_fcm_tokens:
        return
    
    title = "Friend Focusing!"
    body = f"{sender_name} just entered the focus room." if sender_name else "A friend just entered the focus room."
    
    # In production with FIREBASE_SERVICE_ACCOUNT, call firebase_admin.messaging.send_each()
    logger.info(f"Dispatching FCM Push: Title='{title}', Body='{body}' to {len(friend_fcm_tokens)} devices")
