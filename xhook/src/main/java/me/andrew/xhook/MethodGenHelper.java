package me.andrew.xhook;

import android.text.TextUtils;

import com.android.dx.Code;
import com.android.dx.Comparison;
import com.android.dx.DexMaker;
import com.android.dx.Label;
import com.android.dx.Local;
import com.android.dx.MethodId;
import com.android.dx.TypeId;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

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
     * @param origin
     */
    public static void generateInvokerFromMethod(DexMaker dexMaker, TypeId<?> declaringType, Method origin) {
        Class<?>[] pTypes = origin.getParameterTypes();
        TypeId<?> params[] = new TypeId[pTypes.length];
        Class<?> returnType = origin.getReturnType();
        for (int i = 0; i < pTypes.length; ++i) {
            params[i] = TypeId.get(pTypes[i]);
        }
        MethodId proxy = declaringType.getMethod(TypeId.get(returnType), origin.getName() + INVOKER_METHOD, params);
        Code code = dexMaker.declare(proxy, Modifier.isStatic(origin.getModifiers()) ? Modifier.STATIC | Modifier.PUBLIC
                : Modifier.isPrivate(origin.getModifiers()) ? Modifier.PRIVATE : Modifier.PUBLIC);

        Local res = code.newLocal(TypeId.get(returnType));
        if (returnType.equals(void.class)) {
            code.returnVoid();
        } else if (returnType.isPrimitive()) {
            code.loadConstant(res, 0);
            code.returnValue(res);
        } else {
            code.loadConstant(res, null);
            code.returnValue(res);
        }
    }

    /**
     * gen method hook in new dex
     *
     * @param dexMaker
     * @param declaringType
     * @param origin
     */
    public static void generateMethodFromMethod(DexMaker dexMaker, TypeId<?> declaringType, Method origin, MethodId callback) {
        Class<?>[] pTypes = origin.getParameterTypes();
        TypeId<?> params[] = new TypeId[pTypes.length];
        Class<?> returnType = origin.getReturnType();
        for (int i = 0; i < pTypes.length; ++i) {
            params[i] = TypeId.get(pTypes[i]);
        }
        MethodId proxy = declaringType.getMethod(TypeId.get(returnType), origin.getName(), params);
        Code code = dexMaker.declare(proxy, Modifier.isStatic(origin.getModifiers()) ? Modifier.STATIC | Modifier.PUBLIC
                : Modifier.isPrivate(origin.getModifiers()) ? Modifier.PRIVATE : Modifier.PUBLIC);

        //argument declare
        Local<Object[]> args = code.newLocal(TypeId.get(Object[].class)); //func params
        Local<Integer> a = code.newLocal(TypeId.INT); //temp
        Local i_ = code.newLocal(TypeId.INT); //temp
        Local arg_ = code.newLocal(TypeId.OBJECT);
        Local localResult = code.newLocal(TypeId.OBJECT); //return value
        Local priJudge = code.newLocal(TypeId.OBJECT); //return value
        Local caller = code.newLocal(TypeId.STRING); //callback key name
        Local res = code.newLocal(TypeId.get(returnType));
        Local cast = PRIMITIVE_TO_BOXING.containsKey(returnType) ? code.newLocal(TypeId.get(PRIMITIVE_TO_BOXING.get(returnType))) : null; //temp var to cast primitive value
        Local<?> thisRef; //this ref

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
            MethodId mId = getBoxingMethod(pTypes[i]); //object array need a boxing object
            if (mId != null) {
                code.invokeStatic(mId, arg_, code.getParameter(i, (TypeId) proxy.getParameters().get(i)));
                code.aput(args, i_, arg_);
            } else {
                code.aput(args, i_, code.getParameter(i, (TypeId) proxy.getParameters().get(i)));
            }
        }
        code.invokeStatic(callback, localResult, caller, thisRef, args);
        if (returnType.equals(void.class)) {
            code.returnVoid();
        } else if (!returnType.isPrimitive()) {
            code.cast(res, localResult);
            code.returnValue(res);
        } else {
            code.loadConstant(priJudge, null);
            Label baseCase = new Label();
            code.compare(Comparison.NE, baseCase, localResult, priJudge); //callback result is null
            code.loadConstant(res, 0);
            code.returnValue(res);
            code.mark(baseCase);

            MethodId mId = getPrimitiveMethod(returnType); //callback result not null, primitive return value
            code.cast(cast, localResult);
            code.invokeVirtual(mId, res, cast);
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
            params[i] = TypeId.get(pTypes[i]);
        }
        MethodId proxy = declaringType.getMethod(TypeId.get(Void.TYPE), INVOKER_CONSTRUCT, params);
        Code code = dexMaker.declare(proxy, Modifier.STATIC | Modifier.PUBLIC);
        code.returnVoid();
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
            params[i] = TypeId.get(pTypes[i]);
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
            MethodId mId = getBoxingMethod(pTypes[i]);
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

    /**
     * primitive type return value of
     * <p>
     * say return method Integer.valueOf(int) return Integer
     *
     * @param primitive
     * @return
     */
    private static MethodId getBoxingMethod(Class primitive) {
        if (primitive.isPrimitive() && !primitive.equals(void.class)) {
            TypeId type = TypeId.get(PRIMITIVE_TO_BOXING.get(primitive));
            return type.getMethod(type, "valueOf", TypeId.get(primitive));
        } else {
            return null;
        }
    }

    /**
     * primitive type return xxxValue
     * <p>
     * say return method Integer.intValue() return int
     *
     * @param primitive
     * @return
     */
    private static MethodId getPrimitiveMethod(Class primitive) {
        if (primitive.isPrimitive() && !primitive.equals(void.class)) {
            Class boxingClass = PRIMITIVE_TO_BOXING.get(primitive);
            String methodName = primitive.getSimpleName() + "Value";
            TypeId type = TypeId.get(boxingClass);
            return type.getMethod(TypeId.get(primitive), methodName);
        } else {
            return null;
        }
    }

    /**
     * get unique id (simplify md5)
     *
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
