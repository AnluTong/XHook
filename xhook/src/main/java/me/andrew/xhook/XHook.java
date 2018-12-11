package me.andrew.xhook;

import java.lang.reflect.Member;
import java.lang.reflect.Method;

/**
 * created by andrew.tong
 * on 18-12-11.
 */

public class XHook extends Object {

    static {
        System.loadLibrary("xhook");
    }

    public static void hookMethod(MethodBackup old) {
        try {
            replaceNativeArt(old.getOldMethod(), old.getNewMethod(), old.getInvoker());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static native void replaceNativeArt(Member src, Member new_, Method invoker);

    public static final void m1() {}

    public static final void m2() {}
}
