#include <windows.h>
#include <jni.h>
#include <xess/xess_vk.h>
#include <string>
#include <vector>
#include <cstddef>
#include <stdexcept>

// One render thread, one Vulkan device. Never patch or interpose Intel's runtime.
static HMODULE library;
static xess_context_handle_t context;
static VkDevice device;
static PFN_vkDeviceWaitIdle wait_idle;
static uint32_t output_width, output_height, quality;
static constexpr uint32_t init_flags = XESS_INIT_FLAG_LDR_INPUT_COLOR | XESS_INIT_FLAG_INVERTED_DEPTH;

template<class T> static T api(const char* name) {
    auto p = reinterpret_cast<T>(GetProcAddress(library, name));
    if (!p) throw std::runtime_error(std::string("Missing XeSS export: ") + name);
    return p;
}
#define CALL(name, ...) api<decltype(&name)>(#name)(__VA_ARGS__)
static void check(xess_result_t r) {
    if (r >= 0) return;
    const char* reason = "SDK operation failed";
    switch (r) {
        case XESS_RESULT_ERROR_UNSUPPORTED_DEVICE: reason="unsupported GPU"; break;
        case XESS_RESULT_ERROR_UNSUPPORTED_DRIVER: reason="unsupported graphics driver"; break;
        case XESS_RESULT_ERROR_DEVICE_OUT_OF_MEMORY: reason="insufficient GPU memory"; break;
        case XESS_RESULT_ERROR_CANT_LOAD_LIBRARY: reason="required runtime library unavailable"; break;
        case XESS_RESULT_ERROR_INVALID_ARGUMENT: reason="invalid integration parameters"; break;
        default: break;
    }
    throw std::runtime_error(std::string("XeSS ")+reason+" ("+std::to_string(r)+")");
}
static void fail(JNIEnv* env, const std::exception& error) {
    env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), error.what());
}
static jobjectArray strings(JNIEnv* env, const std::vector<std::string>& values) {
    auto array = env->NewObjectArray(static_cast<jsize>(values.size()), env->FindClass("java/lang/String"), nullptr);
    for (size_t i=0; i<values.size(); ++i) {
        auto value=env->NewStringUTF(values[i].c_str()); env->SetObjectArrayElement(array, static_cast<jsize>(i), value); env->DeleteLocalRef(value);
    }
    return array;
}
static void release() {
    if (context) { if (device && wait_idle) wait_idle(device); check(CALL(xessDestroyContext, context)); context=nullptr; }
    output_width=output_height=quality=0;
}
#define JNI_METHOD(name) Java_org_betterLostItems_salts_1anti_1aliasing_client_render_vulkan_xess_XessNativeBridge_##name

extern "C" JNIEXPORT jstring JNICALL JNI_METHOD(load)(JNIEnv* env, jclass, jstring path) {
    try {
        if (!library) {
            const jchar* chars=env->GetStringChars(path,nullptr);
            std::wstring filename(reinterpret_cast<const wchar_t*>(chars), env->GetStringLength(path));
            library=LoadLibraryExW(filename.c_str(),nullptr,LOAD_LIBRARY_SEARCH_DLL_LOAD_DIR|LOAD_LIBRARY_SEARCH_DEFAULT_DIRS);
            env->ReleaseStringChars(path,chars);
            if (!library) throw std::runtime_error("Cannot load libxess.dll (Windows error " + std::to_string(GetLastError()) + ")");
        }
        xess_version_t v{}; check(CALL(xessGetVersion,&v));
        return env->NewStringUTF((std::to_string(v.major)+"."+std::to_string(v.minor)+"."+std::to_string(v.patch)).c_str());
    } catch(const std::exception& e) { fail(env,e); return nullptr; }
}
extern "C" JNIEXPORT jobjectArray JNICALL JNI_METHOD(instanceRequirements)(JNIEnv* env,jclass) {
    try {
        uint32_t count=0,version=0; const char* const* names=nullptr;
        check(CALL(xessVKGetRequiredInstanceExtensions,&count,&names,&version));
        std::vector<std::string> out{"api:"+std::to_string(version)};
        for(uint32_t i=0;i<count;++i) out.emplace_back(std::string("instance:")+names[i]);
        return strings(env,out);
    } catch(const std::exception& e) { fail(env,e); return nullptr; }
}
// Return every requested VkBool32 by its official LWJGL struct and member offset.
// Java resolves the member name and merges it with Minecraft's existing feature set.
template<class T> static void features(std::vector<std::string>& out,const VkBaseOutStructure* base,const char* name,size_t first,size_t end=sizeof(T)) {
    auto bytes=reinterpret_cast<const char*>(base);
    for(size_t offset=first;offset+sizeof(VkBool32)<=end;offset+=sizeof(VkBool32))
        if(*reinterpret_cast<const VkBool32*>(bytes+offset)==VK_TRUE)
            out.emplace_back(std::string("feature:")+name+":"+std::to_string(offset));
}
extern "C" JNIEXPORT jobjectArray JNICALL JNI_METHOD(deviceRequirements)(JNIEnv* env,jclass,jlong instance,jlong physical) {
    try {
        auto vk_instance=reinterpret_cast<VkInstance>(instance); auto gpu=reinterpret_cast<VkPhysicalDevice>(physical);
        uint32_t count=0; const char* const* names=nullptr;
        check(CALL(xessVKGetRequiredDeviceExtensions,vk_instance,gpu,&count,&names));
        std::vector<std::string> out;
        for(uint32_t i=0;i<count;++i) out.emplace_back(std::string("device:")+names[i]);
        VkPhysicalDeviceFeatures2 core{VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FEATURES_2}; void* chain=&core;
        check(CALL(xessVKGetRequiredDeviceFeatures,vk_instance,gpu,&chain));
        for(auto p=static_cast<VkBaseOutStructure*>(chain);p;p=p->pNext) {
            switch(p->sType) {
                case VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FEATURES_2:
                    features<VkPhysicalDeviceFeatures2>(out,p,"VkPhysicalDeviceFeatures2",offsetof(VkPhysicalDeviceFeatures2,features),offsetof(VkPhysicalDeviceFeatures2,features)+sizeof(VkPhysicalDeviceFeatures)); break;
                case VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_2_FEATURES:
                    features<VkPhysicalDeviceVulkan12Features>(out,p,"VkPhysicalDeviceVulkan12Features",offsetof(VkPhysicalDeviceVulkan12Features,samplerMirrorClampToEdge),offsetof(VkPhysicalDeviceVulkan12Features,subgroupBroadcastDynamicId)+sizeof(VkBool32)); break;
                case VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_3_FEATURES:
                    features<VkPhysicalDeviceVulkan13Features>(out,p,"VkPhysicalDeviceVulkan13Features",offsetof(VkPhysicalDeviceVulkan13Features,robustImageAccess),offsetof(VkPhysicalDeviceVulkan13Features,maintenance4)+sizeof(VkBool32)); break;
                case VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SHADER_FLOAT16_INT8_FEATURES:
                    features<VkPhysicalDeviceShaderFloat16Int8Features>(out,p,"VkPhysicalDeviceShaderFloat16Int8Features",offsetof(VkPhysicalDeviceShaderFloat16Int8Features,shaderFloat16)); break;
                case VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_MUTABLE_DESCRIPTOR_TYPE_FEATURES_EXT:
                    features<VkPhysicalDeviceMutableDescriptorTypeFeaturesEXT>(out,p,"VkPhysicalDeviceMutableDescriptorTypeFeaturesEXT",offsetof(VkPhysicalDeviceMutableDescriptorTypeFeaturesEXT,mutableDescriptorType),offsetof(VkPhysicalDeviceMutableDescriptorTypeFeaturesEXT,mutableDescriptorType)+sizeof(VkBool32)); break;
                case VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SHADER_INTEGER_DOT_PRODUCT_FEATURES:
                    features<VkPhysicalDeviceShaderIntegerDotProductFeatures>(out,p,"VkPhysicalDeviceShaderIntegerDotProductFeatures",offsetof(VkPhysicalDeviceShaderIntegerDotProductFeatures,shaderIntegerDotProduct),offsetof(VkPhysicalDeviceShaderIntegerDotProductFeatures,shaderIntegerDotProduct)+sizeof(VkBool32)); break;
                default: throw std::runtime_error("Unsupported XeSS feature structure " + std::to_string(p->sType));
            }
        }
        return strings(env,out);
    } catch(const std::exception& e) { fail(env,e); return nullptr; }
}
extern "C" JNIEXPORT void JNICALL JNI_METHOD(create)(JNIEnv* env,jclass,jlong instance,jlong physical,jlong logical) {
    try {
        release(); device=reinterpret_cast<VkDevice>(logical);
        auto loader=GetModuleHandleW(L"vulkan-1.dll");
        auto get_device_proc=reinterpret_cast<PFN_vkGetDeviceProcAddr>(GetProcAddress(loader,"vkGetDeviceProcAddr"));
        if(!get_device_proc) throw std::runtime_error("Vulkan loader unavailable");
        wait_idle=reinterpret_cast<PFN_vkDeviceWaitIdle>(get_device_proc(device,"vkDeviceWaitIdle"));
        if(!wait_idle) throw std::runtime_error("Vulkan device idle function unavailable");
        check(CALL(xessVKCreateContext,reinterpret_cast<VkInstance>(instance),reinterpret_cast<VkPhysicalDevice>(physical),device,&context));
        // Start compilation during loading, before the first world frame consumes XeSS.
        check(CALL(xessVKBuildPipelines,context,VK_NULL_HANDLE,false,init_flags));
    } catch(const std::exception& e) {
        // A failed pipeline warm-up still owns a context. Retire it before reporting failure.
        try { release(); } catch (...) {}
        fail(env,e);
    }
}
extern "C" JNIEXPORT jintArray JNICALL JNI_METHOD(settings)(JNIEnv* env,jclass,jint width,jint height,jint preset) {
    try {
        if(!context || width<=0 || height<=0 || preset<100 || preset>106) throw std::runtime_error("Invalid XeSS settings");
        xess_2d_t output{static_cast<uint32_t>(width),static_cast<uint32_t>(height)},optimal{},minimum{},maximum{};
        check(CALL(xessGetOptimalInputResolution,context,&output,static_cast<xess_quality_settings_t>(preset),&optimal,&minimum,&maximum));
        if(output_width!=output.x || output_height!=output.y || quality!=static_cast<uint32_t>(preset)) {
            if(wait_idle) wait_idle(device);
            xess_vk_init_params_t init{}; init.outputResolution=output; init.qualitySetting=static_cast<xess_quality_settings_t>(preset);
            // Scene is linear SDR with reversed-Z depth and undilated, unjittered pixel velocities.
            init.initFlags=init_flags;
            check(CALL(xessVKInit,context,&init));
            output_width=output.x; output_height=output.y; quality=preset;
        }
        jint values[]{static_cast<jint>(optimal.x),static_cast<jint>(optimal.y)};
        auto out=env->NewIntArray(2); env->SetIntArrayRegion(out,0,2,values); return out;
    } catch(const std::exception& e) { fail(env,e); return nullptr; }
}
extern "C" JNIEXPORT jint JNICALL JNI_METHOD(execute)(JNIEnv* env,jclass,jlong command,jlongArray handles,jintArray formats,jint width,jint height,jfloat jitter_x,jfloat jitter_y,jboolean reset) {
    try {
        if(!context || !command || env->GetArrayLength(handles)!=8 || env->GetArrayLength(formats)!=4) throw std::runtime_error("Invalid XeSS frame resources");
        jlong h[8]; jint f[4]; env->GetLongArrayRegion(handles,0,8,h); env->GetIntArrayRegion(formats,0,4,f);
        xess_vk_execute_params_t p{};
        xess_vk_image_view_info* resources[]{&p.colorTexture,&p.outputTexture,&p.depthTexture,&p.velocityTexture};
        for(int i=0;i<4;++i) {
            auto& r=*resources[i]; r.image=reinterpret_cast<VkImage>(h[2*i]); r.imageView=reinterpret_cast<VkImageView>(h[2*i+1]);
            r.format=static_cast<VkFormat>(f[i]); r.width=i==1?output_width:width; r.height=i==1?output_height:height;
            r.subresourceRange={static_cast<VkImageAspectFlags>(i==2?VK_IMAGE_ASPECT_DEPTH_BIT:VK_IMAGE_ASPECT_COLOR_BIT),0,1,0,1};
        }
        // Match the projection's -2*jitterY/height. Velocities are current-to-previous in pixels.
        p.jitterOffsetX=jitter_x; p.jitterOffsetY=-jitter_y; p.exposureScale=1.0f;
        p.resetHistory=reset?1:0; p.inputWidth=width; p.inputHeight=height;
        return CALL(xessVKExecute,context,reinterpret_cast<VkCommandBuffer>(command),&p);
    } catch(const std::exception& e) { fail(env,e); return -1000; }
}
extern "C" JNIEXPORT void JNICALL JNI_METHOD(destroy)(JNIEnv* env,jclass) {
    try { release(); } catch(const std::exception& e) { fail(env,e); }
}
