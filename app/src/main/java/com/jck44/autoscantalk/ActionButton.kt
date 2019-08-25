package com.jck44.autoscantalk

import android.content.Context
import android.util.AttributeSet
import android.widget.Button
import java.util.logging.Logger


/**
 * TODO: document your custom view class.
 */
class ActionButton(context: Context, attrs: AttributeSet) : Button(context, attrs) {

    private val logger = Logger.getLogger(javaClass.name)
    private var audioHintText = "Beispiel Audio Hinweis"
    private var audioMessage = "Beispiel Audio Nachricht"

    fun setFocused(){
        requestFocus()
        logger.info("$id has focus: ${hasFocus()}")
    }
}

