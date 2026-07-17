# XposedPhotosFIX Hook Architecture & Maintenance Guide

> **Note**: This README is AI-generated, with technical references, initial approaches, and core logic provided by the original developer, **RevealedSoulEven**.

This document explains the strategy used to dynamically hook Google Photos and bypass the internal camera folder checks. It is intended to serve as a guide for future maintenance, allowing anyone to manually update the hook logic if Google significantly changes the app's architecture.

---

## 1. The Core Problem
Google Photos aggregates all images located anywhere inside the `/DCIM/` folder (such as Screenshots or third-party app folders) and treats them as if they were taken by the camera. 

Internally, when Google Photos scans a file, it evaluates if the file path is a camera folder. If it is, it sets a specific boolean flag inside a **Builder Class** (which eventually creates an immutable Media Item object).

To fix this, the module hooks the system to intercept the camera folder checks and forces the flag to `false` if the file path is not explicitly in the actual camera folder (e.g., `/dcim/camera/`).

---

## 2. The Original Approach (Legacy boolean Method)
Historically (and for over 5 years!), the hook worked by intercepting a very simple, centralized boolean method that Google Photos used to verify if a path was a camera path.

This legacy method:
- Returned a `boolean`
- Took a single `String` argument (the file path)
- Contained the string literal `"/dcim/"`

### Why we shifted:
While this method still exists in the codebase (and we still hook it as a fallback!), it is no longer the primary method called by Google Photos on newer versions for modern media insertion. Google shifted the core logic into complex builder classes, making the legacy hook ineffective on its own for newer updates.

---

## 3. The Current Approach (Structural Caller Matching)
To make the module resilient against cloned classes and obfuscation, we completely shifted from pure string matching to **Structural Caller Matching** using DexKit. We now identify the target class and method by analyzing *how the classes interact with each other in the bytecode*.

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

## 4. How to Fix Manually in the Future
If the module stops working, follow these steps to manually update the tracking logic:

1. **If the Builder Class isn't found**: Google may have changed the string `"LocalMediaInsert"`. Decompile the APK and search for `"/dcim/"` to find the Inserter Class. Check what new unique strings it contains and update the DexKit query.
2. **If the Hook attaches to the wrong method**: Google may have changed the bitmask index from `32`. Decompile the APK, find the Builder Class, look at the validation method that builds the `" inCameraFolder"` string, and check what bitwise AND operation (e.g., `& 32`, `& 64`) it uses. Update the `.usingNumbers()` query accordingly.
3. **If the File Path is not found**: The fallback mechanism should catch it. However, if the fallback fails, decompile the Builder Class and search for `"Null filepath"` to manually find the new variable name or structure.
