package com.andreas_kratzer.ghosttalk.core.ai.domain

import org.json.JSONObject

/**
 * Interface for tools that can be called by Gemini Cloud via Function Calling.
 */
interface AiTool {
    /**
     * The name of the function as it should be registered with Gemini.
     */
    val name: String

    /**
     * Whether this tool requires a signed-in Google user (OAuth).
     */
    val requiresAuth: Boolean

    /**
     * A description of what the function does, used by Gemini to decide when to call it.
     */
    val description: String

    /**
     * The JSON schema for the function parameters.
     */
    val parameters: JSONObject

    /**
     * Executes the tool with the provided arguments.
     * @param args A map of argument names to their values.
     * @return A string representation of the result to be sent back to Gemini.
     */
    suspend fun execute(args: Map<String, Any?>): String
}
