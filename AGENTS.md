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
