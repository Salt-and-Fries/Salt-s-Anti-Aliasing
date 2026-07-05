cmake_minimum_required(VERSION 3.24)
project(salts_dlss_bridge LANGUAGES CXX)

option(SALTS_DLSS_WITH_STREAMLINE "Build against a local NVIDIA Streamline SDK" OFF)
set(SALTS_STREAMLINE_SDK "" CACHE PATH "Path to NVIDIA Streamline SDK root")
set(SALTS_VULKAN_HEADERS "" CACHE PATH "Path to Vulkan-Headers root")

find_package(JNI REQUIRED)

add_library(salts_dlss_bridge SHARED
    src/salts_dlss_bridge.cpp
)

target_compile_features(salts_dlss_bridge PRIVATE cxx_std_20)
target_include_directories(salts_dlss_bridge PRIVATE ${JNI_INCLUDE_DIRS})
target_link_libraries(salts_dlss_bridge PRIVATE ${JNI_LIBRARIES})

if (WIN32)
    target_compile_definitions(salts_dlss_bridge PRIVATE WIN32_LEAN_AND_MEAN NOMINMAX)
endif()

if (SALTS_DLSS_WITH_STREAMLINE)
    if (NOT EXISTS "${SALTS_STREAMLINE_SDK}/include/sl.h")
        message(FATAL_ERROR "SALTS_STREAMLINE_SDK must point to an NVIDIA Streamline SDK root containing include/sl.h")
    endif()

    target_compile_definitions(salts_dlss_bridge PRIVATE SALTS_DLSS_WITH_STREAMLINE=1)
    target_include_directories(salts_dlss_bridge PRIVATE "${SALTS_STREAMLINE_SDK}/include")

    if (SALTS_VULKAN_HEADERS AND EXISTS "${SALTS_VULKAN_HEADERS}/include/vulkan/vulkan_core.h")
        target_include_directories(salts_dlss_bridge PRIVATE "${SALTS_VULKAN_HEADERS}/include")
    elseif(DEFINED ENV{VULKAN_SDK} AND EXISTS "$ENV{VULKAN_SDK}/Include/vulkan/vulkan_core.h")
        target_include_directories(salts_dlss_bridge PRIVATE "$ENV{VULKAN_SDK}/Include")
    else()
        message(FATAL_ERROR "Set SALTS_VULKAN_HEADERS or install Vulkan SDK; Vulkan headers are required for Streamline Vulkan bridge")
    endif()

    if (WIN32)
        target_link_directories(salts_dlss_bridge PRIVATE "${SALTS_STREAMLINE_SDK}/lib/x64")
        target_link_libraries(salts_dlss_bridge PRIVATE sl.interposer)
    endif()
endif()
