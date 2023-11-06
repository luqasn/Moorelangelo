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

data class Transition(
    val event: String,
    val targetState: String,
    val targetArgs: List<String> = emptyList(),
    val sideEffect: String? = null,
    val conditions: List<Condition> = emptyList()
)

sealed class Condition {
    abstract val expression: String

    data class If(override val expression: String) : Condition()
    data class IfNot(override val expression: String) : Condition()
}