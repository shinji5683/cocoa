package com.shinji.serena

class AppProfileHelper {

    fun getSuggestedGranularity(packageName: String): GranularityMode? {
        val pkgLower = packageName.lowercase()

        return when {
            pkgLower.contains("chrome") ||
            pkgLower.contains("browser") ||
            pkgLower.contains("firefox") ||
            pkgLower.contains("edge") -> GranularityMode.HEADINGS

            pkgLower.contains("reader") ||
            pkgLower.contains("kindle") ||
            pkgLower.contains("book") ||
            pkgLower.contains("kobo") -> GranularityMode.PARAGRAPHS

            pkgLower.contains("termux") ||
            pkgLower.contains("terminal") ||
            pkgLower.contains("editor") ||
            pkgLower.contains("code") -> GranularityMode.LINES

            pkgLower.contains("line") ||
            pkgLower.contains("chat") ||
            pkgLower.contains("message") -> GranularityMode.CONTROLS

            else -> null
        }
    }
}

