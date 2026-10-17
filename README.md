# ReelShield

**An Android experiment in reducing short-form video scrolling without blocking the rest of a social app.**

ReelShield is a personal productivity project built around a simple idea: make it easier to leave short-form video feeds while keeping ordinary app features—such as messaging and regular posts—available.

## Current status

| App / surface | Status |
| --- | --- |
| Instagram Reels | Tested on a physical Android device; overlay behavior and leaving Instagram tested |
| YouTube Shorts | Previously tested as working |
| Snapchat Spotlight | Not currently verified; support is still being worked on |
| Facebook Reels | Planned / not verified |

Compatibility can change when social apps update their interfaces. The table reflects testing done so far, not a guarantee for every device or app version.

## How it works

- Uses Android's **Accessibility Service** to observe relevant interface events.
- Detects supported short-form video surfaces and displays a blocker overlay.
- Aims to preserve normal use of the surrounding app rather than blocking the entire app.
- Avoids continuous polling and uses scheduled scans to limit unnecessary work.

The implementation is an experiment, so detection may need adjustment as target apps change.

## Tech

- **Platform:** Android
- **Language:** Java
- **IDE / build:** Android Studio and Gradle
- **Core API:** Android Accessibility Service

## Build and try it

1. Clone this repository and open it in Android Studio.
2. Let Gradle sync and build the project.
3. Install the debug build on an Android device.
4. Open ReelShield and enable its Accessibility Service in Android Settings.
5. Test each target app and navigation path independently.

You may need to accept Android's accessibility warning. Only enable the service if you understand and trust what the app does.

## Privacy and limitations

ReelShield relies on accessibility events and interface information to recognize target surfaces. Review the source code and test the behavior on your own device before using or distributing a build. This repository does not claim complete detection across every app version, device, or screen.

## Why I built it

I wanted to experiment with a practical way to reduce my own short-form scrolling without losing access to useful parts of social apps. The project has been a way to learn Android accessibility APIs, event-driven UI behavior, debugging, and testing against real apps.

## Contributing / feedback

Bug reports and reproducible steps are useful—especially the device model, Android version, target app/version, and the navigation steps that triggered the issue. Please avoid including private messages or other personal data in reports.
