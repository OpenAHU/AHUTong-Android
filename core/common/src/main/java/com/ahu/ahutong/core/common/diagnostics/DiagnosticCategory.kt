package com.ahu.ahutong.core.common.diagnostics

/** Upload choices. Collection itself is always on and remains process-local until submission. */
enum class DiagnosticCategory {
    RUNTIME,
    ERROR,
    NETWORK
}
