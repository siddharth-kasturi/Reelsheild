# ReelShield

**An Android experiment for reducing short-form video scrolling without blocking an entire social app.**

ReelShield is a personal productivity project built around a practical question: can a user make short-form video feeds harder to access while keeping useful parts of the same social apps available?

> **Project status:** Experimental student project. Compatibility varies by app version, device, and navigation path. Some behavior has been tested on a physical Android device, but this is not a guarantee of reliable operation on every setup.

## What it does

- Uses Android's **Accessibility Service** to observe selected interface events and inspect visible UI elements in supported apps.
- Attempts to recognize supported short-form video destinations and show a blocking overlay.
- Aims to leave other parts of the host app available rather than blocking the whole app.
- Uses event-driven checks and scheduled scans to limit repeated work.

**Technical clarification:** ReelShield does not use browser DOM filtering or network-request filtering. Its current implementation is an Android app built around the Accessibility Service API.

## Compatibility and testing status

| App / surface | Status documented by the developer |
| --- | --- |
| Instagram Reels | Tested on a physical Android device; overlay behavior and leaving Instagram were tested |
| YouTube Shorts | Previously tested as working; current compatibility is not guaranteed |
| Snapchat Spotlight | Not currently verified; support remains experimental |
| Facebook Reels | Not currently verified |

Social apps frequently change their interfaces. Detection may stop working after an app update, and behavior can differ across devices. These statuses reflect the developer's testing notes, not independent verification.

## How it works

1. The Android Accessibility Service receives selected interface events.
2. ReelShield checks supported app UI and navigation information for patterns associated with short-form video surfaces.
3. When a supported destination is recognized, the app attempts to display a blocking overlay.
4. It manages state and schedules follow-up scans to handle some interface transitions.

The implementation is heuristic and may misidentify screens or miss a feed. Review the source code to understand its current behavior.

## Technology

- **Platform:** Android
- **Language:** Java
- **Build system:** Gradle
- **Android Gradle Plugin:** 8.7.3
- **Compile SDK:** 35
- **Minimum SDK:** 26 (Android 8.0)
- **Core API:** Android Accessibility Service

## Build and try it

### Requirements

- Android Studio with support for the project's Android Gradle Plugin
- JDK 17 recommended
- An Android device running Android 8.0 (API 26) or later

### Steps

1. Clone or download this repository.
2. Open the project root in Android Studio.
3. Allow Gradle to sync.
4. Build and install the debug app on your Android device.
5. Open ReelShield and follow the in-app instructions to enable its Accessibility Service in Android Settings.
6. Test each supported app and navigation path independently.

The repository includes the Gradle wrapper. From a terminal on Windows, you can also try:

```powershell
.\gradlew.bat assembleDebug
```

On macOS or Linux:

```bash
./gradlew assembleDebug
```

These are build instructions, not a claim that the current commit has passed a fresh build in every environment. If a build fails, record the complete error message, Java version, and Android Studio/Gradle versions before troubleshooting.

## Accessibility and privacy

ReelShield uses Android's Accessibility Service API to inspect selected interface events and UI information in supported apps. That access is central to how the prototype attempts to identify short-form video screens.

- Enable the service only if you understand and trust what it can access.
- Review the source code and test the app on your own device before relying on it.
- This repository does not provide independent security or privacy auditing.
- Do not submit private messages, account details, or other sensitive information in bug reports.

ReelShield is a productivity experiment, not a medical or clinical intervention, and this repository does not establish that it reduces screen time or improves attention.

## Known limitations

- App interface updates can break detection.
- Compatibility is not verified across all devices, Android versions, app versions, or navigation paths.
- Some listed app support is experimental or unverified.
- The project has not been independently audited.
- The README's testing notes are developer-reported; they are not a formal benchmark.

## Why I built it

I wanted to experiment with reducing my own short-form video scrolling without losing access to useful parts of social apps. Building ReelShield has helped me learn about Android accessibility APIs, event-driven UI behavior, debugging, and testing against real applications.

## Bug reports and contributions

Feedback is welcome. When reporting a problem, include (if safe to share):

- Device model and Android version
- Target app and app version
- The navigation steps that caused the issue
- What you expected and what happened instead
- Relevant error logs with personal information removed

Please do not include private messages, account details, or other sensitive data in issues.

## License

See [LICENSE](LICENSE) for the terms governing use, modification, and distribution of this project.
