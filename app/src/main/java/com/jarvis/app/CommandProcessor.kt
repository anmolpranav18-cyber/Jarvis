package com.jarvis.app

/**
 * Turns a heard phrase into a spoken response.
 *
 * This is intentionally simple rule-matching so the app works fully
 * offline out of the box. To make Jarvis actually "smart" (understand
 * open-ended requests), swap the body of process() for a call to an
 * LLM API (e.g. the Anthropic API) — see the README for how to wire
 * that up without hardcoding your API key in source control.
 */
class CommandProcessor {

    // Mock smart-home state — replace with real device integrations
    // (Google Home / Home Assistant / Matter) when you're ready.
    private var lightsOn = false
    private var thermostat = 70

    fun process(heard: String): String {
        val text = heard.lowercase().trim()

        return when {
            text.isEmpty() ->
                "I didn't catch that."

            "turn on the light" in text || "lights on" in text -> {
                lightsOn = true
                "Lights on."
            }

            "turn off the light" in text || "lights off" in text -> {
                lightsOn = false
                "Lights off."
            }

            "temperature" in text || "thermostat" in text -> {
                "Thermostat is set to $thermostat degrees."
            }

            "set temperature" in text || "set thermostat" in text -> {
                val num = Regex("\\d+").find(text)?.value?.toIntOrNull()
                if (num != null) {
                    thermostat = num
                    "Thermostat set to $thermostat degrees."
                } else {
                    "What temperature would you like?"
                }
            }

            "time" in text ->
                "It's " + java.text.SimpleDateFormat("h:mm a").format(java.util.Date())

            "who are you" in text || "your name" in text ->
                "I'm Jarvis, your assistant."

            "stop listening" in text || "go to sleep" in text ->
                "Standing by."

            else ->
                "I heard: $heard. I don't have a rule for that yet — you can add one in CommandProcessor, or connect an AI API for open-ended requests."
        }
    }
}
