# IntelliJ review focus

- Trace UI access to the EDT and expensive work to background execution before flagging a threading problem.
- Check PSI/project-model access against read actions and object lifetimes, using existing platform helpers.
- Follow cancellation across asynchronous work and callbacks after project, module, or UI disposal.
