
# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.

cmake_minimum_required(VERSION 3.22.1)

# Declares and names the project.

project("transcoder")

include_directories(
        include)

set(ffmpeg_libs_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs)

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.

add_library( # Sets the name of the library.
        transcoder
        # Sets the library as a shared library.
        SHARED
        # Provides a relative path to your source file(s).
        transcoder.cpp)

add_library(ffmpeg_libavcodec SHARED IMPORTED)
set_target_properties(ffmpeg_libavcodec PROPERTIES IMPORTED_LOCATION
        ${ffmpeg_libs_DIR}/${ANDROID_ABI}/libavcodec.so)

add_library(ffmpeg_libavfilter SHARED IMPORTED)
set_target_properties(ffmpeg_libavfilter PROPERTIES IMPORTED_LOCATION
        ${ffmpeg_libs_DIR}/${ANDROID_ABI}/libavfilter.so)

add_library(ffmpeg_libavformat SHARED IMPORTED)
set_target_properties(ffmpeg_libavformat PROPERTIES IMPORTED_LOCATION
        ${ffmpeg_libs_DIR}/${ANDROID_ABI}/libavformat.so)

add_library(ffmpeg_libavutil SHARED IMPORTED)
set_target_properties(ffmpeg_libavutil PROPERTIES IMPORTED_LOCATION
        ${ffmpeg_libs_DIR}/${ANDROID_ABI}/libavutil.so)

add_library(ffmpeg_libswresample SHARED IMPORTED)
set_target_properties(ffmpeg_libswresample PROPERTIES IMPORTED_LOCATION
        ${ffmpeg_libs_DIR}/${ANDROID_ABI}/libswresample.so)

# Searches for a specified prebuilt library and stores the path as a
# variable. Because CMake includes system libraries in the search path by
# default, you only need to specify the name of the public NDK library
# you want to add. CMake verifies that the library exists before
# completing its build.

find_library( # Sets the name of the path variable.
              log-lib
              # Specifies the name of the NDK library that
              # you want CMake to locate.
              log )

# Specifies libraries CMake should link to your target library. You
# can link multiple libraries, such as libraries you define in this
# build script, prebuilt third-party libraries, or system libraries.

target_link_libraries( # Specifies the target library.
                        transcoder
                        ffmpeg_libavcodec
                        ffmpeg_libavfilter
                        ffmpeg_libavformat
                        ffmpeg_libavutil
                        ffmpeg_libswresample
                        # Links the target library to the log library
                        # included in the NDK.
                        ${log-lib} )
