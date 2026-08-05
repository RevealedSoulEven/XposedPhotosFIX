package com.souleven.photosmod;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class main implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) {
        if (!lpparam.packageName.equals("com.google.android.apps.photos")) {
            return;
        }

        XposedBridge.log("¸,ø¤°`°¤ø,¸¸,ø¤°`°¤ø,¸¸,ø¤°`°¤ø,¸¸,ø¤°`°¤ø,¸¸,ø¤°`°¤ø,¸¸,ø¤°`°¤ø,¸¸,ø¤°`°¤ø,¸¸,ø¤°`°¤ø,¸\n\n");
        try {
            XposedBridge.log("Google Photos initialization sequence caught.");

            long appVersion = CacheManager.getAppVersion(lpparam);
            if (appVersion == 0L) {
                XposedBridge.log("Unable to verify targeted variant versions. Terminating.");
                return;
            }

            try {
                CacheManager.Cache cache = CacheManager.loadCache();

                if (cache != null && cache.appVersion == appVersion && cache.moduleVersion == CacheManager.MODULE_VERSION) {
                    boolean hasStructural = (cache.builderClassName != null && cache.setterMethodName != null);
                    boolean hasLegacy = (cache.legacyClassName != null && cache.legacyMethodName != null);

                    if (!hasStructural && !hasLegacy) {
                        XposedBridge.log("Cache found but contains no valid hook targets. Re-scanning...");
                    } else {
                        XposedBridge.log("Valid operational cache discovered. Resolving intercept definitions...");
                        if (hasStructural) {
                            CacheManager.executeHook(cache.builderClassName, cache.setterMethodName, cache.filepathFieldName, lpparam);
                        } else {
                            XposedBridge.log("Cached configuration is legacy-only. Structural hook skipped.");
                            XposedBridge.log("If folders are not separating after clearing Google Photos data, please create an issue at: https://github.com/RevealedSoulEven/XposedPhotosFIX/issues");
                        }
                        if (hasLegacy) {
                            CacheManager.executeLegacyHook(cache.legacyClassName, cache.legacyMethodName, lpparam);
                        }
                        XposedBridge.log("Cache configurations loaded successfully.");
                        return;
                    }
                }

                XposedBridge.log("Cache configurations missing or mismatched. Launching dynamic bytecode extraction engine...");
                CacheManager.scanAndHook(lpparam, appVersion);
                XposedBridge.log("Bytecode processing sequence fully finished.");

            } catch (Throwable t) {
                XposedBridge.log("Critical validation anomaly inside container execution layer: " + t.getMessage());
            }
        } finally {
            XposedBridge.log("\n\n¸,ø¤°`°¤ø,¸¸,ø¤°`°¤ø,¸¸,ø¤°`°¤ø,¸¸,ø¤°`°¤ø,¸¸,ø¤°`°¤ø,¸¸,ø¤°`°¤ø,¸¸,ø¤°`°¤ø,¸¸,ø¤°`°¤ø,¸");
        }
    }
}