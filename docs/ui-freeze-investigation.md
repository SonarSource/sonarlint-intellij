# Investigating Marketplace UI freezes

Use the shared [analyze-freeze skill](../.agents/skills/analyze-freeze/SKILL.md) to investigate existing
[Marketplace freeze reports](https://plugins.jetbrains.com/plugin/7973-sonarqube-for-ide/edit/freezes).
The workflow uses the report's message, stack traces, generated analysis and full dump attachments,
then matches the evidence to the affected plugin release. See the [JetBrains announcement](https://blog.jetbrains.com/platform/2026/10/ui-freeze-reports-in-marketplace/)
for background.

## Access and evidence

Sign in with a JetBrains account that can manage the plugin. Open a report and make its available
attachments accessible to the agent through an authenticated browser or a local export. Keep the
report URL, metadata, message and stack traces with the exported full dumps and generated report;
include coroutine dumps, JFR recordings or metrics when available. Preserve attachment names and
sample timestamps. Missing fields or attachments can remain unknown.

Keep raw bundles outside the repository, for example in a temporary directory. Review diagnostic
content for sensitive information before sharing it. An agent that reaches a login page needs
authenticated access or an export before it can analyze the private report.

## Invoking the skill

The skill lives once in `.agents/skills/analyze-freeze/SKILL.md`. Agents that discover repository
skills can invoke `analyze-freeze`; any agent can read the file explicitly. For example:

```text
Read .agents/skills/analyze-freeze/SKILL.md and use it to analyze this Marketplace report:
https://plugins.jetbrains.com/plugin/7973-sonarqube-for-ide/edit/freezes?groupPath=Non-cancellable+Read+Action+in+SonarLintIntelliJClient%2Forg.sonarlint.intellij.SonarLintIntelliJClient.toClientFileDto&reportId=2105931336668938240
Diagnose without editing code. Cite the evidence, match the affected plugin release,
and distinguish it from the current checkout. Identify any information you cannot access.
```

For an exported bundle, replace the URL with its local directory:

```text
Read .agents/skills/analyze-freeze/SKILL.md and investigate the exported Marketplace report
in /tmp/marketplace-freeze-2105931336668938240. Diagnose without editing code;
include confidence, missing evidence and validation for any proposed fix.
```

The linked report is an invocation example, not a verified diagnosis. Existing Marketplace dumps
normally contain the IDE threads executing our plugin. Additional SLOOP diagnostics are useful only
when an identified dependency on that separate process cannot be explained by the report.

## Optional future reporting configuration

Existing reports can be analyzed without changing plugin reporting. A future migration could replace
the `org.sonarlint.intellij.errorsubmitter.BlameSonarSource` `errorHandler` registration in
[`plugin.xml`](../src/main/resources/META-INF/plugin.xml) with:

```xml
<errorHandler implementation="com.intellij.diagnostic.JetBrainsMarketplaceErrorReportSubmitter"/>
```

This JetBrains handler is available since IntelliJ Platform 2023.3, within our current 2024.2 minimum.
It sends exceptions and freeze reports to Marketplace through manual reporting or automatic
submission according to the user's IDE reporting preferences. In Split Mode, the handler must be
registered in a frontend or shared module. Consult the [official configuration guide](https://plugins.jetbrains.com/docs/marketplace/error-handler.html)
before migrating. This investigation workflow leaves the existing reporter and Flight Recorder in place.
