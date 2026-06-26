# Contributing to ProjVault

Thanks for your interest in ProjVault.

## Development Setup

```powershell
cd D:\Java-p\ProjVault
.\mvnw.cmd spring-boot:run
```

Run verification before submitting changes:

```powershell
.\mvnw.cmd -q test
```

## Contribution Rules

- Keep changes focused and small.
- Do not commit real project data, local databases, logs, API keys, generated secret keys or customer documents.
- Add or update tests for backend behavior changes.
- For controller write operations, keep `@RequirePerm` permission checks.
- Prefer existing package structure and service patterns over new abstractions.

## Pull Request Checklist

- [ ] Tests pass locally.
- [ ] No secrets or local data are committed.
- [ ] Documentation is updated when behavior changes.
- [ ] The change is scoped to one feature, bug fix or cleanup.
