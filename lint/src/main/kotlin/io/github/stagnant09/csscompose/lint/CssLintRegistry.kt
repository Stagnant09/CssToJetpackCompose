package io.github.stagnant09.csscompose.lint

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.Vendor
import com.android.tools.lint.detector.api.CURRENT_API

class CssLintRegistry : IssueRegistry() {
    override val issues = listOf(CssStyleAnnotationDetector.ISSUE)
    override val api = CURRENT_API
    override val vendor = Vendor(
        vendorName = "CssIntegration",
        identifier = "io.github.stagnant09.csscompose",
        feedbackUrl = "https://github.com/your-org/css-integration/issues"
    )
}
