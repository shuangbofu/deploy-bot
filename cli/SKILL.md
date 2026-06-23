---
name: deploy-bot-cli
description: Use this skill when Codex, Claude Code, OpenCode, or another local agent needs to inspect a repository and onboard, configure, precheck, deploy, or inspect it through Deploy Bot using the repository-provided CLI and API Token.
---

# Deploy Bot CLI Skill

Use the repository CLI, not browser login, to operate Deploy Bot.

Required environment:

```bash
export DEPLOY_BOT_BASE_URL="https://deploy.example.com"
export DEPLOY_BOT_TOKEN="dbot_xxx"
```

Build the CLI if needed:

```bash
mvn -pl cli -am -DskipTests package
```

Run from this repository:

```bash
./cli/bin/deploy-bot help
```

If the command is installed on PATH, `deploy-bot ...` is also acceptable.

# Workflow

1. Inspect the local Git repository.
2. Identify project type and deployment shape.
3. Query Deploy Bot resources with the CLI.
4. Create or update project, derived template, and pipeline only when needed.
5. Run deployment precheck.
6. Deploy only after precheck passes and the user confirms target host/environment when ambiguous.
7. Inspect deployment detail and logs.

# Repository Inspection

Use local files before choosing a Deploy Bot plugin:

- Spring Boot service: `pom.xml`, `build.gradle`, Spring Boot dependencies, `@SpringBootApplication`, Jar output.
- Node static site: `package.json`, Vite/Webpack config, `dist` or `build` output.
- Fullstack project: frontend and backend directories released together.
- Dockerfile: only use if Deploy Bot has a matching plugin; do not invent Docker support.

Collect:

- project name and Git remote URL
- current branch
- build command and artifact/output path
- target host and deploy directory
- Java/Node/Maven runtime requirements
- service startup args and startup keyword

# CLI Commands

Read resources:

```bash
./cli/bin/deploy-bot projects list
./cli/bin/deploy-bot hosts list
./cli/bin/deploy-bot runtimes list --host-id 1
./cli/bin/deploy-bot plugins list
./cli/bin/deploy-bot templates list
./cli/bin/deploy-bot pipelines list
```

Create or update resources:

```bash
./cli/bin/deploy-bot projects create @project.json
./cli/bin/deploy-bot templates create @template.json
./cli/bin/deploy-bot pipelines apply @pipeline.json
./cli/bin/deploy-bot pipelines apply --id 18 @pipeline.json
```

Precheck and deploy:

```bash
./cli/bin/deploy-bot deployments precheck @deployment.json
./cli/bin/deploy-bot deployments run @deployment.json
./cli/bin/deploy-bot deployments detail 123
./cli/bin/deploy-bot deployments logs 123 --follow
```

Use `-` instead of `@file` to pass JSON through stdin.

# Plugin Selection

Prefer built-in templates:

- Spring Boot Jar service: Spring Boot deployment plugin.
- Static frontend: Node static deployment plugin.
- Frontend plus Spring Boot backend: fullstack deployment plugin.

Create a derived template only when the built-in template cannot express the deployment cleanly:

- script structure differs
- fixed deploy-time copy/move steps are required
- artifact layout is non-standard
- start command needs reusable project-specific wrapping
- multiple modules must be rearranged every deployment

Do not create a derived template only for branch, host, deploy directory, runtime version, ordinary build command, or ordinary startup args.

# Safety Rules

- Never use username/password or browser cookies for agent automation.
- Never invent IDs. Query existing resources first.
- Never deploy if precheck fails.
- Never bypass deployment restriction policies.
- Never store `DEPLOY_BOT_TOKEN` in repository files.
- Never commit generated secrets.
- Ask the user before creating a pipeline when target host, runtime, deploy directory, or production-like environment is ambiguous.

# Output

Report:

- detected project type
- selected plugin/template
- resources found or created
- missing inputs
- precheck result
- deployment ID and final status when deployed
