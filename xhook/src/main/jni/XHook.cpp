#include <jni.h>
#include <string.h>
#include <cstdint>
//
// Created by andrew on 18-12-6.
//

static const char *HOOK_CLASS = "me/andrew/xhook/XHook";
size_t methodSize = 0; //art method size 相邻方法相减的方式
int accessOffset = -1; //art access setting offset
int superOffset = -1; //art super setting offset

class ArtField {
public:
    uint32_t declaring_class_;
};

static long
replaceNativeArt(JNIEnv *env, jclass clazz, jobject src, jobject new_, jobject invoker) {
    void *mSrc = (void *) env->FromReflectedMethod(src);
    void *mNew_ = (void *) env->FromReflectedMethod(new_);
    size_t *mInvoker = (size_t *) env->FromReflectedMethod(invoker);
    memcpy(mInvoker, mSrc, methodSize);
    *(mInvoker + accessOffset) = *(mInvoker + accessOffset) | 0x0002;
    memcpy(mSrc, mNew_, methodSize);
    return (size_t) mInvoker;
}

static jboolean setClassAsSuper(JNIEnv *env, jclass clazz, jobject flag) {
    ArtField *field = (ArtField *) env->FromReflectedField(flag);
    size_t *dCls = (size_t *) field->declaring_class_;
    if (superOffset == -1) {
        return false;
    } else {
        *(dCls + superOffset) = NULL;
        return true;
    }
}

static void computeSuperOffset(JNIEnv *env, jclass clazz, jobject fld, jobject test) {
    ArtField *field = (ArtField *) env->FromReflectedField(fld);
    ArtField *demo = (ArtField *) env->FromReflectedField(test);
    size_t *dCls = (size_t *) field->declaring_class_;
    size_t *hCls = (size_t *) demo->declaring_class_;
    for (int i = 0; i < 50; ++i) {
        if (*(dCls + i) == NULL && *(hCls + i) == *dCls) {//compute SupperClass offset
            superOffset = i;
            break;
        }
    }
}

static JNINativeMethod gMethods[] = {
        {"replaceNativeArt",   "(Ljava/lang/reflect/Member;Ljava/lang/reflect/Member;Ljava/lang/reflect/Method;)J", (void *) replaceNativeArt},
        {"computeSuperOffset", "(Ljava/lang/reflect/Field;Ljava/lang/reflect/Field;)V",                             (void *) computeSuperOffset},
        {"setClassAsSuper",    "(Ljava/lang/reflect/Field;)Z",                                                      (void *) setClassAsSuper},
};

extern "C"
JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env = nullptr;
    if (vm->GetEnv((void **) &env, JNI_VERSION_1_4) != JNI_OK) {
        return JNI_FALSE;
    }
    jclass classEvaluateUtil = env->FindClass(HOOK_CLASS);
    if (env->RegisterNatives(classEvaluateUtil, gMethods, sizeof(gMethods) / sizeof(gMethods[0])) <
        0) {
        return JNI_FALSE;
    }
    //init art method size
    size_t m1 = reinterpret_cast<size_t>(env->GetStaticMethodID(classEvaluateUtil, "m1", "()V"));
    size_t m2 = reinterpret_cast<size_t>(env->GetStaticMethodID(classEvaluateUtil, "m2", "()V"));
    methodSize = m2 - m1;

    //init art method offset
    size_t *c1 = (size_t *) env->GetStaticMethodID(classEvaluateUtil, "m1", "()V");
    size_t *c2 = (size_t *) env->GetStaticMethodID(classEvaluateUtil, "computeAccessFlag", "()I");
    for (int i = 0; i < methodSize / sizeof(void *); ++i) {
        if (*(c1 + i) == 0x80019 && *(c2 + i) == 0x80009) {
            accessOffset = i;
            break;
        }
    }
    return JNI_VERSION_1_4;
}

