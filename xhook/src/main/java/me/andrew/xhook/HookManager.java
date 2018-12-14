package me.andrew.xhook;

import android.app.Application;
import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.android.dx.DexMaker;
import com.android.dx.MethodId;
import com.android.dx.TypeId;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * created by andrew.tong
 * on 18-12-11.
 */

public class HookManager {
    private static final String TAG = HookManager.class.getSimpleName();


    private volatile static HashMap<String, Backup> mHooked = new HashMap<>();
    private volatile static List<Backup> mNeedHooks = new ArrayList<>();
    private static Context mContext;
    private static MethodId mHookCallback;

    static {
        mHookCallback = TypeId.get(HookManager.class).getMethod(TypeId.OBJECT, "invokeCallback", TypeId.STRING, TypeId.OBJECT, TypeId.get(Object[].class));
    }

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
            Map<String, TypeId> classes = new HashMap<>();
            DexMaker dexMaker = new DexMaker();
            while (it.hasNext()) {
                Backup m = it.next();
                Member old = m.oldMethod;
                if (old == null || isHooked(old)) {
                    it.remove();
                    continue;
                }

                //gen mHooked method in new dex
                String oldClassName = old.getDeclaringClass().getName().replace(".", "_");
                TypeId hookClassType = classes.get(oldClassName);
                if (hookClassType == null) {
                    hookClassType = TypeId.get("L" + oldClassName + ";");
                    Class target = old.getDeclaringClass();
                    if (Modifier.isFinal(target.getModifiers())) {
                        dexMaker.declare(hookClassType, "", Modifier.PUBLIC, TypeId.OBJECT);
                    } else {
                        dexMaker.declare(hookClassType, "", Modifier.PUBLIC, TypeId.get(target));
                    }
                    MethodGenHelper.addDefaultConstructor(dexMaker, hookClassType);
                    classes.put(oldClassName, hookClassType);
                }

                if (old instanceof Method) {
                    MethodGenHelper.generateMethodFromMethod(dexMaker, hookClassType, (Method) old, mHookCallback);
                    MethodGenHelper.generateInvokerFromMethod(dexMaker, hookClassType, (Method) old);
                } else {
                    MethodGenHelper.generateMethodFromConstructor(dexMaker, hookClassType, (Constructor) m.oldMethod, mHookCallback);
                    MethodGenHelper.generateInvokerFromConstructor(dexMaker, hookClassType, (Constructor) m.oldMethod);
                }
            }

            //gen new dex
            File outputDir = new File(context.getDir("path", Context.MODE_PRIVATE).getPath());
            if (outputDir.exists()) {
                File[] fs = outputDir.listFiles();
                for (File f : fs) {
                    f.delete();
                }
            }

            //hook and set callback
            ClassLoader loader = dexMaker.generateAndLoad(context.getClassLoader(), outputDir);
            for (Backup bak : methods) {
                Member m = bak.oldMethod;
                String name = m.getDeclaringClass().getName().replace(".", "_");
                Class<?> cls = loader.loadClass(name);
                Constructor con = cls.getDeclaredConstructor();
                con.newInstance(); //dex has gen default constructor
                Member mem;
                Method invoker;
                if (m instanceof Method) {
                    mem = cls.getDeclaredMethod(m.getName(), ((Method) m).getParameterTypes());
                    invoker = cls.getDeclaredMethod(m.getName() + MethodGenHelper.INVOKER_METHOD, ((Method) m).getParameterTypes());
                } else {
                    mem = cls.getDeclaredConstructor(((Constructor) m).getParameterTypes());
                    invoker = cls.getDeclaredMethod(MethodGenHelper.INVOKER_CONSTRUCT, ((Constructor) m).getParameterTypes());
                }
                if (mem == null || invoker == null) {
                    continue;
                }
                XHook.hookMethod(bak.invoker(invoker).newMethod(mem));
                mHooked.put(MethodGenHelper.getUID(m.toString()), bak);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static boolean isHooked(Member m) {
        if (mHooked.get(MethodGenHelper.getUID(m.toString())) != null) {
            return true;
        } else {
            return false;
        }
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
    public static Object invokeCallback(String caller, Object thiz, Object[] args) {
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