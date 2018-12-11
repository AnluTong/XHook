package me.andrew.xhook;

import android.text.TextUtils;

import com.android.dx.Code;
import com.android.dx.DexMaker;
import com.android.dx.FieldId;
import com.android.dx.Local;
import com.android.dx.MethodId;
import com.android.dx.TypeId;

import java.lang.reflect.Constructor;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

/**
 * created by andrew.tong
 * on 18-12-11.
 */

class MethodGenHelper {

    public static final String INVOKER_METHOD = "_Invoker";
    public static final String INVOKER_CONSTRUCT = "init_Invoker";

    private static Map<Class<?>, String> PRIMITIVE_TO_SIGNATURE = new HashMap<>(9);
    private static Map<Class<?>, Class<?>> PRIMITIVE_TO_BOXING = new HashMap<>(9);
    private static MessageDigest mMessageDigest;

    static {
        PRIMITIVE_TO_SIGNATURE.put(byte.class, "B");
        PRIMITIVE_TO_SIGNATURE.put(char.class, "C");
        PRIMITIVE_TO_SIGNATURE.put(short.class, "S");
        PRIMITIVE_TO_SIGNATURE.put(int.class, "I");
        PRIMITIVE_TO_SIGNATURE.put(long.class, "J");
        PRIMITIVE_TO_SIGNATURE.put(float.class, "F");
        PRIMITIVE_TO_SIGNATURE.put(double.class, "D");
        PRIMITIVE_TO_SIGNATURE.put(void.class, "V");
        PRIMITIVE_TO_SIGNATURE.put(boolean.class, "Z");

        PRIMITIVE_TO_BOXING.put(byte.class, Byte.class);
        PRIMITIVE_TO_BOXING.put(char.class, Character.class);
        PRIMITIVE_TO_BOXING.put(short.class, Short.class);
        PRIMITIVE_TO_BOXING.put(int.class, Integer.class);
        PRIMITIVE_TO_BOXING.put(long.class, Long.class);
        PRIMITIVE_TO_BOXING.put(float.class, Float.class);
        PRIMITIVE_TO_BOXING.put(double.class, Double.class);
        PRIMITIVE_TO_BOXING.put(boolean.class, Boolean.class);

        try {
            mMessageDigest = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
        }
    }

    /**
     * gen method invoker(origin implement) in new dex
     *
     * @param dexMaker
     * @param declaringType
     * @param m
     */
    public static void generateInvokerFromMethod(DexMaker dexMaker, TypeId<?> declaringType, Method m) {
        generateMethodFromMethod(dexMaker, declaringType, m, null, m.getName() + INVOKER_METHOD);
    }

    /**
     * gen method hook in new dex
     *
     * @param dexMaker
     * @param declaringType
     * @param m
     */
    public static void generateMethodFromMethod(DexMaker dexMaker, TypeId<?> declaringType, Method m, MethodId callback) {
        generateMethodFromMethod(dexMaker, declaringType, m, callback, m.getName());
    }

    /**
     * gen method hook in new dex, with a new name
     *
     * @param dexMaker
     * @param declaringType
     * @param origin
     * @param newName
     */
    public static void generateMethodFromMethod(DexMaker dexMaker, TypeId<?> declaringType, Method origin, MethodId callback, String newName) {
        Class<?>[] pTypes = origin.getParameterTypes();
        TypeId<?> params[] = new TypeId[pTypes.length];
        for (int i = 0; i < pTypes.length; ++i) {
            params[i] = getType(pTypes[i]);
        }
        MethodId proxy = declaringType.getMethod(TypeId.get(origin.getReturnType()), newName, params);
        Code code;
        if (Modifier.isStatic(origin.getModifiers())) {
            code = dexMaker.declare(proxy, Modifier.STATIC | Modifier.PUBLIC);
        } else {
            int mode = Modifier.isPrivate(origin.getModifiers()) ? Modifier.PRIVATE : Modifier.PUBLIC;
            code = dexMaker.declare(proxy, mode);
        }
        //argument declare
        Local<Object[]> args = code.newLocal(TypeId.get(Object[].class));
        Local<Integer> a = code.newLocal(TypeId.INT);
        Local i_ = code.newLocal(TypeId.INT);
        Local arg_ = code.newLocal(TypeId.OBJECT);
        Local localResult = code.newLocal(TypeId.OBJECT);
        Local caller = code.newLocal(TypeId.STRING);
        Local res = code.newLocal(TypeId.get(origin.getReturnType()));
        Local cast = origin.getReturnType().equals(void.class) ? null : code.newLocal(getType(origin.getReturnType()));
        Local<?> thisRef;

        //argument set value
        if (Modifier.isStatic(origin.getModifiers())) {
            thisRef = code.newLocal(declaringType);
            code.loadConstant(thisRef, null);
        } else {
            thisRef = code.getThis(declaringType);
        }
        code.loadConstant(caller, getUID(origin.toString()));
        code.loadConstant(a, proxy.getParameters().size());
        code.newArray(args, a);
        for (int i = 0; i < pTypes.length; ++i) {
            code.loadConstant(i_, i);
            MethodId mId = getValueFromClass(pTypes[i]);
            if (mId != null) {
                code.invokeStatic(mId, arg_, code.getParameter(i, (TypeId) proxy.getParameters().get(i)));
                code.aput(args, i_, arg_);
            } else {
                code.aput(args, i_, code.getParameter(i, (TypeId) proxy.getParameters().get(i)));
            }
        }
        if (callback != null) {
            code.invokeStatic(callback, localResult, caller, thisRef, args);
        }
        if (origin.getReturnType().equals(void.class)) {
            code.returnVoid();
            return;
        }
        if (getValueFromClass(origin.getReturnType()) != null) {
            MethodId mId = toValueFromClass(origin.getReturnType());
            code.cast(cast, localResult);
            code.invokeVirtual(mId, res, cast);
            code.returnValue(res);
        } else {
            code.cast(res, localResult);
            code.returnValue(res);
        }
    }

    /**
     * gen constructor invoker from new dex
     *
     * @param dexMaker
     * @param declaringType
     * @param m
     */
    public static void generateInvokerFromConstructor(DexMaker dexMaker, TypeId<?> declaringType, Constructor m) {
        Class<?>[] pTypes = m.getParameterTypes();
        TypeId<?> params[] = new TypeId[pTypes.length];
        for (int i = 0; i < pTypes.length; ++i) {
            params[i] = getType(pTypes[i]);
        }
        MethodId proxy = declaringType.getMethod(TypeId.get(Void.TYPE), INVOKER_CONSTRUCT, params);
        Code code;
        code = dexMaker.declare(proxy, Modifier.STATIC | Modifier.PUBLIC);
        code.returnVoid();
        return;
    }

    /**
     * gen constructor hook callback invoker from new dex
     *
     * @param dexMaker
     * @param declaringType
     * @param origin
     * @param callback
     */
    public static void generateMethodFromConstructor(DexMaker dexMaker, TypeId<?> declaringType, Constructor origin, MethodId callback) {
        Class<?>[] pTypes = origin.getParameterTypes();
        TypeId<?> params[] = new TypeId[pTypes.length];
        for (int i = 0; i < pTypes.length; ++i) {
            params[i] = getType(pTypes[i]);
        }
        MethodId proxy = declaringType.getConstructor(params);
        Code code = dexMaker.declare(proxy, Modifier.PUBLIC);
        Local<Object[]> args = code.newLocal(TypeId.get(Object[].class));
        Local<Integer> a = code.newLocal(TypeId.INT);
        Local i_ = code.newLocal(TypeId.INT);
        Local arg_ = code.newLocal(TypeId.OBJECT);
        Local localResult = code.newLocal(TypeId.OBJECT);
        Local<?> thisRef = code.getThis(declaringType);
        Local caller = code.newLocal(TypeId.STRING);
        code.loadConstant(caller, getUID(origin.toString()));
        code.invokeDirect(TypeId.OBJECT.getConstructor(), null, thisRef);
        code.loadConstant(a, proxy.getParameters().size());
        code.newArray(args, a);
        for (int i = 0; i < pTypes.length; ++i) {
            code.loadConstant(i_, i);
            MethodId mId = getValueFromClass(pTypes[i]);
            if (mId != null) {
                code.invokeStatic(mId, arg_, code.getParameter(i, (TypeId) proxy.getParameters().get(i)));
                code.aput(args, i_, arg_);
            } else {
                code.aput(args, i_, code.getParameter(i, (TypeId) proxy.getParameters().get(i)));
            }
        }
        code.invokeStatic(callback, localResult, caller, thisRef, args);
        code.returnVoid();
    }

    /**
     * gen default empty constructor, used for dex parser
     *
     * @param dexMaker
     * @param declaringType
     */
    public static void addDefaultConstructor(DexMaker dexMaker, TypeId<?> declaringType) {
        Code code = dexMaker.declare(declaringType.getConstructor(), Modifier.PUBLIC);
        Local<?> thisRef = code.getThis(declaringType);
        code.invokeDirect(TypeId.OBJECT.getConstructor(), null, thisRef);
        code.returnVoid();
    }

    private static TypeId getType(Class<?> clazz) {
        if (clazz.isPrimitive() && !clazz.equals(void.class))
            clazz = PRIMITIVE_TO_BOXING.get(clazz);
        return TypeId.get(clazz);
    }

    /**
     * primitive type return value of
     *
     * @param clazz
     * @return
     */
    private static MethodId getValueFromClass(Class clazz) {
        if (clazz.isPrimitive() && !clazz.equals(void.class)) {
            TypeId type = TypeId.get(PRIMITIVE_TO_BOXING.get(clazz));
            return type.getMethod(type, "valueOf", TypeId.get(clazz));
        } else {
            return null;
        }
    }

    /**
     * primitive type return xxxValue
     *
     * @param clazz
     * @return
     */
    private static MethodId toValueFromClass(Class clazz) {
        if (clazz.isPrimitive() && !clazz.equals(void.class)) {
            String methodName = clazz.getSimpleName().split(".")[0] + "Value";
            TypeId type = TypeId.get(PRIMITIVE_TO_BOXING.get(clazz));
            return type.getMethod(TypeId.get(clazz), methodName);
        } else {
            return null;
        }
    }

    /**
     * get unique id (simplify md5)
     * @param string
     * @return
     */
    public static String getUID(String string) {
        if (TextUtils.isEmpty(string) || mMessageDigest == null) {
            return "";
        }
        byte[] bytes = mMessageDigest.digest(string.getBytes());
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(Integer.toHexString(b & 0xff));
        }
        return result.toString();
    }
}
