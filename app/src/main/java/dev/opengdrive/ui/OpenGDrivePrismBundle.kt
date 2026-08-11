package dev.opengdrive.ui

import io.noties.prism4j.annotations.PrismBundle

@PrismBundle(
    include = [
        "c", "cpp", "csharp", "css", "dart", "go", "groovy", "java", "javascript",
        "json", "kotlin", "latex", "makefile", "markdown", "markup", "python", "scala",
        "sql", "swift", "yaml",
    ],
    grammarLocatorClassName = ".OpenGDriveGrammarLocator",
)
internal class OpenGDrivePrismBundle
