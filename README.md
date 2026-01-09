<p align="center">
  <img src="https://zimbabeats.com/images/zimbaBeats.png" width="120" alt="ZimbaBeats"/>
  <img src="https://zimbabeats.com/images/ZimbaBeatsFamily.png" width="120" alt="ZimbaBeats Family"/>
<h1 align="center">ZimbaBeats</h1>

<p align="center">
  <strong>Child-Safe YouTube for backend with Enterprise-Grade Parental Controls</strong>
</p>

<p align="center">
  <a href="https://github.com/raveuk/ZimbaBeats/releases/latest">
    <img src="https://img.shields.io/github/v/release/raveuk/ZimbaBeats?style=flat-square&logo=github" alt="Release"/>
  </a>
  <a href="https://github.com/raveuk/ZimbaBeats/releases">
    <img src="https://img.shields.io/github/downloads/raveuk/ZimbaBeats/total?style=flat-square&logo=android" alt="Downloads"/>
  </a>
  <a href="LICENSE">
    <img src="https://img.shields.io/badge/License-GPL%20v3.0-blue?style=flat-square" alt="License"/>
  </a>
  <a href="https://github.com/raveuk/ZimbaBeats/issues">
    <img src="https://img.shields.io/github/issues/raveuk/ZimbaBeats?style=flat-square" alt="Issues"/>
  </a>
</p>

---

## Overview
Our goal is to provide a worry-free experience for users of all ages, allowing them to watch videos and listen to music without exposure to inappropriate content. To achieve this, we have developed a separate, enterprise-grade parental control system designed to address the gaps where many existing music and video platforms fall short.

<b>This project is currently in its first conceptual stage. We welcome community support and donations to help further develop and strengthen the parental control engine.<b>

ZimbaBeats is a purpose-built Android application designed to provide children with a safe, advertisement-free YouTube viewing experience. The application operates in conjunction with the ZimbaBeats Family companion application, enabling parents and guardians to maintain comprehensive oversight and control of their children's media consumption.

The application is built using modern Android development practices, including Jetpack Compose for the user interface and Material Design 3 for visual consistency. All streaming functionality operates without advertisements or tracking mechanisms.

## Screenshots    
 <p align="center">          
 <img src="?raw=true" width="200" />          
  <img src="?raw=true" width="200" />          
   <img src="?raw=true" width="200" />          
   <img src="?raw=true" width="200" /> </p> <p align="center">          

</p>

## Issue Reporting

**All bugs, defects, and issues must be reported immediately.**

Timely reporting of issues is essential for maintaining application stability and security. Users who encounter any problems are required to submit detailed reports through the official issue tracking system.

### Reporting Procedure

1. Navigate to [GitHub Issues](https://github.com/raveuk/ZimbaBeats/issues)
2. Select "New Issue"
3. Complete the bug report template with the following information:
   - Device manufacturer and model
   - Android version
   - Application version
   - Detailed description of the issue
   - Steps to reproduce the problem
   - Screenshots or screen recordings (if applicable)
   - Error messages or crash logs (if available)

<p align="center">
  <a href="https://github.com/raveuk/ZimbaBeats/issues/new">
    <img src="https://img.shields.io/badge/Submit%20Issue-critical?style=for-the-badge&logo=github" alt="Submit Issue"/>
  </a>
</p>

> **Notice:** Failure to report known issues may result in degraded experience for all users. Your cooperation in maintaining application quality is appreciated.

---


## Features

### Core Functionality

| Feature | Description |
|---------|-------------|
| Advertisement-Free Playback | Complete removal of all advertisements during video playback |
| Background Audio | Continued audio playback when application is minimized |
| Offline Storage | Download capability for offline viewing |
| Content Library | Personal playlist and favorites management |
| Search | Filtered search functionality with safe-search enforcement |
| Music Integration | Full YouTube Music catalog access with dedicated player - Thanks to Youtube |

### Playlist Sharing (New in v1.0.9)

| Feature | Description |
|---------|-------------|
| Share Codes | Share playlists with friends using simple 6-character codes |
| Import Playlists | Import shared playlists from friends with one tap |
| Content Filtering | Blocked content automatically removed during import |
| Expiry & Limits | Codes expire after 7 days with max 10 imports |
| Parent Visibility | Parents can see shared playlists and who imported them |
| Revoke Sharing | Parents can revoke share codes at any time |

### Global Content Filtering

| Feature | Description |
|---------|-------------|
|  Global Filtering |  Dangerous or harmful content is blocked platform-wide by the ZimbaBeats team. No app update required - blocks apply instantly. |

### Parental Control Features

| Feature | Description |
|---------|-------------|
| Cloud Synchronization | Real-time settings synchronization between parent and child devices |
| Screen Time Management | Configurable daily usage limits with automatic enforcement |
| Bedtime Scheduling | Time-based access restrictions |
| Age-Based Filtering | Content restriction based on age rating classifications |


### Premium Content Filtering

| Feature | Description |
|---------|-------------|
| Keyword Blocking | Automatic blocking of content containing specified terms |
| Channel Blocking | Complete exclusion of content from specified channels |
| Category Restrictions | Blocking of entire content categories (Gaming, ASMR, Vlogs, etc.) |
| Whitelist Mode | Restriction to approved channels only |
| Safe Search Enforcement | Filtering of explicit content from search results |
| Strict Mode | Restriction to verified child-friendly content only |
| Live Stream Blocking | Prevention of access to live streaming content |
| Comment Hiding | Removal of comment sections from all videos |
| Duration Limits | Maximum video length restrictions |

### Activity Monitoring

| Feature | Description |
|---------|-------------|
| Watch History | Complete viewing history accessible to parents |
| Block Notifications | Real-time alerts when restricted content is accessed |
| Usage Analytics | Daily and weekly viewing statistics |

---

## System Requirements

| Requirement | Specification |
|-------------|---------------|
| Operating System | Android 7.0 (API Level 24) or higher |
| Storage | Minimum 100 MB available space |
| Network | Active internet connection required for streaming |
| Architecture | ARM64-v8a, ARMv7, x86_64 |

---

## Installation

### ZimbaBeats (Child Application)

**License:** GNU General Public License v3.0 (Open Source)

<a href="https://github.com/raveuk/ZimbaBeats/releases/download/v1.0.11/ZimbaBeats.apk">
  <img src="https://img.shields.io/badge/Download-ZimbaBeats-blue?style=for-the-badge&logo=android" alt="Download ZimbaBeats"/>
</a>

### ZimbaBeats Family (Parent Application)

**License:** Proprietary - All Rights Reserved

<a href="https://github.com/raveuk/ZimbaBeats/releases/download/v1.0.11/ZimbaBeats-Family.apk">
  <img src="https://img.shields.io/badge/Download-ZimbaBeats%20Family-green?style=for-the-badge&logo=android" alt="Download ZimbaBeats Family"/>
</a>

---

## Parental Control System

### Architecture

```
+------------------------+                    +------------------------+
|    ZimbaBeats Family   |                    |       ZimbaBeats       |
|   (Parent Device)      |                    |    (Child Device)      |
|                        |                    |                        |
|  - Configure Settings  |                    |  - Receive Settings    |
|  - Monitor Activity    |<------------------>|  - Enforce Limits      |
|  - View History        |   Cloud Sync       |  - Report Activity     |
|  - Block Content       |   (Real-time)      |  - Apply Filters       |
+------------------------+                    +------------------------+
```

### Setup Instructions

1. Install **ZimbaBeats Family** on the parent/guardian device
2. Install **ZimbaBeats** on the child's device
3. Create an account in ZimbaBeats Family (email/password or Google Sign-In)
4. Navigate to **Linked Devices** and tap **Add Device** to generate a pairing code 
5. On the child's device, enter the pairing code during initial setup, or later via **Settings > Parental Controls > Link to Family**
6. Configure parental control settings and content filters as required
7. Monitor activity and manage devices through the ZimbaBeats Family dashboard

### Pairing Code Specifications

| Parameter | Value |
|-----------|-------|
| Format | 6-character alphanumeric |
| Validity Period | 24 hours |
| Usage | Single-use only |
| Security | Encrypted transmission |

### Device Management

Parents can manage linked child devices from the ZimbaBeats Family app:

| Feature | Description |
|---------|-------------|
| Device List | View all linked devices with device model names |
| Online Status | Real-time online/offline status indicators |
| Last Seen | Timestamp of last device activity |
| Remote Unlink | Remove devices from your family at any time |

### Remote Unlink Detection

When a parent removes a child device from their family account:

- Child device detects removal in real-time
- Notification dialog informs the child that parental controls are no longer active
- Local pairing data is automatically cleared
- Child can be re-linked using a new pairing code

---

## Technical Specifications

### Technology Stack

| Component | Technology |
|-----------|------------|
| Language | Kotlin |
| UI Framework | Jetpack Compose |
| Design System | Material Design 3 |
| Dependency Injection | Koin |
| Local Database | Room |
| Media Playback | ExoPlayer |
| Image Loading | Coil |
| Cloud Services | Secure Cloud Infrastructure |
| Asynchronous Operations | Kotlin Coroutines, Flow |
| Architecture Pattern | MVVM (Model-View-ViewModel) |

---

## Building from Source

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or later
- Java Development Kit 17
- Android SDK 34
- Gradle 8.0 or later

### Build Instructions

```bash
# Clone the repository
git clone https://github.com/raveuk/ZimbaBeats.git

# Navigate to project directory
cd ZimbaBeats

# Build debug variant
./gradlew assembleDebug

# Build release variant
./gradlew assembleRelease
```

### Build Outputs

| Variant | Location |
|---------|----------|
| Debug | `app/build/outputs/apk/debug/app-debug.apk` |
| Release | `app/build/outputs/apk/release/app-release.apk` |

---

## Contributing

Contributions to ZimbaBeats are welcome. Please adhere to the following procedures:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/description`)
3. Implement changes with appropriate documentation
4. Ensure all existing tests pass
5. Submit a pull request with a comprehensive description of changes

---

## Support

If you find ZimbaBeats useful, consider supporting the development:

<a href="https://buymeacoffee.com/zimbabeats">
  <img src="https://img.shields.io/badge/Buy%20Me%20a%20Coffee-support-yellow?style=for-the-badge&logo=buy-me-a-coffee" alt="Buy Me a Coffee"/>
</a>

---

## License

### ZimbaBeats (Child App)

```
ZimbaBeats - Child-Safe
Copyright (C) 2025 ZimbaBeats

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program. If not, see <https://www.gnu.org/licenses/>.
```

### ZimbaBeats Family (Parent App)

This software application, including all source code, object code, documentation,
design elements, user interface components, algorithms, and associated intellectual
property, is the exclusive property of ZimbaBeats.

ZimbaBeatsFamily has been developed entirely from original work. No source code,
libraries, or components from third-party open source projects have been
incorporated into this application. All code is original and proprietary.

## ZimbaBeats Family - Parental Control Application
Copyright (C) 2025 ZimbaBeats
All Rights Reserved

This software is proprietary and confidential.
Unauthorized copying, distribution, or use is strictly prohibited.
```

---

## Privacy

ZimbaBeats is committed to protecting user privacy:

- **No Advertisements** - Zero advertising content or tracking
- **No Analytics Collection** - No data transmitted to third-party services
- **Local Data Storage** - Preferences and history stored locally
- **Encrypted Sync** - Parent-child synchronization uses encryption
- **Minimal Permissions** - Only essential permissions requested

---

## Contact

- **Issues:** [GitHub Issues](https://github.com/raveuk/ZimbaBeats/issues)
- **Support:** [Buy Me a Coffee](https://buymeacoffee.com/zimbabeats)

---

<p align="center">
  <sub>Copyright 2025 ZimbaBeats. All rights reserved where applicable.</sub>
</p>

