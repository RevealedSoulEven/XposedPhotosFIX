package com.souleven.photosmod;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;
import org.json.JSONObject;
import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.FindField;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.query.matchers.FieldMatcher;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.query.matchers.MethodsMatcher;
import org.luckypray.dexkit.result.ClassData;
import org.luckypray.dexkit.result.FieldData;
import org.luckypray.dexkit.result.MethodData;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;

public class CacheManager {

    private static final String CACHE_PATH = "/data/data/com.google.android.apps.photos/files/photosmod.cache";
    public static final int MODULE_VERSION = 3;

    public static class Cache {
        public long appVersion;
        public int moduleVersion;
        public String builderClassName;
        public String setterMethodName;
        public String filepathFieldName;
        public String legacyClassName;
        public String legacyMethodName;

        public Cache(long appVersion, int moduleVersion, String builderClassName, String setterMethodName, String filepathFieldName, String legacyClassName, String legacyMethodName) {
            this.appVersion = appVersion;
            this.moduleVersion = moduleVersion;
            this.builderClassName = builderClassName;
            this.setterMethodName = setterMethodName;
            this.filepathFieldName = filepathFieldName;
            this.legacyClassName = legacyClassName;
            this.legacyMethodName = legacyMethodName;
        }
    }

    public static void scanAndHook(LoadPackageParam lpparam, long appVersion) {
        System.loadLibrary("dexkit");

        String builderClassName = null;
        String setterMethodName = null;
        String filepathFieldName = null;
        String legacyClassName = null;
        String legacyMethodName = null;

        try (DexKitBridge bridge = DexKitBridge.create(lpparam.appInfo.sourceDir)) {
            if (bridge != null) {
                List<ClassData> inserterClasses = bridge.findClass(FindClass.create()
                    .matcher(ClassMatcher.create()
                        .usingStrings("/dcim/", "LocalMediaInsert")
                    )
                );

                if (inserterClasses != null && !inserterClasses.isEmpty()) {
                    String inserterClassName = inserterClasses.get(0).getName();
                    XposedBridge.log("Discovered inserter class name: " + inserterClassName);

                    List<MethodData> setterMethods = bridge.findMethod(FindMethod.create()
                        .matcher(MethodMatcher.create()
                            .returnType("void")
                            .paramCount(1)
                            .paramTypes("boolean")
                            .usingNumbers(32) // isolates inCameraFolder from isHidden (16) and raw (8)
                            .declaredClass(ClassMatcher.create()
                                .usingStrings("Missing required properties:", " inCameraFolder")
                            )
                            .callerMethods(MethodsMatcher.create()
                                .add(MethodMatcher.create().declaredClass(ClassMatcher.create().className(inserterClassName)))
                            )
                        )
                    );

                    if (setterMethods != null && !setterMethods.isEmpty()) {
                        MethodData targetMethod = setterMethods.get(0);
                        builderClassName = targetMethod.getClassName();
                        setterMethodName = targetMethod.getName();
                        XposedBridge.log("Discovered builder class: " + builderClassName + ", method: " + setterMethodName);

                        // Identify the exact filepath variable name by tracking what field the validation method writes to
                        List<FieldData> fields = bridge.findField(FindField.create()
                            .matcher(FieldMatcher.create()
                                .declaredClass(ClassMatcher.create().className(builderClassName))
                                .writeMethods(MethodsMatcher.create()
                                    .add(MethodMatcher.create().usingStrings("Null filepath"))
                                )
                            )
                        );

                        if (fields != null && !fields.isEmpty()) {
                            filepathFieldName = fields.get(0).getName();
                            XposedBridge.log("Discovered internal path field mapping: " + filepathFieldName);
                        }
                    }
                }

                // Locate the legacy boolean method (old but maybe works for someone)
                List<MethodData> legacyMethods = bridge.findMethod(FindMethod.create()
                        .matcher(MethodMatcher.create()
                                .returnType("boolean")
                                .usingStrings("/dcim/")
                        )
                );

                if (legacyMethods != null && !legacyMethods.isEmpty()) {
                    MethodData legacyMethod = legacyMethods.get(0);
                    legacyClassName = legacyMethod.getClassName();
                    legacyMethodName = legacyMethod.getName();
                    XposedBridge.log("Discovered legacy boolean method: " + legacyClassName + "." + legacyMethodName);
                }
            }
        } catch (Throwable t) {
            XposedBridge.log("DexKit engine processing anomaly: " + t.getMessage());
        }

        if (builderClassName != null && setterMethodName != null) {
            executeHook(builderClassName, setterMethodName, filepathFieldName, lpparam);

            if (legacyClassName != null && legacyMethodName != null) {
                executeLegacyHook(legacyClassName, legacyMethodName, lpparam);
            }

            Cache newCache = new Cache(appVersion, MODULE_VERSION, builderClassName, setterMethodName, filepathFieldName, legacyClassName, legacyMethodName);
            saveCache(newCache);
        } else {
            XposedBridge.log("Failure: Core tracking compilation vectors did not resolve cleanly.");
        }
    }

    public static void executeLegacyHook(String className, String methodName, LoadPackageParam lpparam) {
        try {
            de.robv.android.xposed.XposedBridge.hookAllMethods(
                    XposedHelpers.findClass(className, lpparam.classLoader),
                    methodName,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            if (param.args.length > 0 && param.args[0] instanceof String) {
                                String filepath = (String) param.args[0];
                                if (filepath != null && !filepath.toLowerCase(Locale.US).contains("/dcim/camera/")) {
                                    param.setResult(false);
                                }
                            }
                        }
                    }
            );
            XposedBridge.log("Legacy intercept runtime hooks mounted securely.");
        } catch (Throwable t) {
            XposedBridge.log("Failed to instantiate legacy mapping layers: " + t.getMessage());
        }
    }

    public static void executeHook(String className, String methodName, final String fieldName, LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                className,
                lpparam.classLoader,
                methodName,
                boolean.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        Boolean inCameraFolder = (Boolean) param.args[0];

                        if (Boolean.TRUE.equals(inCameraFolder)) {
                            String filepath = null;

                            // Method A: Attempt direct structural read from field verified by DexKit
                            if (fieldName != null) {
                                try {
                                    Object optionalPath = XposedHelpers.getObjectField(param.thisObject, fieldName);
                                    if (optionalPath != null && (Boolean) XposedHelpers.callMethod(optionalPath, "isPresent")) {
                                        Object pathObj = XposedHelpers.callMethod(optionalPath, "get");
                                        if (pathObj != null) {
                                            filepath = pathObj.toString();
                                        }
                                    }
                                } catch (Throwable ignored) {}
                            }

                            // Method B (Dynamic Fallback): Iterate fields to track the path if structural definitions shift
                            if (filepath == null) {
                                try {
                                    java.lang.reflect.Field[] fields = param.thisObject.getClass().getDeclaredFields();
                                    for (java.lang.reflect.Field field : fields) {
                                        if (field.getType().getName().contains("Optional")) {
                                            field.setAccessible(true);
                                            Object optionalObj = field.get(param.thisObject);
                                            if (optionalObj != null && (Boolean) XposedHelpers.callMethod(optionalObj, "isPresent")) {
                                                Object val = XposedHelpers.callMethod(optionalObj, "get");
                                                if (val instanceof String) {
                                                    String testPath = (String) val;
                                                    if (testPath.toLowerCase(Locale.US).contains("/dcim/")) {
                                                        filepath = testPath;
                                                        break;
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } catch (Throwable t) {
                                    XposedBridge.log("Dynamic evaluation scanner anomaly: " + t.getMessage());
                                }
                            }
                            
                            if (filepath != null) {
                                if (!filepath.toLowerCase(Locale.US).contains("/dcim/camera/")) {
                                    param.args[0] = false;
                                    // XposedBridge.log("Successfully corrected folder classification state to false for: " + filepath);
                                }
                            }
                        }
                    }
                }
            );
            XposedBridge.log("Structural intercept runtime hooks mounted securely.");
        } catch (Throwable t) {
            XposedBridge.log("Failed to instantiate operations mapping layers: " + t.getMessage());
        }
    }

    public static Cache loadCache() {
        try {
            File file = new File(CACHE_PATH);
            if (!file.exists()) return null;

            String content = new String(Files.readAllBytes(Paths.get(CACHE_PATH)), StandardCharsets.UTF_8);
            JSONObject json = new JSONObject(content);

            return new Cache(
                    json.getLong("appVersion"),
                    json.getInt("moduleVersion"),
                    json.getString("builderClassName"),
                    json.getString("setterMethodName"),
                    json.optString("filepathFieldName", null),
                    json.optString("legacyClassName", null),
                    json.optString("legacyMethodName", null)
            );
        } catch (Throwable t) {
            return null;
        }
    }

    public static void saveCache(Cache cache) {
        try {
            File file = new File(CACHE_PATH);
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            JSONObject json = new JSONObject();
            json.put("appVersion", cache.appVersion);
            json.put("moduleVersion", cache.moduleVersion);
            json.put("builderClassName", cache.builderClassName);
            json.put("setterMethodName", cache.setterMethodName);
            json.put("filepathFieldName", cache.filepathFieldName);
            if (cache.legacyClassName != null) {
                json.put("legacyClassName", cache.legacyClassName);
                json.put("legacyMethodName", cache.legacyMethodName);
            }

            Files.write(Paths.get(CACHE_PATH), json.toString().getBytes(StandardCharsets.UTF_8));
            XposedBridge.log("Dynamic configurations saved to internal file cache.");
        } catch (Throwable ignored) {}
    }

    public static long getAppVersion(LoadPackageParam lpparam) {
        try {
            Class<?> parserCls = Class.forName("android.content.pm.PackageParser");
            Object parser = parserCls.getDeclaredConstructor().newInstance();
            File apkFile = new File(lpparam.appInfo.sourceDir);
            Object pkg = XposedHelpers.callMethod(parser, "parsePackage", apkFile, 0);
            try {
                return XposedHelpers.getLongField(pkg, "mLongVersionCode");
            } catch (Throwable t) {
                return XposedHelpers.getIntField(pkg, "mVersionCode");
            }
        } catch (Throwable t) {
            return 0L;
        }
    }
}
