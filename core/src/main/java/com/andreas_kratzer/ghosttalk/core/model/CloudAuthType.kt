package com.andreas_kratzer.ghosttalk.core.model

enum class CloudAuthType {
    SYSTEM,   // Bisheriger systemweiter Google-Login via Account-Manager
    WEB_FLOW  // Neuer, isolierter In-App OAuth2-Login via Custom Tabs
}
