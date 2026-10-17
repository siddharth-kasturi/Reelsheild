# ReelShield v1.7 - Smooth Multi-App + Instagram DM Fix

ReelShield blocks supported short-form video surfaces without continuously polling or repeatedly walking large accessibility trees.

## Supported apps
- Instagram: Reels tab, confirmed inline Reels in Home, and Reels opened from DMs/chats.
- YouTube: Shorts tab and confirmed Shorts surfaces.
- Snapchat: Spotlight tab and confirmed Spotlight surfaces.
- Facebook and Facebook Lite: Reels tab and confirmed Reel surfaces.

## Instagram DM Reel fix
A Reel opened from a chat is treated as a protected Reel session. The blocker is not cleared merely because Instagram exposes the underlying Messages tree. A strong Reel-viewer signature combined with a messaging screen also starts the protected session even when the original click event exposes no useful Reel label. Back/Home remain usable because the overlay is visual-only.

## Smoothness
- Short-form navigation is blocked immediately from navigation-click events.
- No continuous polling loop.
- Accessibility content/scroll events are coalesced.
- Accessibility tree scans are capped.
- Safe destinations clear the overlay immediately.
- Instagram Home keeps its existing inline-Reel detection/skip behavior from v1.6.

## Setup
1. Open the project in Android Studio.
2. Build/install the debug APK.
3. Open ReelShield and enable its Accessibility Service.
4. Test each supported app independently.

Social apps change their accessibility layouts frequently, so detector adjustments may be needed after app updates.
