# Changelog

## 11.14.0 (2026-02-16)

### DevoxxGenie Integration

- **Editor intention action**: When the DevoxxGenie plugin is installed, a "DevoxxGenie: Fix '...'" action appears in the lightbulb menu for SonarLint issues, sending issue details and code context to DevoxxGenie for AI-assisted fix suggestions.
- **"Fix with DevoxxGenie" button**: A styled button in the rule description header panel sends a structured prompt containing the rule name, issue message, file location, and a ~20-line code snippet to DevoxxGenie.
- **Reflective bridge**: Uses runtime reflection to communicate with DevoxxGenie's `ExternalPromptService`, so there is no compile-time dependency. The integration activates automatically when DevoxxGenie is installed and works with any LLM provider configured in DevoxxGenie.

### Fork Changes

- Changed plugin ID from `org.sonarlint.idea` to `org.sonarlint.idea.devoxxgenie` to prevent the official SonarLint marketplace release from overwriting the fork.
- Updated plugin name to "SonarQube with DevoxxGenie".
- Updated plugin overview description to highlight the DevoxxGenie integration.
- Fixed safe property access for Artifactory Gradle extras (`extra.has()` check) to avoid build failures without `gradle.properties`.

### Base Version

Based on SonarLint for IntelliJ 11.13 (commit `d8f85ccc`).
