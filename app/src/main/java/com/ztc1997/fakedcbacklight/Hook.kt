package com.ztc1997.fakedcbacklight

import android.content.Context
import android.provider.Settings
import de.robv.android.xposed.*
import de.robv.android.xposed.callbacks.XC_LoadPackage

const val HAL_SCREEN_BRIGHTNESS = "COM_ZTC1997_FAKEDCBACKLIGHT_HAL_SCREEN_BRIGHTNESS"
const val REDUCE_BRIGHT_LEVEL = "COM_ZTC1997_FAKEDCBACKLIGHT_REDUCE_BRIGHT_LEVEL"

class Hook : IXposedHookLoadPackage {
    private val prefs = XSharedPreferences(BuildConfig.APPLICATION_ID, "config")

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "android") return

        try {
            val localDisplayDevice = XposedHelpers.findClass(
                "com.android.server.display.LocalDisplayAdapter\$LocalDisplayDevice",
                lpparam.classLoader
            )

            val displayOffloadSessionClass = XposedHelpers.findClass(
                "com.android.server.display.DisplayOffloadSessionImpl",
                lpparam.classLoader
            )

            XposedHelpers.findAndHookMethod(
                localDisplayDevice,
                "requestDisplayStateLocked",
                Int::class.java,
                Float::class.java,
                Float::class.java,
                displayOffloadSessionClass,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        try {
                            val localDisplayAdapter = XposedHelpers.getSurroundingThis(param.thisObject)
                            val ctx = XposedHelpers.callMethod(localDisplayAdapter, "getOverlayContext") as Context
                            val targetBright = param.args[1] as Float

                            val enable = getBoolean("pref_enable", true)
                            val preEnable = XposedHelpers.getAdditionalInstanceField(param.thisObject, "preEnable")
                            XposedHelpers.setAdditionalInstanceField(param.thisObject, "preEnable", enable)

                            if (!enable) {
                                if (preEnable is Boolean && preEnable) {
                                    Settings.Secure.putInt(ctx.contentResolver, "reduce_bright_colors_activated", 0)
                                }
                                return
                            }

                            val minScreenBright = getFloat("pref_min_screen_bright", 1f)
                            if (targetBright >= minScreenBright || 
                                (targetBright < 0 && getBoolean("pref_disable_on_screenoff", false))) {
                                Settings.Secure.putInt(ctx.contentResolver, "reduce_bright_colors_level", 0)
                            } else if (targetBright >= 0) {
                                val dim = (1 - (targetBright / minScreenBright)) * getInt("pref_max_dim_strength", 90)
                                if (checkWriteSettingsPermission(ctx)) {
                                    Settings.Secure.putInt(ctx.contentResolver, "reduce_bright_colors_level", dim.toInt())
                                }
                                Settings.Secure.putInt(ctx.contentResolver, "reduce_bright_colors_activated", 1)
                                param.args[1] = minScreenBright
                            }
                        } catch (e: Throwable) {
                           
                        }
                    }

                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val localDisplayAdapter = XposedHelpers.getSurroundingThis(param.thisObject)
                            val ctx = XposedHelpers.callMethod(localDisplayAdapter, "getOverlayContext") as Context
                            val targetBright = param.args[1] as Float

                            Settings.System.putFloat(ctx.contentResolver, HAL_SCREEN_BRIGHTNESS, targetBright)
                            
                            val level = Settings.Secure.getInt(ctx.contentResolver, "reduce_bright_colors_level", 0)
                            Settings.System.putInt(ctx.contentResolver, REDUCE_BRIGHT_LEVEL, level)
                        } catch (e: Throwable) {
                      
                        }
                    }
                }
            )

            val displayPowerController = XposedHelpers.findClass(
                "com.android.server.display.DisplayPowerController",
                lpparam.classLoader
            )

            XposedBridge.hookAllMethods(
                displayPowerController,
                "applyReduceBrightColorsSplineAdjustment",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (getBoolean("pref_enable", true)) param.result = null
                    }
                }
            )

            XposedBridge.hookAllMethods(
                displayPowerController,
                "handleRbcChanged",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (getBoolean("pref_enable", true)) param.result = null
                    }
                }
            )

        } catch (e: Throwable) {
        }
    }

    private fun getBoolean(key: String, defValue: Boolean): Boolean {
        return try {
            if (prefs.hasFileChanged()) prefs.reload()
            prefs.getBoolean(key, defValue)
        } catch (e: Throwable) {
            defValue
        }
    }

    private fun getFloat(key: String, defValue: Float): Float {
        return try {
            if (prefs.hasFileChanged()) prefs.reload()
            prefs.getFloat(key, defValue)
        } catch (e: Throwable) {
            defValue
        }
    }

    private fun getInt(key: String, defValue: Int): Int {
        return try {
            if (prefs.hasFileChanged()) prefs.reload()
            prefs.getInt(key, defValue)
        } catch (e: Throwable) {
            defValue
        }
    }

    private fun checkWriteSettingsPermission(context: Context): Boolean {
        return try {
            Settings.System.canWrite(context)
        } catch (e: Throwable) {
            false
        }
    }
}
