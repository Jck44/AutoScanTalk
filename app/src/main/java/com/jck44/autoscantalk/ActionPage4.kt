package com.jck44.autoscantalk

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.view.children
import java.util.*
import java.util.logging.Logger

class ActionPage4 : AppCompatActivity() {

    private var lastButtons = mutableListOf<Button>()
    private val logger = Logger.getLogger(javaClass.name)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_action_page_4)

        val timer = Timer()
        var timerTask = FocusTimerTask(getChildren(window.decorView))
        timer.scheduleAtFixedRate(timerTask, 1000, 1000)


    }

    inline fun <reified T : View> getChildren(view: View): List<T> {
        var childrenOfType = mutableListOf<T>()
        val allChildren = (view as ViewGroup).getAllViews()
        for(child in allChildren){
            if(child is T)
                childrenOfType.add(child)
        }
        return childrenOfType.toList()
    }

    protected fun View.getAllViews(): List<View> {
        if (this !is ViewGroup || childCount == 0) return listOf(this)

        return children
            .toList()
            .flatMap { it.getAllViews() }
            .plus(this as View).asReversed()
    }

    fun actionButton(view: View) {
        var previousActionsText = ""


        val previousActions = findViewById<TextView>(R.id.textView_previousActions)
        val currentButton = findViewById<Button>(view.id)
        while(lastButtons.count() > 5) {
            val action = lastButtons.first()

            lastButtons.removeAt(1)
            logger.info("Removed buttonAction with id:${action.id} from ${::lastButtons.name}")
        }
        lastButtons.add(currentButton)
        logger.info("Added buttonAction with id:${currentButton.id} to ${::lastButtons.name}")

        for(lastAction in lastButtons){
            previousActionsText += lastAction.text.toString() + System.getProperty("line.separator")
        }

        previousActions.apply { text = previousActionsText.trim()}
    }
}
