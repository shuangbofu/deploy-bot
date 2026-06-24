---
name: deploy-bot-cli
description: Use when Codex, Claude Code, OpenCode, or another local coding agent needs to onboard a repository into Deploy Bot, choose a deployment plugin/template, create or update a project/template/pipeline, run deployment precheck, trigger deployment, or inspect deployment logs through the repository CLI and API Token.
---

# Deploy Bot Agent Skill

Use this as an Agent runbook. Do not ask the user to manually execute CLI commands unless the environment blocks you. The Agent should inspect, query, generate JSON, run precheck, and report results.

## Fast Path

1. Verify CLI access.
2. Inspect the target repository.
3. Query existing Deploy Bot resources.
4. Choose an existing plugin/template.
5. Generate a reviewable `pipeline.json`.
6. Run precheck with `deployment.json`.
7. Ask for confirmation only before writing/updating resources or deploying when target details are unclear or production-like.
8. Trigger deployment only after precheck passes.

## Required Environment

```bash
export DEPLOY_BOT_BASE_URL="https://deploy.example.com"
export DEPLOY_BOT_TOKEN="dbot_xxx"
```

If either variable is missing, stop and ask the user for it. Never use browser login, username/password, cookies, or copied session tokens.

Build the CLI when needed:

```bash
mvn -pl cli -am -DskipTests package
```

Use either form:

```bash
./cli/bin/deploy-bot help
deploy-bot help
```

## 60 Second Start

Run these first to verify the token works and collect selectable platform resources:

```bash
./cli/bin/deploy-bot projects list
./cli/bin/deploy-bot hosts list
./cli/bin/deploy-bot plugins list
./cli/bin/deploy-bot templates list
```

Use the output to match existing project, host, plugin, and template IDs. If a required resource is missing, decide whether to create it or ask the user.

Then inspect the target repo to infer deployment shape and default branch:

```bash
git remote -v
git branch --show-current
find . -maxdepth 3 -name pom.xml -o -name package.json -o -name Dockerfile
```

Use the output to choose Spring Boot, Node static, fullstack, or another available plugin.

If a pipeline may already exist, inspect it before creating anything new:

```bash
./cli/bin/deploy-bot pipelines list
./cli/bin/deploy-bot pipelines detail <pipelineId>
./cli/bin/deploy-bot pipelines plugin-plan <pipelineId>
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

## Project Detection

Choose deployment shape from local files:

- Spring Boot Jar: `pom.xml` or `build.gradle`, Spring Boot dependency, `@SpringBootApplication`, Jar output.
- Node static site: `package.json`, Vite/Webpack/React/Vue, static output such as `dist` or `build`.
- Fullstack: frontend and Spring Boot backend released from one repository.
- Dockerfile: only use Docker if Deploy Bot exposes a Docker plugin/template. Do not invent Docker support.

Collect:

- Git URL and current branch.
- build command.
- artifact/output path.
- runtime requirements.
- startup args and startup keyword for services.
- special copy/move steps that may require a derived template.

## Resource Query Commands

Use these commands to read Deploy Bot state before generating JSON:

```bash
./cli/bin/deploy-bot projects list
./cli/bin/deploy-bot hosts list [--all]
./cli/bin/deploy-bot runtimes list [--host-id <id>] [--type JAVA|NODE|MAVEN]
./cli/bin/deploy-bot plugins list
./cli/bin/deploy-bot plugins shell-variables
./cli/bin/deploy-bot templates list
./cli/bin/deploy-bot pipelines list
./cli/bin/deploy-bot pipelines detail <id>
./cli/bin/deploy-bot pipelines plugin-plan <id>
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

Use command output IDs. Never invent IDs.

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
./cli/bin/deploy-bot projects create @project.json
./cli/bin/deploy-bot templates create @template.json
./cli/bin/deploy-bot templates update <id> @template.json
./cli/bin/deploy-bot pipelines apply @pipeline.json
./cli/bin/deploy-bot pipelines apply --id <id> @pipeline.json
```

- `projects create`: create a project only when the Git repository is not already registered.
- `templates create/update`: create or update a derived template only when default templates cannot express stable project-specific steps.
- `pipelines apply`: create a new pipeline from `pipeline.json`.
- `pipelines apply --id`: update an existing pipeline after reading `pipelines detail <id>` and preserving unrelated fields.

Ask before running write commands unless the user explicitly asked you to create/update.

## Precheck And Deploy

Always precheck before deployment:

```bash
./cli/bin/deploy-bot deployments precheck @deployment.json
```

Use precheck to validate pipeline existence, branch, deployment restriction policies, and basic deployability before triggering a deployment.

Only deploy when precheck succeeds:

```bash
./cli/bin/deploy-bot deployments run @deployment.json
./cli/bin/deploy-bot deployments detail <deploymentId>
./cli/bin/deploy-bot deployments logs <deploymentId> --follow
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
