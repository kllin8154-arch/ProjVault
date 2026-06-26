# ProjVault

English | [中文](README.zh-CN.md)

ProjVault is an open-source project knowledge center for project managers. It turns messy project folders into searchable, evidence-backed project knowledge: scans mixed files, detects changes, builds a knowledge graph, answers questions with citations, tracks document versions, and generates reviewable AI deliverables.

> Status: early development. The current public module is Project Knowledge Center (PKC).

## Why ProjVault Exists

Real project archives are rarely clean. A project manager may receive Word, Excel, PDF, Markdown, HTML, screenshots, prototypes and archives from different teams. Files may be duplicated, misplaced, renamed, partially replaced, mixed with examples, or even contain documents from another project.

Simple file indexing can tell you that a keyword exists. It cannot reliably answer:

- Which document is the current valid version?
- Which change is outside the contract scope?
- Which evidence supports this conclusion?
- Is this IP, port or database address real, or only an example?
- Which files are related to this risk, contract clause, deliverable or service?

ProjVault is built for that gap.

## Screenshots

### Scan Report

![Scan report](docs/img.png)

### Knowledge Graph

![Knowledge graph](docs/img_1.png)

### Evidence-backed AI Q&A

![AI Q&A](docs/img_2.png)

### AI Deliverables

![AI deliverables](docs/img_3.png)

More screenshots are available in [docs](docs/).

## Core Capabilities

- Project CRUD and root path management
- Full and incremental scan pipeline
- File fingerprinting with SHA-256
- Added, modified, deleted and renamed file detection
- Multi-format document parsing
- Configuration and service endpoint extraction
- Noise filtering for examples, misplaced files and cross-project clues
- RAG question answering with citations
- GraphRAG-style local relationship expansion
- Knowledge graph visualization
- Ask history
- Document family clustering and version comparison
- AI deliverable generation, preview, quality check, revision and approval flow
- RBAC with project ownership isolation
- Per-user AI API settings encrypted with AES-GCM
- Golden evaluation set and runtime observability panels

## Tech Stack

- Java 17
- Spring Boot 4.1
- Spring Data JPA / Hibernate
- H2 for local development
- MySQL 8 for production
- Apache Tika for document parsing
- Static HTML / CSS / JavaScript frontend
- AntV G6-compatible graph visualization
- Custom AI provider layer for mock, OpenAI-compatible and Anthropic-style models

## Quick Start

Requirements:

- JDK 17+
- Maven Wrapper, included in this repository

Run in development mode:

```powershell
cd ProjVault
.\mvnw.cmd spring-boot:run
```

Then open:

```text
http://localhost:8090/
```

Default local account:

```text
username: admin
password: admin123
```

Change the initial admin password for any shared or production environment:

```powershell
$env:PROJVAULT_ADMIN_PASSWORD="replace-with-a-strong-password"
```

## AI Configuration

The system supports:

- Mock provider for local development
- OpenAI-compatible providers such as DeepSeek, OpenAI-compatible gateways, Qwen-compatible endpoints and Ollama-compatible endpoints
- Anthropic-style provider

For local experiments, set environment variables before starting:

```powershell
$env:LLM_API_KEY="your-api-key"
```

Non-admin users configure their own AI API settings in the system UI. Their API keys are encrypted and are not returned by the API.

## Production Notes

Production deployments should use MySQL and must provide secrets through the environment or the deployment platform's secret manager.

Important variables:

```text
PROJVAULT_DB_PASSWORD
PROJVAULT_ADMIN_USERNAME
PROJVAULT_ADMIN_PASSWORD
PROJVAULT_SECRETS_MASTER_KEY
PROJVAULT_PDF_FONT_PATH
```

`PROJVAULT_SECRETS_MASTER_KEY` must be a Base64-encoded 32-byte key. Do not commit generated keys, local H2 databases, logs, uploaded project files or real customer documents.

## License

This project is released under the Apache License 2.0. See [LICENSE](LICENSE).
