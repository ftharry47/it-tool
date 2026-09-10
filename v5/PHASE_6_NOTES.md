# Phase 6 — Knowledge Base

## What was built

### Flyway `V9__knowledge_base.sql`

- `kb_article` with DRAFT/PENDING_REVIEW/PUBLISHED/ARCHIVED status, markdown `body`, category, view/helpful counters, version number.
- `kb_article_version` to hold snapshots of previous published states.
- `kb_feedback` for helpful/not-helpful votes with comments.
- `kb_article_number_seq` for article numbers.

### Entities / Repositories

- `KbArticle`, `KbArticleVersion`, `KbFeedback`
- `KbArticleRepository`, `KbArticleVersionRepository`, `KbFeedbackRepository`

### Status workflow

- `KbStatusMachine`: `DRAFT -> PENDING_REVIEW -> PUBLISHED -> ARCHIVED`
- `PENDING_REVIEW` can be sent back to `DRAFT` for rework.
- `PUBLISHED` can move only to `ARCHIVED`.

### Versioning rule

- DRAFT / PENDING_REVIEW / ARCHIVED edits update the live `kb_article` row in place.
- **PUBLISHED** edits: the live article's current content is snapshotted into `kb_article_version` (with the current `version`), then `kb_article.version` is incremented, then the live row is overwritten.
- Status-only changes (e.g. PUBLISHED -> ARCHIVED) do not create version rows.

### Full-text search

- `KnowledgeBaseSearch` interface.
- `TsVectorKnowledgeBaseSearch` — production PostgreSQL path with `to_tsvector('english', coalesce(title,'') || ' ' || coalesce(body,'')) @@ plainto_tsquery('english', :q)`, ranked by `ts_rank`. Active `@Profile("!test")`.
- `IlikeKnowledgeBaseSearch` — H2 / test-only fallback using `LOWER(...) LIKE LOWER(...)`. It is clearly commented as a test-only shim.

### Service & API

- `KnowledgeBaseService` with create, update (with versioning), list, get, delete, feedback, list versions, search, suggest.
- `KnowledgeBaseController` at `/api/v1/kb` with endpoints for articles, versions, feedback, search, and suggest.

### Tests

- `KbStatusMachineTest` — legal and illegal status transitions.
- `KnowledgeBaseServiceTest`:
  - `publishedEditCreatesVersionAndIncrementsVersion` — proves a PUBLISHED edit snapshots a `kb_article_version` and increments the live version.
  - `draftEditDoesNotCreateVersion` — proves a DRAFT edit updates in place with no version row.
- `IlikeKnowledgeBaseSearchTest` — proves the H2/ILIKE fallback returns the expected article for a keyword and a description.
- `TsVectorKnowledgeBaseSearchTest` — mocks `EntityManager` and verifies the production query uses `to_tsvector`/`plainto_tsquery` and returns the expected result row for search and suggest.

## Known gaps / follow-up

- `TsVectorKnowledgeBaseSearchTest` only proves the SQL string is built correctly and maps a mocked result row. It does **not** execute against real PostgreSQL. Before trusting `/api/v1/kb/search` and `/api/v1/kb/suggest` in production, test them manually against the actual Azure Postgres instance with real data.

## Build result

```powershell
.\package.ps1
```

- `mvn package` with tests: **53 tests run, 0 failures, 0 errors, 1 skipped**
- `KbStatusMachineTest: 4 passed`
- `KnowledgeBaseServiceTest: 2 passed`
- `IlikeKnowledgeBaseSearchTest: 2 passed`
- `TsVectorKnowledgeBaseSearchTest: 2 passed`
- `target/app.zip` built cleanly
