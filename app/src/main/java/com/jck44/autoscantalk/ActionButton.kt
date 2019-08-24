package com.jck44.autoscantalk

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.AsyncTask
import android.util.AttributeSet
import android.widget.Button


/**
 * TODO: document your custom view class.
 */
class ActionButton(context: Context, attrs: AttributeSet) : Button(context, attrs) {

    private var audioHintText = "Beispiel Audio Hinweis"
    private var audioMessage = "Beispiel Audio Nachricht"
    val defaultBackground = background

    fun setFocued(){
        val gd = GradientDrawable()
        gd.setColor(-0xff0100) // Changes this drawbale to use a single color instead of a gradient
        gd.cornerRadius = 5f
        gd.setStroke(1, -0x1000000)
        background = gd
        //TODO("Just for testing")


    }

    fun removeFocus(){
        background = defaultBackground
    }

}

