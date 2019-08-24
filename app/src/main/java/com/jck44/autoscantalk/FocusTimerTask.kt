package com.jck44.autoscantalk

import android.view.View
import java.util.*
import java.util.logging.Logger

class FocusTimerTask(private val buttons: List<ActionButton>) : TimerTask() {

    private val logger = Logger.getLogger(javaClass.name)
    private var number = buttons.count() - 1
    override fun run() {

        for(button in buttons.asReversed())
            button.removeFocus()

        //buttons[number].setFocued()
        if(number < 0)
            number = buttons.count() - 1

        logger.info("Focus Button number:${number}")
        buttons[number--].setFocued()
//
        //TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

}