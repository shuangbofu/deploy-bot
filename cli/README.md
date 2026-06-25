# Deploy Bot CLI

Build:

```bash
mvn -pl cli -am -DskipTests package
```

Run from repository:

```bash
export DEPLOY_BOT_BASE_URL="http://localhost:8080"
export DEPLOY_BOT_TOKEN="dbot_xxx"
./cli/bin/deploy-bot projects list --keyword demo
```

Optional install to PATH:

```bash
ln -sf "$PWD/cli/bin/deploy-bot" /usr/local/bin/deploy-bot
deploy-bot help
```

The script runs `cli/target/deploy-bot-cli-0.0.1-SNAPSHOT.jar`, so rebuild after CLI code changes.

List commands use paged APIs by default. The default page size is 20, and `--limit` is an alias of `--page-size`:

```bash
deploy-bot projects list --keyword demo --limit 20
deploy-bot hosts list --keyword test --limit 20
deploy-bot templates list --plugin-id springboot-deployment --limit 20
deploy-bot pipelines list --keyword demo --limit 20
```
