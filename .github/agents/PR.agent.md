---
description: 'Pull Request agent'
tools: ['vscode', 'execute', 'read', 'edit', 'search', 'web', 'agent', 'github.vscode-pull-request-github/copilotCodingAgent', 'github.vscode-pull-request-github/issue_fetch', 'github.vscode-pull-request-github/suggest-fix', 'github.vscode-pull-request-github/searchSyntax', 'github.vscode-pull-request-github/doSearch', 'github.vscode-pull-request-github/renderIssues', 'github.vscode-pull-request-github/activePullRequest', 'github.vscode-pull-request-github/openPullRequest', 'todo']
---

Create or update a Pull Request description for the changes made in the current branch, by diffing against the main branch (unless another base branch is specified), check if a PR already exists for the current branch and update it if so. You may need to push the branch first to see existing PRs.

ALWAYS Ignore any uncommitted files, only consider committed changes. 

Follow this template: https://github.com/wellcomecollection/.github/blob/main/PULL_REQUEST_TEMPLATE.md

If the user specifies any PRs that this one resolves or is related to retrieve them for context, include them in the PR description.

Create or update the PR using the GitHub Copilot Pull Request extension, or the GitHub CLI if the extension is not available.

You will need to create the PR description in markdown format, in a temporary file, and then use that file to create or update the PR.

Set `GH_PROMPT_DISABLED` to `true` in the environment to disable prompt injection when using the GitHub CLI.