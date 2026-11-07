package com.example.model

enum class IssueLevel {
    ERROR,
    WARNING,
    INFO
}

data class ValidationIssue(
    val level: IssueLevel,
    val line: Int,
    val column: Int = 0,
    val message: String,
    val suggestion: String? = null
)

data class ValidationResult(
    val isValid: Boolean,
    val issues: List<ValidationIssue>,
    val doc: AnimeDoc? = null,
    val summary: String = ""
) {
    val errorCount: Int get() = issues.count { it.level == IssueLevel.ERROR }
    val warningCount: Int get() = issues.count { it.level == IssueLevel.WARNING }
}
