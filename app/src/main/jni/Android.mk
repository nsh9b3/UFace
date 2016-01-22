LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

# OpenCV
OPENCV_CAMERA_MODULES   := on
OPENCV_INSTALL_MODULES  := on
include /home/nsh9b3/Android/OpenCV/sdk/native/jni/OpenCV.mk
# OpenCV

LOCAL_LDLIBS    := -llog
LOCAL_MODULE    := face_detection
LOCAL_SRC_FILES := NativeDetectionBasedTracker.cpp

include $(BUILD_SHARED_LIBRARY)