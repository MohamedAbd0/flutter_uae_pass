## 0.0.1

- Initial release
- ios set ups
- android set ups

## 0.0.2

- iOS build issues fixed

## 0.0.3

- fixed bug for null values in profile information

## 1.0.0

- adding Emirates ID number "idn" in ProfileData.

## 1.0.1

- updated java version

## 1.0.2

- adding language support

## 1.0.3

- resolve conflict with go_router custom schema integration

## 1.0.4

- Enhanced iOS webview presentation with full screen modal and back navigation
- Added custom Android webview activity for full screen authentication experience
- Implemented consistent "UAE PASS" title across both platforms
- Unified cancellation behavior with proper error messaging
- Improved user experience with native back button support on both platforms

## 1.0.5

- **NEW: Added web platform support with OAuth2 popup authentication flow**
- Web implementation supports all core features: signIn, getAccessToken, getProfile, signOut
- Cross-platform compatibility across iOS, Android, and Web

## 1.0.6

- **CRITICAL FIX: Resolved dart:html import error on mobile platforms**
- Implemented conditional imports to prevent web-specific code from being compiled on mobile
- Added platform-specific stub implementations for proper cross-platform compatibility
- Fixed "Dart library 'dart:html' is not available on this platform" error when running on Android/iOS

## 1.1.0

- Fix Facing chrome redirection issue after auth succes or failure
- Getting Error UAE Pass app not found

## 1.1.1

- Fix: include XIB resources in iOS podspec to prevent crash
