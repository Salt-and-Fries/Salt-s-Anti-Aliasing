#pragma once

#include <cstdint>
#include <string>

namespace salts::dlss_clear {
// Scope the image-initialization destination correction to synchronous DLSS evaluation.
struct EvaluationScope {
    EvaluationScope();
    ~EvaluationScope();
    EvaluationScope(const EvaluationScope&) = delete;
    EvaluationScope& operator=(const EvaluationScope&) = delete;
};
bool enable_layer(const std::wstring& directory);
uint64_t barrier_count();
uint64_t device_count();
}
