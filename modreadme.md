# XposedPhotosFIX Hook Architecture & Maintenance Guide

> **Note**: This README is AI-generated, with technical references, initial approaches, and core logic provided by the original developer, **RevealedSoulEven**.

This document explains the strategy used to dynamically hook Google Photos and bypass the internal camera folder checks. It is intended to serve as a guide for future maintenance, allowing anyone to manually update the hook logic if Google significantly changes the app's architecture.

---

## 1. The Core Problem
Google Photos aggregates all images located anywhere inside the `/DCIM/` folder (such as Screenshots or third-party app folders) and treats them as if they were taken by the camera. 

Internally, when Google Photos scans a file, it evaluates if the file path is a camera folder. If it is, it sets a specific boolean flag inside a **Builder Class** (which eventually creates an immutable Media Item object).

To fix this, the module hooks the system to intercept the camera folder checks and forces the flag to `false` if the file path is not explicitly in the actual camera folder (e.g., `/dcim/camera/`).

---

## 2. The Legacy Approach (boolean Method)
For over 5 years, Google Photos used a simple, centralized boolean method to verify if a path was a camera path.

This legacy method:
- Returned a `boolean`
- Took a single `String` argument (the file path)
- Contained the string literal `"/dcim/"`

This was the only method the module hooked for years, and it worked flawlessly.

---

## 3. The Structural Approach (Caller Matching via DexKit)
In mid-2026, Google shifted the core logic into complex builder classes, making the legacy hook ineffective on its own. The module was updated (v3.0) to use **Structural Caller Matching** via DexKit to find and hook the new builder method.

### Step A: Finding the "Inserter Class"
Instead of looking directly for the Builder Class, we first look for the massive class responsible for scanning and inserting local media into the database. 
* **Signature**: This class contains highly unique strings: `"/dcim/"` and `"LocalMediaInsert"`.

### Step B: Finding the Target Setter Method
The Inserter Class evaluates the file path, determines if it's in a DCIM folder, and passes that boolean result into the Builder Class via a setter method.
* **Signature**: We ask DexKit to find a `void(boolean)` method that:
  1. Is declared inside a class containing `" inCameraFolder"`
  2. Is **invoked by** the Inserter Class we found in Step A.
  3. Uses the bitmask number `32` (this is critical to isolate the camera folder setter from other boolean setters like `isHidden` (16) or `raw` (8) which are also called by the Inserter Class).

### Step C: Finding the File Path Variable
To evaluate the file path in our hook, we need to know which class variable holds it.
* **Signature**: Inside the Builder Class, there is a method that validates the file path and throws a `NullPointerException` with the exact string `"Null filepath"`. We use DexKit to find which class variable that specific method writes to.

### Step D: The Dynamic Fallback
Even if DexKit fails to find the exact file path variable name, the hook contains a highly robust dynamic fallback:
* At runtime, the hook uses Reflection to iterate over all fields in the Builder Class that are of type `Optional`.
* It calls `.get()` on them, and if it finds a String containing `"/dcim/"`, it assumes that is the file path and runs the override logic.

---

## 4. The Google Switcheroo (August 2026)
Just **2 weeks** after v3.0 was released with the structural approach, Google removed the builder class/setter method from a newer version of Google Photos, reverting back to the legacy boolean method. This broke the module because v3.0 treated the structural method as **required** — if it wasn't found, the module would log a failure and not activate any hooks at all, even though the legacy method was still successfully discovered.

### What happened:
- v3.0 shipped with structural hooking as the primary method.
- Google Photos updated and removed the structural builder classes entirely.
- The DexKit scan found the legacy method but not the structural one.
- The module hit the failure branch and did nothing. No hooks were applied.

### The fix (current approach — Graceful Degradation):
The module now treats both methods as **independently optional**. It will activate whichever methods it finds:

| Structural Found | Legacy Found | Result |
|:---:|:---:|:---|
| ✅ | ✅ | Both hooks active (strongest coverage) |
| ✅ | ❌ | Structural hook only + log |
| ❌ | ✅ | Legacy hook only + log |
| ❌ | ❌ | Failure — logs a message asking to create a GitHub issue |

The caching system was also updated to support legacy-only caches. Previously, the cache required `builderClassName` and `setterMethodName` to be present — a legacy-only cache would crash on load. Now all fields are optional.

### Current status:
As of August 2026, the legacy method is working again on the latest Google Photos build. It's unclear if Google will bring the structural method back in future builds — this could be build-specific. The module is now prepared for either scenario, so let's wait and see what happens.

---

## 5. How to Fix Manually in the Future
If the module stops working, follow these steps to manually update the tracking logic:

1. **If neither method is found**: Google may have changed the string `"LocalMediaInsert"` or moved away from `"/dcim/"` entirely. Decompile the APK and search for camera folder detection logic. Check what new unique strings are used and update the DexKit queries.
2. **If the structural hook attaches to the wrong method**: Google may have changed the bitmask index from `32`. Decompile the APK, find the Builder Class, look at the validation method that builds the `" inCameraFolder"` string, and check what bitwise AND operation (e.g., `& 32`, `& 64`) it uses. Update the `.usingNumbers()` query accordingly.
3. **If the File Path is not found**: The fallback mechanism should catch it. However, if the fallback fails, decompile the Builder Class and search for `"Null filepath"` to manually find the new variable name or structure.
4. **If only legacy is active but folders aren't separating**: The legacy method might not be the primary check anymore. Google may have introduced a completely new approach. Decompile and investigate the media scanning flow from scratch.
