package me.andrew.xhook;

import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;

/**
 * created by andrew.tong
 * on 18-12-11.
 */

class XHook extends Object {
    int superFlag;

    static {
        System.loadLibrary("xhook");
        try {
            computeSuperOffset(Object.class.getDeclaredFields()[0], XHook.class.getDeclaredField("superFlag"));
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }
    }

    public static boolean makeClassSuper(Class cls) {
        try {
            setClassAsSuper(cls.getField(MethodGenHelper.DEFAULT_GEN_FIELD_NAME));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static void hookMethod(MethodBackup old) {
        try {
            long bak = replaceNativeArt(old.getOldMethod(), old.getNewMethod(), old.getInvoker());
            old.setBackAddr(bak);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static native long replaceNativeArt(Member src, Member new_, Method invoker);

    private static native boolean setClassAsSuper(Field fld);

    private static native void computeSuperOffset(Field fld, Field test);

    public static int computeAccessFlag() {
        return 1;
    }

    public static final void m1() {
    }

    public static final void m2() {
    }
}
