# PatentFlow BE

PatentFlow backend MVP scaffold for FE API integration.

## Run

```bash
docker compose up --build
```

Local verification:

```bash
mvn test
```

API base URL:

```text
http://localhost:8080/api/v1
```

Swagger UI:

```text
http://localhost:8080/swagger-ui.html
```

## MVP APIs

- `GET /api/v1/patents` - FR-001, FR-002
- `GET /api/v1/patents/{patentId}` - FR-005, FR-006, FR-007, FR-008, FR-011, FR-012
- `GET /api/v1/patents/{patentId}/history` - FR-013

The current implementation loads official demo patent metadata from `docs/skax_patents_list.md` and adds mock evaluation/workflow data around it.

## External Patent Lookup

`GET /api/v1/patents/external-lookup` tries sources in `sourcePriority` order.

```text
sourcePriority=KIPRIS,GOOGLE_PATENTS
```

KIPRIS is disabled by default because it needs a KIPRISPlus service key.

```bash
PATENTFLOW_KIPRIS_ENABLED=true
PATENTFLOW_KIPRIS_SERVICE_KEY=...
```

Google Patents is implemented as a public page fallback, not an official JSON API. If external lookup fails or has missing fields, the API falls back to `docs/skax_patents_list.md` metadata where available.
