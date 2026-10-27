# Investigating Marketplace UI freezes

Open the [Marketplace Freezes page](https://plugins.jetbrains.com/plugin/7973-sonarqube-for-ide/edit/freezes)
and select a report. Sign in with a JetBrains account that can manage the plugin.

Ask your agent to use the shared [analyze-freeze skill](../.agents/skills/analyze-freeze/SKILL.md):

```text
Read .agents/skills/analyze-freeze/SKILL.md and analyze <Marketplace report URL>.
Use the message, stack traces, generated report and full dumps.
Cite the evidence, check the affected plugin version and suggest a minimal fix if justified.
Do not edit code.
```

If the agent cannot access the private report, download its attachments and save the report URL,
metadata, message and stack traces alongside them. Replace the URL in the prompt with that directory's
path. Keep diagnostic files outside Git and review sensitive content before sharing.

Existing reports need no reporting configuration changes. For a future migration, see
[JetBrains' Marketplace reporting guide](https://plugins.jetbrains.com/docs/marketplace/error-handler.html).
