package com.andreas_kratzer.ghosttalk.core.settings

enum class AutoReadMode(val intervalMinutes: Int) {
    OFF(0),           // Kein Auto-Read (aktuelles Verhalten)
    IMMEDIATE(0),     // Sofort vorlesen (FIFO Queue)
    EVERY_2_MIN(2),
    EVERY_5_MIN(5),
    EVERY_10_MIN(10),
    EVERY_15_MIN(15),
    EVERY_30_MIN(30);
}
