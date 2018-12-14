#include <jni.h>
#include <string.h>
#include <cstdint>
//
// Created by andrew on 18-12-6.
//

static const char *HOOK_CLASS = "me/andrew/xhook/XHook";
size_t methodSize = 0; //art method size 相邻方法相减的方式

class ArtField {
public:
    uint32_t declaring_class_;
    uint32_t access_flags_;
};

static void
replaceNativeArt(JNIEnv *env, jclass clazz, jobject origin_, jobject hook_, jobject invoker_) {
    ArtField *origin = (ArtField *) env->FromReflectedMethod(origin_);
    ArtField *hook = (ArtField *) env->FromReflectedMethod(hook_);
    ArtField *invoker = (ArtField *) env->FromReflectedMethod(invoker_);
    origin->access_flags_ = origin->access_flags_ | 0x0003;
    hook->access_flags_ = hook->access_flags_ | 0x0003;
    invoker->access_flags_ = invoker->access_flags_  | 0x0003;
    memcpy(invoker, origin, methodSize);
    memcpy(origin, hook, methodSize);
}

static JNINativeMethod gMethods[] = {
        {"replaceNativeArt", "(Ljava/lang/reflect/Member;Ljava/lang/reflect/Member;Ljava/lang/reflect/Method;)V", (void *) replaceNativeArt},
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
    return JNI_VERSION_1_4;
}

