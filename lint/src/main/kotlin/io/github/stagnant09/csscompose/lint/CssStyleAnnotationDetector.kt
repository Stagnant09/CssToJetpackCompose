package io.github.stagnant09.csscompose.lint

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.*
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.getParentOfType

class CssStyleAnnotationDetector : Detector(), Detector.UastScanner {

    override fun getApplicableUastTypes() = listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext) = object : UElementHandler() {
        override fun visitCallExpression(node: UCallExpression) {
            if (node.methodName != "rememberParsedCss") return

            val containingFunction = node.getParentOfType<UMethod>() ?: return

            val hasAnnotation = containingFunction.annotations
                .any { it.qualifiedName == "io.github.stagnant09.csscompose.CssStyle" }

            if (!hasAnnotation) {
                context.report(
                    issue = ISSUE,
                    scope = containingFunction as? com.intellij.psi.PsiElement ?: return,
                    location = context.getLocation(containingFunction as? com.intellij.psi.PsiElement ?: return),
                    message = "Functions calling `rememberParsedCss()` should be annotated with `@CssStyle` for readability."
                )
            }
        }
    }

    companion object {
        val ISSUE = Issue.create(
            id = "MissingCssStyleAnnotation",
            briefDescription = "Missing @CssStyle annotation",
            explanation = """
                Any composable function that calls `rememberParsedCss()` should also be 
                annotated with `@CssStyle` to make the CSS source visible at the call site
                and to support future KSP-based compile-time processing.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = Implementation(
                CssStyleAnnotationDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}
