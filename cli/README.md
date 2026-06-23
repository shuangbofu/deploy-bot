# Deploy Bot CLI

Build:

```bash
mvn -pl cli -am -DskipTests package
```

Run from repository:

```bash
export DEPLOY_BOT_BASE_URL="http://localhost:8080"
export DEPLOY_BOT_TOKEN="dbot_xxx"
./cli/bin/deploy-bot projects list
```

Optional install to PATH:

```bash
ln -sf "$PWD/cli/bin/deploy-bot" /usr/local/bin/deploy-bot
deploy-bot help
```

The script runs `cli/target/deploy-bot-cli-0.0.1-SNAPSHOT.jar`, so rebuild after CLI code changes.
