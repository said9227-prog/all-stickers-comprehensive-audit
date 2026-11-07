package com.example.diagnostics

enum class DiagnosticStage {
    IMPORT,
    EXTRACT,
    VALIDATE,
    STORE,
    URI_CREATE,
    URI_OPEN,
    DECODE,
    PREVIEW,
    CONVERT,
    EXPORT
}

enum class DiagnosticLevel {
    INFO,
    WARNING,
    ERROR
}
