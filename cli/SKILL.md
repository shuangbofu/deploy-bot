---
name: deploy-bot-cli
description: Use when Codex, Claude Code, OpenCode, or another local coding agent needs to onboard a repository into Deploy Bot, choose a deployment plugin/template, create or update a project/template/pipeline, run deployment precheck, trigger deployment, or inspect deployment logs through the repository CLI and API Token.
---

# Deploy Bot Agent Skill

Use this as an Agent runbook. The skill is the source of truth for the workflow; do not read Deploy Bot source code or the target project source code to guess behavior. Use explicit deployment manifests, user-provided values, existing Deploy Bot resources, CLI help, and CLI outputs instead. Do not ask the user to manually execute CLI commands unless the environment blocks you. The Agent should query Deploy Bot resources, generate JSON, run precheck, and report results.

## Fast Path

1. Verify CLI access.
2. Query existing Deploy Bot resources with narrow list commands.
3. Read the repository deployment manifest if the user provided one.
4. Ask for missing deployment inputs instead of scanning source code.
5. Choose an existing plugin/template.
6. Generate a reviewable `pipeline.json`.
7. Run precheck with `deployment.json`.
8. Ask for confirmation only before writing/updating resources or deploying when target details are unclear or production-like.
9. Trigger deployment only after precheck passes.

## Required Environment

```bash
export DEPLOY_BOT_BASE_URL="https://deploy.example.com"
export DEPLOY_BOT_TOKEN="dbot_xxx"
```

If either variable is missing, stop and ask the user for it. Never use browser login, username/password, cookies, or copied session tokens.

Verify the CLI command first:

```bash
deploy-bot help
```

If `deploy-bot` is not found, the local CLI has not been installed into `PATH`. Do not search the current repository for Deploy Bot source code. First check whether the user provided an explicit CLI path:

```bash
test -x "${DEPLOY_BOT_CLI:-}" && "$DEPLOY_BOT_CLI" help
```

If `DEPLOY_BOT_CLI` is missing, build and expose it only from an explicit local Deploy Bot repository path:

```bash
test -x "$DEPLOY_BOT_REPO/cli/bin/deploy-bot"
mvn -f "$DEPLOY_BOT_REPO/pom.xml" -pl cli -am -DskipTests package
mkdir -p "$HOME/.local/bin"
ln -sf "$DEPLOY_BOT_REPO/cli/bin/deploy-bot" "$HOME/.local/bin/deploy-bot"
```

If `DEPLOY_BOT_REPO` is empty, ask the user for the CLI path or their local Deploy Bot repository path. Never infer `DEPLOY_BOT_REPO` from the current Git repository because the current directory is usually the application being deployed. If `$HOME/.local/bin` is not in `PATH`, either add it for the current shell or call `$HOME/.local/bin/deploy-bot` directly.

Use the repository-local form only when the current working directory is the Deploy Bot repository:

```bash
./cli/bin/deploy-bot help
```

## 60 Second Start

Run these first to verify the token works and collect selectable platform resources. List commands are paged by default with page size 20. Prefer `--keyword`, `--plugin-id`, `--project-id`, `--host-id`, or `--tags` instead of pulling broad pages. Use `--limit` as a short alias of `--page-size`.

```bash
deploy-bot projects list --keyword <repo-or-project-keyword> --limit 20
deploy-bot hosts list --keyword <host-keyword> --limit 20
deploy-bot plugins list
deploy-bot templates list --plugin-id <plugin-id> --limit 20
```

Use the output to match existing project, host, plugin, and template IDs. If a required resource is missing, decide whether to create it or ask the user.

Do not inspect target project source code. If project type, Git URL, branch, build command, artifact path, runtime, startup command, target host, or target directory is unclear, use one of these sources only:

- A deployment manifest explicitly provided by the project or user, such as `deploybot.json`.
- Existing Deploy Bot project, template, pipeline, and plugin-plan outputs.
- Direct user answers.

If a pipeline may already exist, inspect it before creating anything new:

```bash
deploy-bot pipelines list
deploy-bot pipelines detail <pipelineId>
deploy-bot pipelines plugin-plan <pipelineId>
```

Use `pipelines detail` as the source of truth when updating. Use `plugin-plan` to understand the plugin-resolved build/deploy steps and required runtime/config fields.

## Required Inputs Before Writing

Before creating/updating a pipeline, know these values:

- `projectId` or enough data to create a project.
- `targetHostId`.
- `targetDir`.
- `defaultBranch`.
- plugin/template choice: either `templateId`, or `templatePluginId` plus `builtinTemplateKey`.
- required runtime IDs such as `javaEnvironmentId`, `nodeEnvironmentId`, `mavenEnvironmentId`, `runtimeJavaEnvironmentId`.
- required `pluginConfig` keys.

If `targetHostId`, `targetDir`, runtime, branch, or production-like target is ambiguous, ask the user a short question before writing anything.

## Deployment Input Source

Do not detect deployment shape by reading source code. A project that wants Agent onboarding should provide a small deployment manifest, or the user should provide the missing fields during the conversation.

A recommended `deploybot.json` shape:

```json
{
  "projectName": "my-service",
  "gitUrl": "git@github.com:owner/repo.git",
  "defaultBranch": "main",
  "pluginId": "springboot-deployment",
  "builtinTemplateKey": "default",
  "targetDir": "/home/admin/apps/my-service",
  "buildCommand": "mvn clean package -DskipTests",
  "artifactPath": "target/my-service.jar",
  "startupArgs": "--spring.profiles.active=test",
  "startupKeyword": "Started",
  "tags": ["test"]
}
```

This manifest is optional, but missing values must come from platform queries or user input. If the manifest is absent, do not scan `pom.xml`, `package.json`, `Dockerfile`, source directories, or arbitrary files to infer values.

Required values must come from the manifest, platform queries, or direct user answers:

- Git URL and current branch.
- build command.
- artifact/output path.
- runtime requirements.
- startup args and startup keyword for services.
- special copy/move steps that may require a derived template.

## Resource Query Commands

Use these commands to read Deploy Bot state before generating JSON:

```bash
deploy-bot projects list [--page <n>] [--page-size <n>|--limit <n>] [--keyword <kw>] [--git-auth-type <type>]
deploy-bot hosts list [--all] [--page <n>] [--page-size <n>|--limit <n>] [--keyword <kw>] [--type LOCAL|SSH]
deploy-bot runtimes list [--host-id <id>] [--type JAVA|NODE|MAVEN]
deploy-bot plugins list
deploy-bot plugins shell-variables
deploy-bot templates list [--page <n>] [--page-size <n>|--limit <n>] [--keyword <kw>] [--template-type <type>] [--plugin-id <id>] [--monitor-process <true|false>]
deploy-bot pipelines list [--page <n>] [--page-size <n>|--limit <n>] [--keyword <kw>] [--project-id <id>] [--template-id <id>] [--host-id <id>] [--tags <tag>]
deploy-bot pipelines detail <id>
deploy-bot pipelines plugin-plan <id>
```

- `projects list`: find or verify the project for the Git repository.
- `hosts list`: choose the target host ID.
- `runtimes list`: choose Java/Node/Maven runtime IDs for build or service startup.
- `plugins list`: discover supported deployment types and built-in template keys.
- `plugins shell-variables`: inspect variables available inside template scripts.
- `templates list`: find derived templates when defaults are not enough.
- `pipelines list`: avoid duplicate pipelines.
- `pipelines detail`: copy existing values when updating.
- `pipelines plugin-plan`: verify how a pipeline resolves to actual build/deploy steps.

Use command output IDs. Never invent IDs. If the first page does not contain the target, narrow the query before increasing `--page-size`; only page forward when the keyword/filter is already specific.

## JSON Shapes

Project:

```json
{
  "name": "my-service",
  "description": "",
  "gitUrl": "git@github.com:owner/repo.git",
  "gitAuthType": "SSH"
}
```

Pipeline:

```json
{
  "name": "my-service-test",
  "description": "",
  "projectId": 1,
  "templatePluginId": "springboot-deployment",
  "builtinTemplateKey": "default",
  "targetHostId": 1,
  "targetDir": "/home/admin/apps/my-service",
  "defaultBranch": "main",
  "variables": {},
  "tags": ["test"],
  "importantTags": ["test"],
  "javaEnvironmentId": 1,
  "mavenEnvironmentId": 2,
  "runtimeJavaEnvironmentId": 3,
  "pluginConfig": {},
  "startupKeyword": "Started",
  "startupTimeoutSeconds": 120,
  "notificationBindings": []
}
```

Deployment:

```json
{
  "pipelineId": 18,
  "branchName": "main",
  "triggeredBy": "agent",
  "variableOverrides": {},
  "replaceRunning": false
}
```

When updating an existing pipeline, start from `pipelines detail <id>` and preserve fields you are not intentionally changing.

## Write Commands

Use `@file` for reviewable JSON. Use `-` only when piping generated JSON.

```bash
deploy-bot projects create @project.json
deploy-bot templates create @template.json
deploy-bot templates update <id> @template.json
deploy-bot pipelines apply @pipeline.json
deploy-bot pipelines apply --id <id> @pipeline.json
```

- `projects create`: create a project only when the Git repository is not already registered.
- `templates create/update`: create or update a derived template only when default templates cannot express stable project-specific steps.
- `pipelines apply`: create a new pipeline from `pipeline.json`.
- `pipelines apply --id`: update an existing pipeline after reading `pipelines detail <id>` and preserving unrelated fields.

Ask before running write commands unless the user explicitly asked you to create/update.

## Precheck And Deploy

Always precheck before deployment:

```bash
deploy-bot deployments precheck @deployment.json
```

Use precheck to validate pipeline existence, branch, deployment restriction policies, and basic deployability before triggering a deployment.

Only deploy when precheck succeeds:

```bash
deploy-bot deployments run @deployment.json
deploy-bot deployments detail <deploymentId>
deploy-bot deployments logs <deploymentId> --follow
```

- `deployments run`: trigger the deployment.
- `deployments detail`: read final status, snapshot, diff, and execution metadata.
- `deployments logs --follow`: stream deployment logs until completion when the user wants live feedback.

If precheck fails, stop and report the failed items. Do not deploy.

## Plugin And Template Rules

Prefer built-in templates:

- Spring Boot Jar service: Spring Boot deployment plugin.
- Static frontend: Node static deployment plugin.
- Frontend plus Spring Boot backend: fullstack deployment plugin.

Create a derived template only when the default template cannot express stable project-specific steps, such as fixed file rearrangement, extra artifact copy/move, non-standard module layout, or reusable startup wrapping.

Do not create a derived template just for branch, host, deploy directory, runtime version, ordinary build command, ordinary startup args, or variable values.

## Stop And Ask

Ask the user before continuing when:

- target host, deploy directory, runtime, or branch is unclear.
- the operation may affect production or an important shared environment.
- creating/updating project credentials is required.
- derived template script content is non-trivial.
- precheck fails.
- deployment restrictions block deployment.

Use short, concrete questions with the exact missing fields.

## Safety Rules

- Never store `DEPLOY_BOT_TOKEN` in repository files.
- Never commit generated secrets or credentials.
- Never bypass deployment restriction policies.
- Never deploy after failed precheck.
- Never invent unsupported plugins.
- Never silently overwrite an existing pipeline; compare current detail first.

## Final Report

Report only useful results:

- detected project type.
- selected plugin/template.
- resources reused or created.
- JSON files generated.
- missing inputs or questions asked.
- precheck result.
- deployment ID, status, and log summary when deployed.
