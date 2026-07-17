<img width="128" height="128" src="https://i.imgur.com/UyoTRed.png" alt="icon_square">

# Xposed Google Photos FIX

Xposed Module to Force Google Photos to separate each folder like Snapchat, Screenshot, Facebook, and so on separated from the Camera folder.

## [Downloads](https://github.com/RevealedSoulEven/XposedPhotosFIX/releases/)

> [![APK: v3.0](https://img.shields.io/badge/APK-v3.0-brightgreen)](https://github.com/RevealedSoulEven/XposedPhotosFIX/releases/)

> [!NOTE]
> **Status (July 2026):** This module is confirmed to be working with the latest version of Google Photos.
>
> If it stops working in the future, please open an issue. Google Photos' folder detection logic has remained largely unchanged for over five years, so updates are rarely needed.

## What's New in v3.0:
- **Moved to DexKit Engine**: Completely rebuilt to scan bytecode at runtime to dynamically find the correct obfuscated classes and methods. This makes the module extremely resilient to future Google Photos updates!
- **Modernized Hooking Logic**: Shifted from intercepting the legacy DCIM boolean method (which Google stopped using as the primary check in recent updates) to a structural hook that intercepts the modern media Builder classes directly.
- **Fast Caching**: Built-in caching system ensures the heavy DexKit scan only runs once.

## Requirements:

- Android Device (ROOTED)
- Xposed framework installed properly. Preferred [LSPosed](https://github.com/LSPosed/LSPosed/)

## Usage:
1. Enable the module.
2. Select **Google Photos** in LSPosed.
3. If it doesn't work, clear the data for Google Photos.
4. If the issue persists, re-optimize the module in LSPosed.

## Credits:

- SoulEven : for my lazy brain (aka Ayush Maurya)
- My School : for giving me boring lectures so I got enough time to make this
- [LSPosed](https://github.com/LSPosed/LSPosed/) : for nothing hehe
- [DexKit](https://github.com/LuckyPray/DexKit) : for the insanely fast runtime bytecode scanning engine
- My PC : for not responding the entire time
- Google : for making such a useless Photos app that required me to separate the folders
- You : for wasting your time reading this