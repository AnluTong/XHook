package me.andrew.xhook;

import android.app.Application;
import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javassist.ClassPool;
import javassist.CtClass;

/**
 * created by andrew.tong
 * on 18-12-11.
 */

public class HookManager {
    private static final String TAG = HookManager.class.getSimpleName();


    private volatile static HashMap<String, Backup> mHooked = new HashMap<>();
    private volatile static List<Backup> mNeedHooks = new ArrayList<>();
    private static Context mContext;

    public static Context getContext() {
        if (mContext == null) {
            try {
                Class at = Class.forName("android.app.ActivityThread");
                Application current = (Application) at.getDeclaredMethod("currentApplication").invoke(null);
                mContext = current.getBaseContext();
                return mContext;
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        } else {
            return mContext;
        }
    }

    public static void startHook() {
        startArtHook(mNeedHooks);
        mNeedHooks.clear();
    }

    public static void addHookMethod(Class<?> clazz, String methodName, Object[] params, HookCallback callback) {
        try {
            if (callback == null || clazz == null || TextUtils.isEmpty(methodName)) return;
            if (params == null) params = new Object[0];
            Method m = clazz.getDeclaredMethod(methodName, transformParameterClasses(clazz.getClassLoader(), params));
            mNeedHooks.add(new Backup().oldMethod(m).callback(callback));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void addHookConstructor(Class<?> clazz, Object[] params, HookCallback callback) {
        try {
            if (callback == null || clazz == null) return;
            if (params == null) params = new Object[0];
            Constructor m = clazz.getDeclaredConstructor(transformParameterClasses(clazz.getClassLoader(), params));
            mNeedHooks.add(new Backup().oldMethod(m).callback(callback));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void startArtHook(List<Backup> methods) {
        try {
            Context context = getContext();
            if (context == null) return;

            Iterator<Backup> it = methods.iterator();
            Map<String, CtClass> classes = new HashMap<>();
            ClassPool pool = ClassPool.getDefault(context);
            while (it.hasNext()) {
                Backup m = it.next();
                Member old = m.oldMethod;
                if (old == null || isHooked(old)) {
                    it.remove();
                    continue;
                }

                //gen mHooked method in new dex
                Class<?> oldClass = old.getDeclaringClass();
                String hookName = oldClass.getName().replace(".", "_");
                CtClass hookClass = classes.get(hookName);
                if (hookClass == null) {
                    hookClass = MethodGenHelper.makeNewClass(pool, old.getDeclaringClass(), hookName);
                    classes.put(hookName, hookClass);
                }

                if (old instanceof Method) {
                    MethodGenHelper.addHookMethod(pool, hookClass, (Method) old);
                } else if (old instanceof Constructor) {
                    MethodGenHelper.addHookConstructor(pool, hookClass, (Constructor) old);
                }
            }

            //get new dex class loader
            ClassLoader loader = MethodGenHelper.getNewClassLoader(getContext(), classes.values());
            if (loader == null) return;

            //actual hook
            for (Backup bak : methods) {
                Member m = bak.oldMethod;
                String name = m.getDeclaringClass().getName().replace(".", "_");
                Class<?> cls = loader.loadClass(name);
                Constructor con = cls.getDeclaredConstructor();
                con.newInstance(); //dex has gen default constructor
                Member mem;
                Method backup;
                if (m instanceof Method) {
                    mem = cls.getDeclaredMethod(m.getName(), ((Method) m).getParameterTypes());
                    backup = cls.getDeclaredMethod(MethodGenHelper.getMethodBackup(m.getName()), ((Method) m).getParameterTypes());
                } else {
                    mem = cls.getDeclaredConstructor(((Constructor) m).getParameterTypes());
                    backup = cls.getDeclaredMethod(MethodGenHelper.getConstructorBackup(), ((Constructor) m).getParameterTypes());
                }
                XHook.hookMethod(bak.invoker(backup).newMethod(mem));
                mHooked.put(m.toString(), bak);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static boolean isHooked(Member m) {
        return mHooked.containsKey(m.toString());
    }

    private static Class<?>[] transformParameterClasses(ClassLoader classLoader, Object[] params) {
        Class[] result = new Class[params.length];
        for (int i = 0; i < params.length; ++i) {
            Object type = params[i];
            if (type instanceof Class) {
                result[i] = (Class<?>) type;
            } else if (type instanceof String) {
                try {
                    result[i] = classLoader.loadClass((String) type); //((String) type, classLoader);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                throw new NullPointerException("parameter type must either be specified as Class or String");
            }
        }
        return result;
    }

    /**
     * 源函数被调用的回调
     *
     * @param caller
     * @param thiz
     * @param args
     * @return
     */
    static Object invokeCallback(String caller, Object thiz, Object[] args) {
        Log.d(TAG, "hook method invoke!");

        Backup back = mHooked.get(caller);
        HookCallback callback = (back == null) ? null : (HookCallback) back.callback;
        if (callback == null) {
            return null;
        }

        Member old = back.invoker;
        if (old == null) {
            return null;
        }

        if (old instanceof Method) {
            ((Method) old).setAccessible(true);
        } else {
            ((Constructor) old).setAccessible(true);
        }

        Param param = new Param();
        param.method = old;
        param.thisObject = thiz;
        param.args = args;
        try {
            callback.beforeHookedMethod(param);
        } catch (Throwable t) {
            t.printStackTrace();
        }

        if (param.callOrigin) {
            try {
                param.result = ((Method) old).invoke(thiz, args);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        try {
            callback.afterHookedMethod(param);
        } catch (Throwable t) {
            t.printStackTrace();
        }
        return param.result;
    }
}