# ZimbaBeats User Guide

A complete guide to setting up and using ZimbaBeats for child-safe YouTube viewing with parental controls.

---

## Table of Contents

1. [Overview](#overview)
2. [Installation](#installation)
3. [Parent App Setup](#parent-app-setup)
4. [Child App Setup](#child-app-setup)
5. [Linking Parent and Child Devices](#linking-parent-and-child-devices)
6. [Managing Children Profiles](#managing-children-profiles)
7. [Content Filtering](#content-filtering)
8. [Music Filtering](#music-filtering)
9. [Screen Time & Bedtime](#screen-time--bedtime)
10. [Monitoring Activity](#monitoring-activity)
11. [Playlist Sharing](#playlist-sharing)
12. [Troubleshooting](#troubleshooting)

---

## Overview

ZimbaBeats consists of two applications:

| App | Purpose | Install On |
|-----|---------|------------|
| **ZimbaBeats** | Child-safe YouTube player | Child's device |
| **ZimbaBeats Family** | Parental control dashboard | Parent's device |

### Key Features

- Advertisement-free video and music playback
- Real-time parental controls synced via cloud
- Age-based content filtering
- Per-child profiles with individual settings
- Screen time limits and bedtime scheduling
- Music whitelist/blocklist filtering
- Activity monitoring and alerts

---

## Installation

### Download Links

- **ZimbaBeats (Child App)**: [Download APK](https://github.com/raveuk/ZimbaBeats/releases/latest)
- **ZimbaBeats Family (Parent App)**: [Download APK](https://github.com/raveuk/ZimbaBeats/releases/latest)

### Installation Steps

1. Download the APK file to your Android device
2. Open the downloaded file
3. If prompted, enable "Install from unknown sources" in Settings
4. Tap **Install**
5. Once installed, tap **Open** to launch

### System Requirements

- Android 7.0 (API 24) or higher
- 100 MB available storage
- Active internet connection

---

## Parent App Setup

### Step 1: Create Account

1. Open **ZimbaBeats Family** on your device
2. Tap **Sign In with Google** or **Sign Up with Email**
3. Complete the authentication process
4. You'll be taken to the main dashboard

### Step 2: Set Your PIN

1. Go to **Settings** (gear icon)
2. Tap **Set PIN**
3. Enter a 4-digit PIN
4. Confirm your PIN
5. This PIN protects access to parental settings

### Step 3: Add Your Children

1. From the dashboard, tap **My Children**
2. Tap the **+ Add Child** button
3. Enter child's details:
   - **Name**: Child's first name
   - **Birth Year**: For age-appropriate defaults
   - **Avatar**: Choose a character (optional)
4. Tap **Save**
5. Repeat for additional children

---

## Child App Setup

### Step 1: Install the App

1. Install **ZimbaBeats** on the child's device (see [Installation](#installation))
2. Open the app
3. The app works standalone but with limited parental features

### Step 2: Initial Configuration

1. On first launch, you'll see the home screen with videos
2. The app is ready to use immediately
3. For parental controls, link to parent device (next section)

---

## Linking Parent and Child Devices

### On Parent Device (ZimbaBeats Family)

1. Open **ZimbaBeats Family**
2. Go to **My Children**
3. Find the child profile you want to link
4. Tap **Add Device** button
5. A **6-character pairing code** will appear
6. The code is valid for **24 hours**

### On Child Device (ZimbaBeats)

1. Open **ZimbaBeats**
2. Go to **Settings** (gear icon in bottom navigation or menu)
3. Tap **Parental Controls**
4. Tap **Link to Family**
5. Enter the **6-character code** from parent's device
6. Enter the **child's name** as displayed
7. Tap **Connect**
8. You'll see "Connected to Family" when successful

### Verification

**On Parent Device:**
- The linked device appears under the child's profile
- Shows device model, online status, and last seen time

**On Child Device:**
- Settings shows "Linked to: [Parent Name]"
- Parental control settings are now active

---

## Managing Children Profiles

### Viewing All Children

1. Open **ZimbaBeats Family**
2. Tap **My Children** from dashboard
3. See all children with their linked devices

### Editing a Child Profile

1. Go to **My Children**
2. Tap the **three-dot menu** on the child's card
3. Select **Edit**
4. Modify name, birth year, or avatar
5. Tap **Save**

### Expanding Child Settings

1. Go to **My Children**
2. Tap the **gear icon** on a child's card
3. Settings panel expands showing:
   - Age Rating
   - Video Filters
   - Music Filters

### Removing a Child

1. Go to **My Children**
2. Tap the **three-dot menu** on the child's card
3. Select **Delete**
4. Confirm deletion
5. All linked devices will be automatically unlinked

### Managing Linked Devices

1. Go to **My Children**
2. Find the child's profile
3. Under "Devices" section:
   - View all linked devices
   - See online/offline status
   - Tap **X** to remove a device
   - Tap **Move** to reassign to different child

---

## Content Filtering

Content filtering controls what videos the child can watch.

### Setting Age Rating

1. Go to **My Children**
2. Tap gear icon on child's card
3. Under **Age Rating**, select:

| Rating | Description |
|--------|-------------|
| **Under 5** | Most restrictive, toddler content only |
| **5-7** | Young children content |
| **8-12** | Pre-teen appropriate content |
| **13+** | Teen content |
| **16+** | Older teen content |
| **All Ages** | No age restrictions |

### Video Filter Options

Expand child settings to access:

#### Blocked Keywords
- Tap **Blocked Keywords**
- Add words/phrases to block
- Videos with these words in title/description are hidden
- Example: "scary", "horror", "prank"

#### Blocked Channels
- Tap **Blocked Channels**
- Add channel names or IDs to block
- All videos from these channels are hidden

#### Content Toggles

| Toggle | Effect |
|--------|--------|
| **Safe Search** | Filters explicit content from search |
| **Strict Mode** | Only shows verified kid-friendly content |
| **Block Live Streams** | Prevents watching live content |
| **Hide Comments** | Removes comment section from videos |

### How Filtering Works

1. Parent sets filters in **ZimbaBeats Family**
2. Settings sync to cloud in real-time
3. Child's **ZimbaBeats** app receives settings
4. Content is filtered before display
5. Blocked content simply doesn't appear

---

## Music Filtering

Music filtering controls what music the child can listen to.

### Understanding Filter Modes

#### Whitelist Mode (Ages 5-14)
- **All music is BLOCKED by default**
- Only parent-approved music is allowed
- Safest option for young children

#### Blocklist Mode (Ages 16+)
- **All music is ALLOWED by default**
- Parent blocks specific artists/content
- More freedom with targeted restrictions

### Configuring Music Filters

1. Go to **My Children**
2. Tap gear icon on child's card
3. Scroll to **Music Filters** section

#### When Whitelist Mode is ON:

| Setting | Description |
|---------|-------------|
| **Include Kids Artists** | Auto-allow CoComelon, Disney, Pinkfong, etc. |
| **Allowed Artists** | Add specific artists child can hear |
| **Allowed Keywords** | Add keywords to match song titles |

#### When Whitelist Mode is OFF:

| Setting | Description |
|---------|-------------|
| **Blocked Artists** | Artists whose music is hidden |
| **Blocked Keywords** | Keywords that block matching songs |

### Adding Allowed/Blocked Items

1. Tap the setting card (e.g., "Allowed Artists")
2. Type the artist name or keyword
3. Tap **+** or **Add**
4. Item appears in the list
5. Tap **X** to remove items
6. Tap **Done** when finished

### Example Configurations

**For a 6-year-old:**
- Whitelist Mode: ON
- Include Kids Artists: ON
- Allowed Artists: "Taylor Swift", "Ed Sheeran"
- Allowed Keywords: "frozen", "moana", "encanto"

**For a 14-year-old:**
- Whitelist Mode: OFF
- Blocked Artists: [explicit artists]
- Blocked Keywords: "explicit", "drugs"

---

## Screen Time & Bedtime

### Setting Daily Screen Time Limits

1. Open **ZimbaBeats Family**
2. Go to **Screen Time** from dashboard
3. Enable **Screen Time Limits**
4. Set daily limit (e.g., 2 hours)
5. Choose which days the limit applies

### What Happens When Limit is Reached

- Child sees "Time's Up" message
- App becomes locked
- Resets at midnight or configured time
- Parent can grant extra time if needed

### Setting Bedtime

1. Go to **Bedtime** from dashboard
2. Enable **Bedtime Mode**
3. Set **Start Time** (e.g., 8:00 PM)
4. Set **End Time** (e.g., 7:00 AM)
5. Choose which days apply

### During Bedtime Hours

- App shows "It's Bedtime" message
- Content is inaccessible
- Automatically unlocks when bedtime ends

---

## Monitoring Activity

### Viewing Watch History

1. Open **ZimbaBeats Family**
2. Go to **Activity** from dashboard
3. Select child from dropdown
4. View:
   - Videos watched today
   - Music played
   - Search queries
   - Blocked content attempts

### Understanding Alerts

When a child attempts to access blocked content:
- Parent receives notification (if enabled)
- Attempt is logged in Activity
- Shows what was blocked and why

### Usage Statistics

The Activity screen shows:
- Total watch time today/this week
- Most watched categories
- Peak usage times
- Content block frequency

---

## Playlist Sharing

Children can share playlists with friends using share codes.

### Creating a Share Code (Child App)

1. Open a playlist in **ZimbaBeats**
2. Tap **Share** button
3. A 6-character code is generated
4. Share this code with friends
5. Code expires after 7 days

### Importing a Shared Playlist (Child App)

1. Go to **Library** > **Playlists**
2. Tap **Import Playlist**
3. Enter the 6-character share code
4. Playlist is added to library
5. Blocked content is automatically filtered out

### Parent Visibility

Parents can see in **ZimbaBeats Family**:
- Which playlists were shared
- Who imported shared playlists
- Option to revoke share codes

---

## Troubleshooting

### Pairing Issues

**Problem:** Pairing code not working
- Ensure code is entered correctly (case-sensitive)
- Check code hasn't expired (24-hour validity)
- Verify internet connection on both devices
- Generate a new code and try again

**Problem:** Child device shows "Not linked"
- Re-enter pairing code on child device
- Check parent account is logged in
- Restart both apps

### Content Not Filtering

**Problem:** Blocked content still appears
- Wait 30 seconds for sync
- Restart child app
- Verify filter settings are saved
- Check child is assigned to correct profile

**Problem:** All content is blocked
- Check if Whitelist Mode is ON with empty lists
- Add allowed artists/keywords
- Or turn Whitelist Mode OFF

### Sync Issues

**Problem:** Settings not syncing
- Check internet connection
- Verify parent is logged in
- Restart both apps
- Re-link device if persistent

### App Crashes

**Problem:** App closes unexpectedly
- Clear app cache: Settings > Apps > ZimbaBeats > Clear Cache
- Update to latest version
- Reinstall if problem persists
- Report issue on GitHub

### Music Not Playing

**Problem:** Music tracks won't play
- Check internet connection
- Verify music isn't blocked by filters
- Try searching for different content
- Some region restrictions may apply

---

## Quick Reference

### Parent App Navigation

| Screen | Access | Purpose |
|--------|--------|---------|
| Dashboard | Home | Overview and quick actions |
| My Children | Dashboard > My Children | Manage profiles and devices |
| Screen Time | Dashboard > Screen Time | Set usage limits |
| Bedtime | Dashboard > Bedtime | Set sleep schedule |
| Activity | Dashboard > Activity | View history and alerts |
| Settings | Gear icon | App settings and PIN |

### Child App Navigation

| Screen | Access | Purpose |
|--------|--------|---------|
| Home | Bottom nav | Browse videos |
| Music | Bottom nav | Browse and play music |
| Search | Top bar | Find content |
| Library | Bottom nav | Playlists and favorites |
| Settings | Menu/Bottom nav | Link to parent, preferences |

### Keyboard Shortcuts (Pairing Code)

- Codes are 6 characters
- Alphanumeric (letters and numbers)
- Case-insensitive
- Valid for 24 hours
- Single use only

---

## Support

### Reporting Issues

1. Go to [GitHub Issues](https://github.com/raveuk/ZimbaBeats/issues)
2. Click **New Issue**
3. Select bug report template
4. Include:
   - Device model
   - Android version
   - App version
   - Steps to reproduce
   - Screenshots if applicable

### Getting Help

- **GitHub Issues**: Technical problems
- **Discussions**: Feature requests and questions

### Supporting Development

If you find ZimbaBeats useful:
- Star the repository on GitHub
- Report bugs to help improve
- [Buy Me a Coffee](https://buymeacoffee.com/zimbabeats)

---

## Version History

| Version | Key Features |
|---------|--------------|
| v1.0.12 | Multi-child profiles, per-child filtering, music blocklist |
| v1.0.11 | Cloud pairing fixes, filter sync improvements |
| v1.0.10 | Music filtering, age-based restrictions |
| v1.0.9 | Playlist sharing with codes |

---

*Last updated: January 2026*

*ZimbaBeats - Child-Safe YouTube with Parental Controls*
