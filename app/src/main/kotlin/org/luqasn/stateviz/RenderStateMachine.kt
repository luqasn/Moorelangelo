package org.luqasn.stateviz

fun renderStateMachine(stateMachine: List<Statement>): String {
    val stateToId = stateMachine.filterIsInstance<Statement.State>().mapIndexed { index, state ->
        state.name to "s${index}"
    }.toMap()
    val stateInfo = stateMachine.filterIsInstance<Statement.State>().map { state ->
        val info = state.onEnter.map { "onEnter: $it" } + state.onExit.map { "onExit: $it" }
        val infoString = info.map { it.replace("\n", "<br>").replace("\"", "'") }.joinToString("<br>")
        """state "${state.name}<br>$infoString" as ${stateToId[state.name]}"""
    }
    val statements = stateMachine.map {
        when (it) {
            is Statement.InitialState -> "[*] --> ${stateToId[it.state]}"
            is Statement.State -> {
                val stateIdFrom = stateToId[it.name]
                it.transitions.map {
                    val stateIdTo = stateToId[it.targetState]
                    "$stateIdFrom --> ${stateIdTo}:${it.event} // ${it.sideEffect ?: ""}"
                }.joinToString(
                    separator = "\n"
                )
            }
        }
    }.joinToString(separator = "\n")
    return """
```mermaid    
   stateDiagram-v2
   ${stateInfo.joinToString(separator = "\n")}
   ${statements}
```
    """.trimIndent()
}