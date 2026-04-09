# Frontend Project Guidelines

## Scope
- This file applies to all files under `frontend/`.
- These rules override the root `AGENTS.md` for frontend work.

## Build And Test
- Run commands from the `frontend` directory.
- Install dependencies with:
	```bash
	npm ci
	```
- Start local development server:
	```bash
	npm run dev
	```
- Build production bundle:
	```bash
	npm run build
	```
- Run lint checks:
	```bash
	npm run lint
	```

## Architecture
- Stack: Next.js 16 App Router, React 19, TypeScript, Tailwind CSS v4.
- Entry layout is `src/app/page.tsx`, composed by `Sidebar` and `ChatArea`.
- `src/components/ChatArea.tsx` handles history loading, SSE streaming, and list virtualization.
- `next.config.ts` rewrites `/api/:path*` to `BACKEND_INTERNAL_URL` (default `http://localhost:8080`).
- Styling is utility-first with Tailwind and shared helpers like `cn()` in `src/lib/utils.ts`.

## Conventions
- Keep interactive/stateful components as client components (`"use client"`).
- Always call backend endpoints through `/api/...` routes; do not hardcode backend hosts in components.
- Preserve existing SSE behavior in chat flows (`Accept: text/event-stream`, `data:` parsing, text fallback).
- Keep slash-command routing in `ChatInput` aligned with backend intent types (`/help`, `/plan`, `/qa`).
- Follow strict TypeScript types and avoid introducing `any` unless there is no practical alternative.
- Reuse existing UI primitives and helper utilities before creating new abstractions.

## Common Pitfalls
- Missing `BACKEND_INTERNAL_URL` causes API proxy failures in containerized environments.
- Breaking auto-scroll/virtualization behavior in `ChatArea` can cause severe chat UX regressions.
- SSE depends on backend and Nginx streaming settings; see deployment docs before changing stream behavior.

## Documentation Map (Link, Do Not Duplicate)
- Root project setup and backend contract: [../README.md](../README.md)
- Backend/shared defaults: [../AGENTS.md](../AGENTS.md)
- Deployment and proxy behavior: [../deploy/README.md](../deploy/README.md)
- Frontend framework basics: [README.md](README.md)

## Working Style
- Prefer small, targeted changes that preserve existing interaction patterns.
- Validate with the smallest relevant command (`npm run lint` or `npm run build`) before finishing.
