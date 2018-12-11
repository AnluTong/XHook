LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_CFLAGS := -Wno-error=format-security -fpermissive
LOCAL_CFLAGS += -fno-rtti -fno-exceptions
LOCAL_CFLAGS += -fvisibility=hidden -O3
LOCAL_CFLAGS	:= -std=gnu++11 -DDEBUG -O0
LOCAL_MODULE := xhook
LOCAL_LDLIBS := -llog
LOCAL_ARM_MODE := arm
LOCAL_LDFLAGS := -Wl,--build-id
LOCAL_SRC_FILES := XHook.cpp

include $(BUILD_SHARED_LIBRARY)