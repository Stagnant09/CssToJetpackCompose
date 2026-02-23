package com.example.cssintegration
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtVisitorVoid

class CssStyleProcessor(
    private val logger: KSPLogger
) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val composables = resolver
            .getSymbolsWithAnnotation("androidx.compose.runtime.Composable")
            .filterIsInstance<KSFunctionDeclaration>()

        composables.forEach { function ->
            val functionText = function.containingFile?.toString() ?: ""
            val callsRememberParsedCss = functionText.contains("rememberParsedCss")

            val hasCssStyle = function.annotations.any {
                println(it.shortName)
                it.shortName.asString() == "CssStyle"
            }

            if (callsRememberParsedCss && !hasCssStyle) {
                logger.error(
                    "Composable '${function.simpleName.asString()}' " +
                            "calls rememberParsedCss(...) but is not annotated with @CssStyle.",
                    function
                )
            }
        }

        return emptyList()
    }
}

fun KtNamedFunction.containsCall(name: String): Boolean {
    var found = false

    accept(object : KtVisitorVoid() {
        override fun visitCallExpression(expression: KtCallExpression) {
            if (expression.calleeExpression.toString() == name) {
                found = true
            }
            super.visitCallExpression(expression)
        }
    })

    return found
}