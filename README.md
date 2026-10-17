# ReelShield v1.7 — Smooth Multi-App + Instagram DM Fix

ReelShield is an Android productivity app that helps reduce short-form video scrolling while keeping normal app functionality accessible.

## Supported apps

- **Instagram:** Reels tab and confirmed inline Reels on Home, including Reels opened from chats.
- **YouTube:** Shorts tab and confirmed Shorts surfaces.
- **Snapchat:** Spotlight tab and confirmed Spotlight surfaces.
- **Facebook and Facebook Lite:** Reels tab and confirmed Reel surfaces.

## Instagram DM Reel handling

ReelShield treats a Reel opened from a chat as a protected Reel-viewing session. It avoids clearing the blocker merely because Instagram exposes the underlying messaging interface. Back and Home remain usable because the overlay is visual-only.

## Performance and detection

- Blocks supported short-form navigation through accessibility events.
- Avoids continuous polling.
- Coalesces accessibility content and scroll events.
- Limits accessibility-tree scans.
- Clears the overlay when a safe destination is detected.
- Preserves existing Instagram Home inline-Reel detection behavior.

## Setup

1. Open the project in Android Studio.
2. Build and install the debug APK.
3. Open ReelShield and enable its Accessibility Service.
4. Test each supported app independently.

Social apps may change their accessibility layouts, so detector adjustments may be needed after app updates.

## Privacy and accessibility

ReelShield uses Android's Accessibility Service to detect supported short-form video interfaces. Review the implementation and test its behavior on your own device before distributing the app.