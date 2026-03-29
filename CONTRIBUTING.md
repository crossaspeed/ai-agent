# Contributing Guide

Thank you for your interest in contributing.

## Before You Start

- Be respectful and collaborative.
- Read the Code of Conduct in `CODE_OF_CONDUCT.md`.
- Open an issue first for major changes.

## Development Setup

Prerequisites:

- Java 17
- Maven 3.9+
- Node.js 20+
- Docker (optional, for deployment testing)

Backend setup:

```bash
mvn compile test-compile
mvn spring-boot:run
```

Frontend setup:

```bash
cd frontend
npm ci
npm run dev
```

## Running Tests

Backend tests:

```bash
mvn test
```

Frontend lint:

```bash
cd frontend
npm run lint
```

## Branch and Commit Convention

- Branch examples:
  - `feat/add-study-plan-filter`
  - `fix/chat-stream-timeout`
  - `docs/update-readme`
- Commit style (recommended):
  - `feat: add topic upload retry`
  - `fix: handle empty memory id`
  - `docs: improve deploy guide`

## Pull Request Checklist

- [ ] Code builds successfully
- [ ] Relevant tests pass
- [ ] No sensitive data (keys, passwords, tokens) committed
- [ ] Documentation updated if behavior changed
- [ ] PR description explains what changed and why

## Scope Guidance

Please keep PRs focused and small. If the change is large, split it into multiple PRs.

## Security

Do not report vulnerabilities in public issues. See `SECURITY.md`.
