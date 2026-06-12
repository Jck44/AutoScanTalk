package com.andreas_kratzer.ghosttalk.core.call

object CallDurationAnnouncer {

    fun formatDurationAnnouncement(seconds: Int, interval: Int, isEnglish: Boolean): String? {
        if (interval <= 0 || seconds <= 0 || seconds % interval != 0) {
            return null
        }
        return if (isEnglish) {
            if (seconds < 60) {
                "Call duration is $seconds seconds."
            } else {
                val minutes = seconds / 60
                val remainingSeconds = seconds % 60
                val minStr = if (minutes == 1) "minute" else "minutes"
                if (remainingSeconds == 0) {
                    "Call has been active for $minutes $minStr."
                } else {
                    "Call has been active for $minutes $minStr and $remainingSeconds seconds."
                }
            }
        } else {
            if (seconds < 60) {
                "Telefonat dauert seit $seconds Sekunden."
            } else {
                val minutes = seconds / 60
                val remainingSeconds = seconds % 60
                val minStr = if (minutes == 1) "Minute" else "Minuten"
                if (remainingSeconds == 0) {
                    "Telefonat dauert seit $minutes $minStr."
                } else {
                    "Telefonat dauert seit $minutes $minStr und $remainingSeconds Sekunden."
                }
            }
        }
    }

    fun maxDurationReachedText(isEnglish: Boolean): String {
        return if (isEnglish) {
            "Maximum call duration reached. Ending the call."
        } else {
            "Maximale Anrufdauer erreicht. Der Anruf wird beendet."
        }
    }
}
