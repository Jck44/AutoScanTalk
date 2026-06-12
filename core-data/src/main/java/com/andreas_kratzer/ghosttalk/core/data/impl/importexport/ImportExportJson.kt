package com.andreas_kratzer.ghosttalk.core.data.impl.importexport

import kotlinx.serialization.json.Json

internal val ImportExportJson = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
    encodeDefaults = true
}
