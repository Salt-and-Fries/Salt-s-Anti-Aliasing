cmake_minimum_required(VERSION 3.24)
project(salts_fsr_bridge LANGUAGES CXX)

option(SALTS_FSR_WITH_FFX_SDK "Build against AMD FidelityFX SDK v1.x" ON)
set(SALTS_FFX_SDK "" CACHE PATH "Path to AMD FidelityFX SDK v1.x root")
set(SALTS_VULKAN_HEADERS "" CACHE PATH "Path to Vulkan-Headers root")

find_package(JNI REQUIRED)

add_library(salts_fsr_bridge SHARED
    src/salts_fsr_bridge.cpp
)

target_compile_features(salts_fsr_bridge PRIVATE cxx_std_20)
target_include_directories(salts_fsr_bridge PRIVATE ${JNI_INCLUDE_DIRS})
target_link_libraries(salts_fsr_bridge PRIVATE ${JNI_LIBRARIES} ${CMAKE_DL_LIBS})

if (WIN32)
    target_compile_definitions(salts_fsr_bridge PRIVATE WIN32_LEAN_AND_MEAN NOMINMAX)
endif()

if (SALTS_FSR_WITH_FFX_SDK)
    if (NOT EXISTS "${SALTS_FFX_SDK}/ffx-api/include/ffx_api/ffx_upscale.h")
        message(FATAL_ERROR "SALTS_FFX_SDK must point to an AMD FidelityFX SDK v1.x root containing ffx-api/include/ffx_api/ffx_upscale.h")
    endif()

    target_compile_definitions(salts_fsr_bridge PRIVATE SALTS_FSR_WITH_FFX_SDK=1)
    target_include_directories(salts_fsr_bridge PRIVATE "${SALTS_FFX_SDK}/ffx-api/include")

    if (SALTS_VULKAN_HEADERS AND EXISTS "${SALTS_VULKAN_HEADERS}/include/vulkan/vulkan_core.h")
        target_include_directories(salts_fsr_bridge PRIVATE "${SALTS_VULKAN_HEADERS}/include")
    elseif(DEFINED ENV{VULKAN_SDK} AND EXISTS "$ENV{VULKAN_SDK}/Include/vulkan/vulkan_core.h")
        target_include_directories(salts_fsr_bridge PRIVATE "$ENV{VULKAN_SDK}/Include")
    else()
        message(FATAL_ERROR "Set SALTS_VULKAN_HEADERS or install Vulkan SDK; Vulkan headers are required for FidelityFX Vulkan bridge")
    endif()
endif()
