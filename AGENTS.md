# Project Agent Policy

- Default to using subagents on every non-trivial task in this project.
- Before doing substantial work directly, first check whether part of the task can be delegated to one or more subagents in parallel.
- Prefer spawning at least one subagent for exploration, verification, or implementation whenever the task is more than a tiny one-step action.
- Keep the main agent focused on coordination, integration, and final verification while subagents handle bounded subtasks.
- Only skip subagents for clearly trivial requests where delegation would add unnecessary overhead, or when higher-priority system/developer instructions require keeping the work local.
