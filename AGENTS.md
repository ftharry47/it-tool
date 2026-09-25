# IT Support Portal — Coding Conventions

## v4 Java backend conventions

The `v4/src/main/java` Spring Boot backend follows these practices to avoid `LazyInitializationException`, `EntityNotFoundException`, and spurious HTTP 500 responses.

### 1. Reload associations before mapping to responses

When an entity may hold a lazy or soft-deleted association, reload the related entity through its repository inside `toResponse(...)` instead of accessing it directly.

Example from `v4/src/main/java/com/alignedcardio/itsm/service/ProjectService.java`:

```java
private ProjectResponse toResponse(Project p) {
    UUID leadId = null;
    String leadName = null;
    if (p.getLead() != null) {
        AppUser lead = appUserRepository.findById(p.getLead().getId()).orElse(null);
        if (lead != null) {
            leadId = lead.getId();
            leadName = lead.getDisplayName();
        }
    }
    return new ProjectResponse(
            p.getId(),
            p.getKey(),
            p.getName(),
            p.getDescription(),
            leadId,
            leadName,
            p.getStatus().name(),
            p.getCreatedAt(),
            p.getUpdatedAt()
    );
}
```

### 2. Compute resource URLs after persistence

Do not build URLs that depend on an auto-generated ID before the entity is saved. Save first, then set the derived URL/path, and save again if necessary.

Example from `v4/src/main/java/com/alignedcardio/itsm/service/IncidentAttachmentService.java`:

```java
attachment = attachmentRepository.save(attachment);
attachment.setBlobUrl("/api/v1/incidents/" + incident.getId() + "/attachments/" + attachment.getId());
attachment = attachmentRepository.save(attachment);
```

### 3. Register all `NotFoundException` variants in the global handler

`v4` has two `NotFoundException` classes:

- `com.alignedcardio.itsm.api.incident.NotFoundException`
- `com.alignedcardio.itsm.service.NotFoundException`

Both must be handled in `v4/src/main/java/com/alignedcardio/itsm/config/GlobalExceptionHandler.java` so missing entities return `404 Not Found` instead of `500 Internal Server Error`.

```java
@ExceptionHandler(NotFoundException.class)
public ResponseEntity<Map<String, String>> handleNotFound(NotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(Map.of("error", ex.getMessage()));
}

@ExceptionHandler(com.alignedcardio.itsm.service.NotFoundException.class)
public ResponseEntity<Map<String, String>> handleServiceNotFound(com.alignedcardio.itsm.service.NotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(Map.of("error", ex.getMessage()));
}
```

## General project notes

- Prefer minimal upstream fixes over downstream workarounds.
- Identify the root cause before implementing changes.
- Add regression tests when fixing bugs, but keep the implementation minimal.

## v5 Azure deployment configuration

Build command (run from `C:\Users\SriHariThangavel\Documents\Dev-IT\v5`):

```powershell
.\package.ps1 `
  -AzureClientId "<AZURE_GRAPH_CLIENT_ID / Entra app registration Client ID>" `
  -AzureTenantId "<AZURE_AD_TENANT_ID / Entra tenant ID>" `
  -AppBaseUrl "https://itsm-alignedcardio.azurewebsites.net"
```

- `VITE_AZURE_CLIENT_ID` / `VITE_AZURE_TENANT_ID` are build-time values only. They are baked into the JS by Vite during `npm run build`, so they must be passed to `package.ps1` (or set as local env vars). Adding them to Azure App Service app settings does not help.
- The frontend MSAL Client ID is usually the same as the `AZURE_GRAPH_CLIENT_ID` app setting.
- The tenant ID is usually the same as the `AZURE_AD_TENANT_ID` app setting.
- `AppBaseUrl` should match the `APP_BASE_URL` app setting.

Deploy command / Kudu steps:

1. Open `https://itsm-alignedcardio.scm.azurewebsites.net/ZipDeployUI`
2. Upload `target/app.zip`
3. In Azure App Service Configuration:
   - **Startup Command:** `java -jar /home/site/wwwroot/app.jar`
   - **WEBSITES_PORT:** must be empty / unset
   - Do **not** use `startup.sh`

Build-time note:

- `pom.xml` runs `npm run build` in the `generate-resources` phase so Vite injects the env vars into `src/main/resources/static` before Maven copies it to `target/classes/static` and packages `app.jar`.
- **`mvn package` is incremental — deleted resources linger.** `package.ps1` deliberately avoids `mvn clean` (it would delete `target/node/node_modules` while the frontend-maven plugin holds a lock on Windows). Consequence: when a file under `src/main/resources` is deleted or renamed, its copy under `target/classes/` survives and gets repackaged into `app.jar` — e.g., a removed migration `.sql` keeps deploying alongside its `.java` replacement and Flyway runs both. **After deleting/renaming anything under `src/main/resources`, manually delete the matching file under `target/classes/` (or `rm -rf target/classes`) before packaging**, then verify with `jar tf target\app.jar | Select-String "<name>"` before uploading to Kudu.
