cmake_minimum_required(VERSION 3.24)
project(salts_xess_bridge LANGUAGES CXX)
find_package(JNI REQUIRED)
set(SALTS_VULKAN_HEADERS "C:/SDKs/Vulkan-Headers" CACHE PATH "Vulkan-Headers root")
add_library(salts_xess_bridge SHARED src/salts_xess_bridge.cpp)
target_compile_features(salts_xess_bridge PRIVATE cxx_std_20)
target_include_directories(salts_xess_bridge PRIVATE ${JNI_INCLUDE_DIRS} include "${SALTS_VULKAN_HEADERS}/include")
target_compile_definitions(salts_xess_bridge PRIVATE WIN32_LEAN_AND_MEAN NOMINMAX VK_NO_PROTOTYPES)

