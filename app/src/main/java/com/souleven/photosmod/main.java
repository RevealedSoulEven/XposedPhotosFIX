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

        XposedBridge.log("Google Photos initialization sequence caught.");

        long appVersion = CacheManager.getAppVersion(lpparam);
        if (appVersion == 0L) {
            XposedBridge.log("Unable to verify targeted variant versions. Terminating.");
            return;
        }

        try {
            CacheManager.Cache cache = CacheManager.loadCache();

            if (cache != null && cache.appVersion == appVersion && cache.moduleVersion == CacheManager.MODULE_VERSION) {
                XposedBridge.log("Valid operational cache discovered. Resolving intercept definitions...");
                CacheManager.executeHook(cache.builderClassName, cache.setterMethodName, cache.filepathFieldName, lpparam);
                if (cache.legacyClassName != null && cache.legacyMethodName != null) {
                    CacheManager.executeLegacyHook(cache.legacyClassName, cache.legacyMethodName, lpparam);
                }
                XposedBridge.log("Cache configurations loaded successfully.");
                return;
            }

            XposedBridge.log("Cache configurations missing or mismatched. Launching dynamic bytecode extraction engine...");
            CacheManager.scanAndHook(lpparam, appVersion);
            XposedBridge.log("Bytecode processing sequence fully finished.");

        } catch (Throwable t) {
            XposedBridge.log("Critical validation anomaly inside container execution layer: " + t.getMessage());
        }
    }
}