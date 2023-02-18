package com.souleven.photosmod;
import android.app.AndroidAppHelper;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import java.lang.reflect.Modifier;
import java.util.Locale;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import io.github.neonorbit.dexplore.DexFactory;
import io.github.neonorbit.dexplore.Dexplore;
import io.github.neonorbit.dexplore.filter.*;
import io.github.neonorbit.dexplore.result.*;



// Original Developer : Revealed SoulEven (a.k.a SoulEven)
// Github : https://github.com/RevealedSoulEven

public class main implements IXposedHookLoadPackage {


    String TAG = "ayush";
    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam loadPackageParam) {

        if (loadPackageParam.packageName.equals("com.google.android.apps.photos")) {

            ClassFilter classFilter = new ClassFilter.Builder()
                    .setReferenceTypes(ReferenceTypes.builder().addString().build())
                    .setReferenceFilter(pool ->
                            pool.stringsContain("/dcim/")
                    ).build();

            MethodFilter methodFilter = new MethodFilter.Builder()
                    .setReferenceTypes(ReferenceTypes.builder().addString().build())
                    .setReferenceFilter(pool ->
                            pool.stringsContain("/dcim/")
                    ).build();
            Dexplore dexplore = DexFactory.load(loadPackageParam.appInfo.sourceDir);
            MethodData result = dexplore.findMethod(DexFilter.MATCH_ALL, classFilter, methodFilter);
            XposedBridge.hookMethod(result.loadMethod(loadPackageParam.classLoader), new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Boolean returnval = (Boolean) param.getResult();
                    if(returnval==true){
                        if(!param.args[0].toString().toLowerCase(Locale.US).contains("/dcim/camera/")){
                            param.setResult(false);
                        }
                    }
                }
            });
        }
    }
}
