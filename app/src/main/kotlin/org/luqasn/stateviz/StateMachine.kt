package org.luqasn.stateviz

data class StateMachine(val statements: List<Statement>)

sealed class Statement {
    data class InitialState(val state: String) : Statement()

    data class State(
        val name: String,
        val transitions: List<Transition>,
        val onEnter: List<String>,
        val onExit: List<String>
    ) : Statement()
}

data class Transition(val event: String, val targetState: String, val targetArgs: List<String> = emptyList(), val sideEffect: String? = null)

