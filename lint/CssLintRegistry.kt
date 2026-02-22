package com.example.cssintegration.lint

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.Vendor
import com.android.tools.lint.detector.api.CURRENT_API

class CssLintRegistry : IssueRegistry() {
    override val issues = listOf(CssStyleAnnotationDetector.ISSUE)
    override val api = CURRENT_API
    override val vendor = Vendor(
        vendorName = "CssIntegration",
        identifier = "com.example.cssintegration",
        feedbackUrl = "https://github.com/your-org/css-integration/issues"
    )
}
