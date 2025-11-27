package com.haizerdev.impactanalysis.model

import java.io.Serializable

/**
 * Impact analysis result
 */
data class ImpactAnalysisResult(
    val changedFiles: List<ChangedFile>,
    val affectedModules: Set<String>,
    val testsToRun: Map<TestType, List<String>>,
    val filesToLint: List<String>,
    val timestamp: Long = System.currentTimeMillis()
) : Serializable

/**
 * Changed file information
 */
data class ChangedFile(
    val path: String,
    val module: String?,
    val changeType: ChangeType,
    val language: FileLanguage?
) : Serializable

/**
 * Git change type
 */
enum class ChangeType {
    ADDED,
    MODIFIED,
    DELETED,
    RENAMED,
    COPIED
}

/**
 * File programming language
 */
enum class FileLanguage(val extensions: List<String>) {
    KOTLIN(listOf("kt", "kts")),
    JAVA(listOf("java")),
    XML(listOf("xml")),
    JSON(listOf("json")),
    GROOVY(listOf("groovy", "gradle")),
    PROPERTIES(listOf("properties")),
    YAML(listOf("yaml", "yml")),
    UNKNOWN(emptyList());

    companion object {
        fun fromExtension(extension: String): FileLanguage {
            return values().find {
                it.extensions.any { ext -> ext.equals(extension, ignoreCase = true) }
            } ?: UNKNOWN
        }

        fun fromPath(path: String): FileLanguage {
            val extension = path.substringAfterLast('.', "")
            return fromExtension(extension)
        }
    }
}
