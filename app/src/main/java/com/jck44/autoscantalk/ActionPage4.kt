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
    private val timer = Timer()
    private var timerTask : FocusTimerTask? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_action_page_4)

        val enableAutoScan = resources.getBoolean(R.bool.enableAutoScan)

        if(enableAutoScan)
            startAutoScan()
    }

    private fun startAutoScan(){

        val autoScanStartDelay = resources.getInteger(R.integer.timer_start_delay_milliseconds).toLong()
        val autoScanInterval = resources.getInteger(R.integer.timer_interval_milliseconds).toLong()

        if(timerTask == null)
            timerTask = FocusTimerTask(getChildren(window.decorView))
        timer.scheduleAtFixedRate(timerTask, autoScanStartDelay, autoScanInterval)
    }

    private fun resetAutoScan(){
        timerTask?.cancel()
        timer.purge()
        timerTask = null
        startAutoScan()

    }

    private inline fun <reified T : View> getChildren(view: View): List<T> {
        var childrenOfType = mutableListOf<T>()
        val allChildren = (view as ViewGroup).`access$getAllViews`()
        for(child in allChildren){
            if(child is T)
                childrenOfType.add(child)
        }
        return childrenOfType.toList()
    }

    private fun View.getAllViews(): List<View> {
        if (this !is ViewGroup || childCount == 0) return listOf(this)

        return children
            .toList()
            .flatMap { it.getAllViews() }
            .plus(this as View).asReversed()
    }

    fun clickActionButton(view: View) {
        val currentButton = findViewById<Button>(view.id)
        logger.info("Button (id:${currentButton.id}) clicked.")

        var previousActionsText = ""
        val maxLastSavedActions = resources.getInteger(R.integer.max_last_saved_actions)
        val previousActions = findViewById<TextView>(R.id.textView_previousActions)

        while(lastButtons.count() > maxLastSavedActions) {
            val action = lastButtons.first()

            lastButtons.removeAt(1)
            logger.info("Removed buttonAction with id:${action.id} from ${::lastButtons.name}")
        }
        lastButtons.add(currentButton)
        logger.info("Added buttonAction with id:${currentButton.id} to ${::lastButtons.name}")

        for(lastAction in lastButtons){
            previousActionsText += lastAction.text.toString() + " (id:${lastAction.id})" + System.getProperty("line.separator")
        }

        previousActions.apply { text = previousActionsText.trim()}
        resetAutoScan()
    }

    @PublishedApi
    internal fun View.`access$getAllViews`() = getAllViews()
}
