package com.jck44.autoscantalk

import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import java.util.*
import java.util.logging.Logger

class FocusTimerTask(private val buttons: List<ActionButton>) : TimerTask() {

    private val logger = Logger.getLogger(javaClass.name)
    private val numberOfButtonsZeroBased = buttons.count() - 1
    private var number = numberOfButtonsZeroBased
    override fun run() {

        if(number < 0)
            resetButtonNumber()

        logger.info("Focus on ${buttons.first().javaClass.name} number:${number+1}/${numberOfButtonsZeroBased+1}")
        doAsync{
            uiThread { buttons[number--].setFocused() }
        }
    }
    override fun cancel(): Boolean {
        resetButtonNumber()
        return super.cancel()
    }

    private fun resetButtonNumber(){
        number = numberOfButtonsZeroBased
    }
}