package com.andreas_kratzer.ghosttalk.core.cloud

/**
 * Data class containing information about a local Bridge's SSL certificate.
 */
data class BridgeCertificateInfo(
    val subject: String,
    val issuer: String,
    val validFrom: String,
    val validTo: String,
    val fingerprint: String
)
