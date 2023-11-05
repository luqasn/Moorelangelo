/*
 * This Kotlin source file was generated by the Gradle 'init' task.
 */
package org.luqasn.stateviz

import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.com.intellij.psi.PsiManager
import org.jetbrains.kotlin.com.intellij.testFramework.LightVirtualFile
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.plainContent

private val project by lazy {
    KotlinCoreEnvironment.createForProduction(
        Disposer.newDisposable(),
        CompilerConfiguration(),
        EnvironmentConfigFiles.JVM_CONFIG_FILES //Can be JS/NATIVE_CONFIG_FILES for non JVM projects
    ).project
}

fun createKtFile(codeString: String, fileName: String) =
    PsiManager.getInstance(project)
        .findFile(
            LightVirtualFile(fileName, KotlinFileType.INSTANCE, codeString)
        ) as KtFile

fun parse(code: String): List<StateMachine> {
    val file = createKtFile(code, "main.kt")

    return file.children.flatMap { element: PsiElement ->
        visit(element)
    }
}

fun visit(element: PsiElement): List<StateMachine> = when (element) {
    is KtElement -> inspectElement(element).toList()
    else -> emptyList()
}

private fun KtExpression.hasName(s: String): Boolean {
    if (this !is KtNameReferenceExpression)
        return false
    return this.getReferencedNameAsName().asString() == s
}

fun inspectElement(element: KtElement): Sequence<StateMachine> = sequence {
    yieldAll(element.children.flatMap { visit(it) })

    when (element) {
        is KtDotQualifiedExpression -> {
            if (!element.receiverExpression.hasName("StateMachine"))
                return@sequence
            val selectorExpression = element.selectorExpression as? KtCallExpression ?: return@sequence
            if (selectorExpression.calleeExpression?.hasName("create") != true)
                return@sequence
            val body = selectorExpression.lambdaArguments.firstOrNull()?.getLambdaExpression()?.bodyExpression ?: error(
                "expected lambda argument to StateMachine.create"
            )
            val statements = evaluateStateMachine(body.children)

            yield(StateMachine(statements))
        }
    }
}

fun evaluateStateMachine(children: Array<PsiElement>): List<Statement> {
    return children.filterIsInstance<KtCallExpression>().mapNotNull {
        when (val call = it.calleeExpression) {
            is KtNameReferenceExpression -> when (call.getReferencedNameAsName().asString()) {
                "initialState" -> Statement.InitialState(it.valueArgumentList!!.arguments.first().text)
                "state" -> parseState(it)
                else -> null
            }

            else -> null
        }
    }
}

fun evaluateState(state: String, children: Array<PsiElement>): Statement.State {
    val transitions = mutableListOf<Transition>()
    val onExit = mutableListOf<String>()
    val onEnter = mutableListOf<String>()
    children.filterIsInstance<KtCallExpression>().forEach {
        when (val call = it.calleeExpression) {
            is KtNameReferenceExpression -> when (call.getReferencedNameAsName().asString()) {
                "on" -> {
                    val eventType = it.typeArguments.firstOrNull()?.text
                    if (eventType != null) {
                        transitions.addAll(
                            evaluateTransitions(
                                state,
                                eventType,
                                it.lambdaArguments.first().getLambdaExpression()!!.bodyExpression!!.children
                            )
                        )
                    } else {
                        transitions.addAll(
                            evaluateTransitions(
                                state,
                                it.valueArguments.first().getArgumentExpression()!!.unquote(),
                                it.lambdaArguments.first().getLambdaExpression()!!.bodyExpression!!.children
                            )
                        )
                    }

                }

                "onEnter" -> {
                    val body = it.lambdaArguments.first().getLambdaExpression()!!.bodyExpression!!

                    onEnter.add(body.text)
                }

                "onExit" -> {
                    val body = it.lambdaArguments.first().getLambdaExpression()!!.bodyExpression!!

                    onExit.add(body.text)
                }
            }
        }
    }
    return Statement.State(state, transitions, onEnter, onExit)
}

fun evaluateTransitions(state: String, event: String, children: Array<PsiElement>) = sequence {
    children.filterIsInstance<KtCallExpression>().forEach {
        when (val call = it.calleeExpression) {
            is KtNameReferenceExpression -> when (call.getReferencedNameAsName().asString()) {
                "transitionTo" -> {
                    val args = it.valueArgumentList!!.arguments
                    val argumentExpression = args.first().getArgumentExpression()!!

                    when (argumentExpression) {
                        is KtCallExpression -> {
                            yield(
                                Transition(
                                    event = event,
                                    targetState = argumentExpression.calleeExpression!!.text,
                                    targetArgs = argumentExpression.valueArguments.map {
                                        it.text
                                    },
                                    sideEffect = args.elementAtOrNull(1)?.getArgumentExpression()?.unquote()
                                )
                            )
                        }

                        else -> yield(
                            Transition(
                                event = event,
                                targetState = argumentExpression.unquote(),
                                sideEffect = args.elementAtOrNull(1)?.getArgumentExpression()?.unquote()
                            )
                        )
                    }


                }

                "dontTransition" -> {
                    val args = it.valueArgumentList!!.arguments

                    yield(
                        Transition(
                            event = event,
                            targetState = state,
                            sideEffect = args.firstOrNull()?.getArgumentExpression()?.unquote()
                        )
                    )
                }
            }
        }
    }
}

fun parseState(expression: KtCallExpression): Statement.State {
    val stateType = expression.typeArguments.firstOrNull()
    val stateDefinition = expression.lambdaArguments.first().getLambdaExpression()!!.bodyExpression!!.children

    return if (stateType != null) {
        evaluateState(stateType.text, stateDefinition)
    } else {
        val firstArg = expression.valueArguments.first()
        val state = firstArg.getArgumentExpression()!!.unquote()
        evaluateState(state, stateDefinition)
    }
}

private fun KtExpression.unquote(): String {
    val state = when (this) {
        is KtStringTemplateExpression -> this.plainContent
        else -> this.text
    }
    return state
}
