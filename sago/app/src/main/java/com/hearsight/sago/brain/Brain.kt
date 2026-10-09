package com.hearsight.sago.brain

/** What Sago decided to do with an utterance. Gemma produces these from Day 2. */
sealed interface BrainResult {
    data class Answer(val text: String) : BrainResult
    data class Action(val name: String, val parameters: Map<String, String>) : BrainResult
}

/** The LLM layer. It decides; the ActionRouter (Day 2) executes. */
interface Brain {
    suspend fun respond(utterance: String): BrainResult
}

/** Day 1 placeholder so the voice loop can be tested end to end before Gemma lands. */
class EchoBrain : Brain {
    override suspend fun respond(utterance: String): BrainResult =
        BrainResult.Answer("You said: $utterance")
}
