package me.andrew.xhook;

import android.content.Context;
import android.support.annotation.Keep;
import android.support.annotation.NonNull;

import com.android.dex.DexFormat;
import com.android.dex.util.FileUtils;
import com.android.dx.cf.direct.DirectClassFile;
import com.android.dx.cf.direct.StdAttributeFactory;
import com.android.dx.cf.iface.ParseException;
import com.android.dx.dex.DexOptions;
import com.android.dx.dex.cf.CfOptions;
import com.android.dx.dex.cf.CfTranslator;
import com.android.dx.dex.file.ClassDefItem;
import com.android.dx.dex.file.DexFile;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtMethod;

/**
 * created by andrew.tong
 * on 18-12-11.
 * <p>
 * used for gen code like this
 * <p>
 * public class me_andrew_testhook_MainActivity {
 * private byte showToast(String str, int i) {
 * Object invokeCallback = HookManager.invokeCallback("ea1e4cc68ce5162464e69e99fb66985", this, new Object[]{str, Integer.valueOf(i)});
 * return invokeCallback == null ? (byte) 0 : ((Byte) invokeCallback).byteValue();
 * }
 * <p>
 * <p>
 * private byte showToast_Invoker(String str, int i) {
 * return (byte) 0;
 * }
 * }
 */

@Keep
class MethodGenHelper {

    private static Map<Class<?>, Class<?>> PRIMITIVE_TO_BOXING = new HashMap<>(9);
    private static final String BACKUP_METHOD = "_Backup";
    private static final String BACKUP_CONSTRUCTOR = "init_Backup";

    static {
        PRIMITIVE_TO_BOXING.put(byte.class, Byte.class);
        PRIMITIVE_TO_BOXING.put(char.class, Character.class);
        PRIMITIVE_TO_BOXING.put(short.class, Short.class);
        PRIMITIVE_TO_BOXING.put(int.class, Integer.class);
        PRIMITIVE_TO_BOXING.put(long.class, Long.class);
        PRIMITIVE_TO_BOXING.put(float.class, Float.class);
        PRIMITIVE_TO_BOXING.put(double.class, Double.class);
        PRIMITIVE_TO_BOXING.put(boolean.class, Boolean.class);
    }

    /**
     * make hook class in new dex, with a default constructor
     *
     * @param pool
     * @param refClazz
     * @param name
     * @return
     */
    public static CtClass makeNewClass(@NonNull ClassPool pool, @NonNull Class refClazz, @NonNull String name) {
        CtClass hookClass = null;
        try {
            hookClass = pool.makeClass(name);
            hookClass.setSuperclass(Modifier.isFinal(refClazz.getModifiers())
                    ? pool.get(Object.class.getName())
                    : pool.get(refClazz.getName()));
            CtConstructor ct = new CtConstructor(null, hookClass);
            ct.setBody(null);
            hookClass.addConstructor(ct);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return hookClass;
    }

    /**
     * add two method in newClass, old(hook entrance) and old_Backup(hook back)
     * be care of boxing
     *
     * @param pool
     * @param newClass
     * @param old
     */
    public static void addHookMethod(@NonNull ClassPool pool, @NonNull CtClass newClass, @NonNull Method old) {
        try {
            Class<?>[] pTypes = old.getParameterTypes();
            CtClass[] typeClass = new CtClass[pTypes.length];
            StringBuilder typeString = new StringBuilder();
            for (int i = 0; i < pTypes.length; ++i) {
                typeClass[i] = pool.get(pTypes[i].getName());
                if (pTypes[i].isPrimitive()) {
                    typeString.append("new ").append(getBoxingClassName(pTypes[i])).append("($").append(i + 1).append(")");
                } else {
                    typeString.append("$").append(i + 1);
                }
                if (i < pTypes.length - 1) typeString.append(",");
            }
            Class<?> returnType = ((Method) old).getReturnType();
            boolean isStatic = Modifier.isStatic(old.getModifiers());
            CtMethod mBackup = new CtMethod(pool.get(returnType.getName()), old.getName() + BACKUP_METHOD, typeClass, newClass);
            mBackup.setModifiers(isStatic ? Modifier.STATIC | Modifier.PUBLIC
                    : Modifier.isPrivate(old.getModifiers()) ? Modifier.PRIVATE : Modifier.PUBLIC);
            mBackup.setBody(null);
            newClass.addMethod(mBackup);

            CtMethod mRouter = new CtMethod(pool.get(returnType.getName()), old.getName(), typeClass, newClass);
            mRouter.setModifiers(isStatic ? Modifier.STATIC | Modifier.PUBLIC
                    : Modifier.isPrivate(old.getModifiers()) ? Modifier.PRIVATE : Modifier.PUBLIC);

            StringBuilder body = new StringBuilder();
            body.append("{java.lang.Object invokeCallback=").append(MethodGenHelper.class.getName());
            body.append(".invokeCallback(\"").append(old.toString()).append("\",");
            body.append(isStatic ? "null" : "this").append(",new java.lang.Object[");
            body.append(pTypes.length == 0 ? "0]);" : "]{" + typeString + "});");
            if (void.class.equals(returnType)) {
                body.append("}");
            } else if (returnType.isPrimitive()) {
                String shortType = returnType.getSimpleName();
                body.append("return ((").append(getBoxingClassName(returnType)).append(")(invokeCallback==null?new ")
                        .append(getBoxingClassName(returnType)).append("((").append(shortType).append(")0)")
                        .append(":invokeCallback)).").append(shortType).append("Value()").append(";}");
            } else {
                String shortType = returnType.getName();
                body.append("return (").append(shortType).append(")invokeCallback;}");
            }
            mRouter.setBody(body.toString());
            newClass.addMethod(mRouter);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * gen constructor in new dex
     *
     * @param pool
     * @param newClass
     * @param old
     */
    public static void addHookConstructor(@NonNull ClassPool pool, @NonNull CtClass newClass, @NonNull Constructor old) {
        try {
            Class<?>[] pTypes = old.getParameterTypes();
            CtClass[] typeClass = new CtClass[pTypes.length];
            StringBuilder typeString = new StringBuilder();
            for (int i = 0; i < pTypes.length; ++i) {
                typeClass[i] = pool.get(pTypes[i].getName());
                if (pTypes[i].isPrimitive()) {
                    typeString.append("new ").append(getBoxingClassName(pTypes[i])).append("($").append(i + 1).append(")");
                } else {
                    typeString.append("$").append(i + 1);
                }
                if (i < pTypes.length - 1) typeString.append(",");
            }
            CtMethod mBackup = new CtMethod(pool.get(void.class.getName()), BACKUP_CONSTRUCTOR, typeClass, newClass);
            mBackup.setModifiers(Modifier.isStatic(old.getModifiers()) ? Modifier.STATIC | Modifier.PUBLIC
                    : Modifier.isPrivate(old.getModifiers()) ? Modifier.PRIVATE : Modifier.PUBLIC);
            mBackup.setBody(null);
            newClass.addMethod(mBackup);

            CtConstructor mRouter = new CtConstructor(typeClass, newClass);
            mRouter.setModifiers(Modifier.isStatic(old.getModifiers()) ? Modifier.STATIC | Modifier.PUBLIC
                    : Modifier.isPrivate(old.getModifiers()) ? Modifier.PRIVATE : Modifier.PUBLIC);

            StringBuilder body = new StringBuilder();
            body.append("{").append(MethodGenHelper.class.getName());
            body.append(".invokeCallback(\"").append(old.toString()).append("\",this,new java.lang.Object[");
            body.append(pTypes.length == 0 ? "0]);" : "]{" + typeString + "});}");
            mRouter.setBody(body.toString());
            newClass.addConstructor(mRouter);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * gen class file and dex file, finally gen class loader
     *
     * @param context
     * @param classes
     * @return
     */
    public static ClassLoader getNewClassLoader(Context context, Collection<CtClass> classes) {
        //gen new dex
        File outputDir = new File(context.getCacheDir(), "hook");
        if (outputDir.exists()) {
            remove(outputDir);
        }
        outputDir.mkdir();

        for (CtClass ct : classes) {
            try {
                ct.writeFile(outputDir.getAbsolutePath()); //write classes
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                ct.detach();
            }
        }

        File[] fs = outputDir.listFiles();
        if (fs == null || fs.length == 0) return null;

        final CfOptions cfOptions = new CfOptions();
        final DexOptions dxOption = new DexOptions();
        dxOption.targetApiLevel = DexFormat.API_NO_EXTENDED_OPCODES;
        final DexFile outputDex = new DexFile(dxOption);
        for (File f : fs) {
            try {
                String fixName = f.getAbsolutePath();
                byte[] bytes = FileUtils.readFile(fixName);
                DirectClassFile classFile = new DirectClassFile(bytes, fixName, false);
                classFile.setAttributeFactory(StdAttributeFactory.THE_ONE);
                ClassDefItem clazz = CfTranslator.translate(classFile, null, cfOptions, dxOption, outputDex);
                outputDex.add(clazz);
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }

        File result = new File(outputDir, "Generated.jar");
        try (
                JarOutputStream jarOut = new JarOutputStream(new FileOutputStream(result))) {
            byte[] dex = outputDex.toDex(null, false);
            result.createNewFile();
            JarEntry entry = new JarEntry(DexFormat.DEX_IN_JAR_NAME);
            entry.setSize(dex.length);
            jarOut.putNextEntry(entry);
            jarOut.write(dex);
            jarOut.closeEntry();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return generateClassLoader(result, outputDir, context.getClassLoader());
    }

    public static String getMethodBackup(String old) {
        return old + BACKUP_METHOD;
    }

    public static String getConstructorBackup() {
        return BACKUP_CONSTRUCTOR;
    }

    private static String getBoxingClassName(Class clazz) {
        Class tar = MethodGenHelper.PRIMITIVE_TO_BOXING.get(clazz);
        return tar == null ? "" : tar.getName();
    }

    private static ClassLoader generateClassLoader(File result, File dexCache, ClassLoader parent) {
        try {
            return (ClassLoader) Class.forName("dalvik.system.DexClassLoader")
                    .getConstructor(String.class, String.class, String.class, ClassLoader.class)
                    .newInstance(result.getPath(), dexCache.getAbsolutePath(), null,
                            parent);
        } catch (ClassNotFoundException var5) {
            throw new UnsupportedOperationException("load() requires a Dalvik VM", var5);
        } catch (InvocationTargetException var6) {
            throw new RuntimeException(var6.getCause());
        } catch (InstantiationException var7) {
            throw new AssertionError();
        } catch (NoSuchMethodException var8) {
            throw new AssertionError();
        } catch (IllegalAccessException var9) {
            throw new AssertionError();
        }
    }

    private static void remove(File dir) {
        File files[] = dir.isDirectory() ? dir.listFiles() : null;
        if (files != null) {
            for (int i = 0; i < files.length; i++) {
                if (files[i].isDirectory()) {
                    remove(files[i]);
                } else {
                    //删除文件
                    files[i].delete();
                }
            }
        }
        //删除目录
        dir.delete();
    }

    public static Object invokeCallback(String caller, Object thiz, Object[] args) {
        return HookManager.invokeCallback(caller, thiz, args);
    }
}
