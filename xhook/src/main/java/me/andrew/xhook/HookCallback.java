package me.andrew.xhook;

/**
 * created by andrew.tong
 * on 18-12-11.
 */

public abstract class HookCallback {
    protected void beforeHookedMethod(Param param) throws Throwable { }
    protected void afterHookedMethod(Param param) throws Throwable { }
}
