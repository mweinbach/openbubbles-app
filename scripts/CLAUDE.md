# scripts/ — Developer Scripts

## dart-fix-common-issues.sh
Runs `dart fix --apply` across the project to automatically resolve common lint issues (unused imports, deprecated APIs, etc.).

```bash
bash scripts/dart-fix-common-issues.sh
```

Run this after making broad changes or before committing if the linter is reporting auto-fixable issues.
