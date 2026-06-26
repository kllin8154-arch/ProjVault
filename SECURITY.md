# Security Policy

ProjVault handles project documents, AI API credentials and generated evidence. Treat all deployments as sensitive by default.

## Reporting a Vulnerability

Please report vulnerabilities privately through the repository owner's preferred security contact or GitHub Security Advisories once enabled.

Do not publish:

- real project documents
- API keys
- generated AES-GCM master keys
- H2 database files
- production database credentials
- session cookies

## Secrets

Never commit local runtime data or secrets. In production, configure secrets using environment variables or a deployment secret manager.

Required production secret:

```text
PROJVAULT_SECRETS_MASTER_KEY
```

It must be a Base64-encoded 32-byte key. This key protects encrypted user AI settings. Losing or rotating it without a migration plan may make existing encrypted API keys unreadable.

## Supported Security Boundaries

ProjVault aims to protect against:

- normal web user privilege escalation
- project data visibility across non-admin users
- API response leaks of personal AI keys
- database backup exposure of plaintext AI keys

ProjVault cannot protect secrets from an operating-system administrator, database administrator, or anyone with full server access. Those roles are part of the deployment trust boundary.
