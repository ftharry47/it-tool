package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.entity.AuditLog;
import com.alignedcardio.itsm.entity.BaseEntity;
import com.alignedcardio.itsm.entity.Category;
import com.alignedcardio.itsm.entity.Incident;
import com.alignedcardio.itsm.entity.Location;
import com.alignedcardio.itsm.entity.Priority;
import com.alignedcardio.itsm.repository.AppUserRepository;
import com.alignedcardio.itsm.repository.AuditLogRepository;
import com.alignedcardio.itsm.repository.CategoryRepository;
import com.alignedcardio.itsm.repository.IncidentRepository;
import com.alignedcardio.itsm.repository.LocationRepository;
import com.alignedcardio.itsm.repository.PriorityRepository;
import jakarta.persistence.EntityManager;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Imports historical Incidents from a legacy .xlsx export. Imported rows are
 * always CLOSED, carry is_legacy_import=true, and never create SlaInstance rows
 * (the SlaEngine is deliberately not invoked), so they are excluded from SLA
 * compliance but included in general ticket reporting.
 */
@Service
public class IncidentImportService {

    private static final Logger log = LoggerFactory.getLogger(IncidentImportService.class);
    private static final String LEGACY_USER_EMAIL = "legacy-import@alignedcardio.local";

    /** Expected column headers, matched case-insensitively. */
    private static final String[] HEADERS = {
            "Ticket ID", "Created Date", "Name", "Email", "Phone", "Location",
            "Issue Type", "Impact Area", "Description", "Status", "Priority",
            "Marked Critical", "Assigned To", "Escalation Level", "Resolved By", "Resolved Date"
    };

    private static final Set<String> TRUTHY = Set.of("yes", "true", "y", "1", "x", "critical");

    private static final Map<String, String> PRIORITY_ALIASES = Map.ofEntries(
            Map.entry("critical", "Critical"), Map.entry("urgent", "Critical"),
            Map.entry("p1", "Critical"), Map.entry("sev1", "Critical"), Map.entry("1", "Critical"),
            Map.entry("high", "High"), Map.entry("p2", "High"), Map.entry("2", "High"),
            Map.entry("medium", "Medium"), Map.entry("normal", "Medium"), Map.entry("moderate", "Medium"),
            Map.entry("p3", "Medium"), Map.entry("3", "Medium"),
            Map.entry("low", "Low"), Map.entry("p4", "Low"), Map.entry("minor", "Low"), Map.entry("4", "Low"));

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_OFFSET_DATE_TIME,
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("M/d/yyyy h:mm a"),
            DateTimeFormatter.ofPattern("M/d/yyyy H:mm"),
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("M/d/yyyy"),
            DateTimeFormatter.ofPattern("d-MMM-yyyy"));

    public record ImportPreview(int totalRows, int importable, int skipped, List<PreviewRow> rows) {
    }

    public record PreviewRow(int rowNumber, String legacyTicketId, String title, String requester,
                             boolean requesterMatched, String locationName, boolean locationMatched,
                             String categoryName, boolean categoryMatched, String priorityName,
                             boolean markedCritical, String assigneeName, boolean assigneeMatched,
                             String status, String createdAt, String resolvedAt,
                             List<String> warnings, String error) {
    }

    public record ImportResult(int imported, int skipped, List<SkippedRow> skippedRows) {
    }

    public record SkippedRow(int rowNumber, String reason) {
    }

    private record RawRow(int rowNumber, String ticketId, String createdDate, String name, String email,
                          String phone, String location, String issueType, String impactArea, String description,
                          String status, String priority, String markedCritical, String assignedTo,
                          String escalationLevel, String resolvedBy, String resolvedDate,
                          OffsetDateTime createdAt, OffsetDateTime resolvedAt) {
        boolean isEmpty() {
            return (ticketId + name + email + description + status).isBlank();
        }
    }

    private final IncidentRepository incidentRepository;
    private final AppUserRepository appUserRepository;
    private final LocationRepository locationRepository;
    private final CategoryRepository categoryRepository;
    private final PriorityRepository priorityRepository;
    private final AuditLogRepository auditLogRepository;
    private final EntityManager em;

    public IncidentImportService(IncidentRepository incidentRepository,
                                 AppUserRepository appUserRepository,
                                 LocationRepository locationRepository,
                                 CategoryRepository categoryRepository,
                                 PriorityRepository priorityRepository,
                                 AuditLogRepository auditLogRepository,
                                 EntityManager em) {
        this.incidentRepository = incidentRepository;
        this.appUserRepository = appUserRepository;
        this.locationRepository = locationRepository;
        this.categoryRepository = categoryRepository;
        this.priorityRepository = priorityRepository;
        this.auditLogRepository = auditLogRepository;
        this.em = em;
    }

    @Transactional(readOnly = true)
    public ImportPreview preview(InputStream in, UUID orgId) {
        RefData ref = new RefData().load(orgId);
        List<RawRow> raw = parse(in);
        List<PreviewRow> rows = new ArrayList<>();
        int importable = 0;
        for (RawRow r : raw) {
            PreviewRow row = mapRow(r, ref);
            rows.add(row);
            if (row.error() == null) importable++;
        }
        return new ImportPreview(rows.size(), importable, rows.size() - importable, rows);
    }

    @Transactional
    public ImportResult commit(InputStream in, UUID orgId, AppUser actor) {
        RefData ref = new RefData().load(orgId);
        List<RawRow> raw = parse(in);
        List<SkippedRow> skipped = new ArrayList<>();
        int imported = 0;
        for (RawRow r : raw) {
            PreviewRow mapped = mapRow(r, ref);
            if (mapped.error() != null) {
                skipped.add(new SkippedRow(r.rowNumber(), mapped.error()));
                continue;
            }
            Incident incident = buildIncident(r, mapped, ref);
            incident = incidentRepository.save(incident);
            incidentRepository.flush();
            // createdAt is a DB-default, insertable=false column — set it to the
            // historical Created Date via a direct update after insert.
            em.createNativeQuery("UPDATE incident SET created_at = :created WHERE id = :id")
                    .setParameter("created", r.createdAt())
                    .setParameter("id", incident.getId())
                    .executeUpdate();
            writeImportAudit(incident, r, actor);
            imported++;
        }
        log.info("Legacy incident import: {} imported, {} skipped for org {}", imported, skipped.size(), orgId);
        return new ImportResult(imported, skipped.size(), skipped);
    }

    private Incident buildIncident(RawRow r, PreviewRow mapped, RefData ref) {
        Incident i = new Incident();
        i.setTitle(mapped.title());
        i.setDescription(truncate(r.description(), 4000));
        i.setStatus(Incident.Status.CLOSED);
        i.setRequester(resolveRequester(r, ref));
        i.setAssignee(findUserByName(r.assignedTo(), ref));
        i.setLocation(mapped.locationMatched() ? findLocation(r.location(), ref) : null);
        i.setCategory(resolveCategory(r, ref));
        i.setPriority(resolvePriority(r, ref));
        i.setPhone(r.phone() == null || r.phone().isBlank() ? null : truncate(r.phone().trim(), 20));
        i.setResolvedAt(r.resolvedAt() != null ? r.resolvedAt() : r.createdAt());
        i.setClosedAt(r.resolvedAt() != null ? r.resolvedAt() : r.createdAt());
        i.setClosingNotes(provenance(r));
        i.setLegacyTicketId(truncate(r.ticketId(), 64));
        i.setLegacyRequester(truncate(legacyRequesterLabel(r), 255));
        i.setLegacyImport(true);
        return i;
    }

    private String provenance(RawRow r) {
        StringBuilder sb = new StringBuilder("Legacy import");
        appendProvenance(sb, "Issue Type", r.issueType());
        appendProvenance(sb, "Impact Area", r.impactArea());
        appendProvenance(sb, "Status", r.status());
        appendProvenance(sb, "Escalation", r.escalationLevel());
        appendProvenance(sb, "Assigned To", r.assignedTo());
        appendProvenance(sb, "Resolved By", r.resolvedBy());
        return sb.toString();
    }

    private void appendProvenance(StringBuilder sb, String label, String value) {
        if (value != null && !value.isBlank()) {
            sb.append(" — ").append(label).append(": ").append(value.trim());
        }
    }

    private String legacyRequesterLabel(RawRow r) {
        String name = r.name() == null ? "" : r.name().trim();
        String email = r.email() == null ? "" : r.email().trim();
        if (name.isBlank() && email.isBlank()) return null;
        if (email.isBlank()) return name;
        if (name.isBlank()) return email;
        return name + " <" + email + ">";
    }

    private void writeImportAudit(Incident incident, RawRow r, AppUser actor) {
        AuditLog entry = new AuditLog();
        entry.setOrgId(incident.getOrgId());
        entry.setActorUserId(actor == null ? BaseEntity.SYSTEM_USER_ID : actor.getId());
        entry.setAction("IMPORT");
        entry.setEntityType("INCIDENT");
        entry.setEntityId(incident.getId());
        entry.setAfterState("{\"legacyTicketId\":\"" + escapeJson(r.ticketId())
                + "\",\"legacyRequester\":\"" + escapeJson(legacyRequesterLabel(r)) + "\"}");
        auditLogRepository.save(entry);
    }

    private String escapeJson(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private PreviewRow mapRow(RawRow r, RefData ref) {
        List<String> warnings = new ArrayList<>();
        String error = null;

        if (r.createdAt() == null) {
            error = "Missing or unparseable Created Date";
        }

        boolean requesterMatched = r.email() != null && !r.email().isBlank()
                && findUserByEmail(r.email(), ref) != null;
        String requesterLabel = legacyRequesterLabel(r);
        if (!requesterMatched) {
            warnings.add("Requester unmatched; using 'Legacy Import' placeholder");
        }

        boolean locationMatched = r.location() != null && !r.location().isBlank()
                && findLocation(r.location(), ref) != null;
        if (r.location() != null && !r.location().isBlank() && !locationMatched) {
            warnings.add("Location '" + r.location().trim() + "' unmatched; left empty");
        }

        Category category = resolveCategory(r, ref);
        boolean categoryMatched = category != null;
        if ((r.issueType() != null && !r.issueType().isBlank()
                || r.impactArea() != null && !r.impactArea().isBlank()) && !categoryMatched) {
            warnings.add("Category unmatched for Issue Type/Impact Area; left empty");
        }

        Priority priority = resolvePriority(r, ref);
        boolean markedCritical = isTruthy(r.markedCritical());
        if (markedCritical) {
            warnings.add("Marked Critical overrides Priority '" + nullToDash(r.priority()) + "'");
        }

        boolean assigneeMatched = r.assignedTo() != null && !r.assignedTo().isBlank()
                && findUserByName(r.assignedTo(), ref) != null;
        if (r.assignedTo() != null && !r.assignedTo().isBlank() && !assigneeMatched) {
            warnings.add("Assignee '" + r.assignedTo().trim() + "' unmatched; left unassigned");
        }

        if (r.phone() != null && r.phone().trim().length() > 20) {
            warnings.add("Phone exceeds 20 chars; will be truncated");
        }
        if (r.resolvedAt() == null) {
            warnings.add("No Resolved Date; Created Date used for resolved/closed timestamps");
        }

        return new PreviewRow(r.rowNumber(), r.ticketId(), deriveTitle(r), requesterLabel,
                requesterMatched, r.location(), locationMatched,
                category == null ? null : category.getName(), categoryMatched,
                priority == null ? null : priority.getName(), markedCritical,
                r.assignedTo(), assigneeMatched, "CLOSED",
                r.createdAt() == null ? null : r.createdAt().toString(),
                (r.resolvedAt() != null ? r.resolvedAt() : r.createdAt()) == null ? null
                        : (r.resolvedAt() != null ? r.resolvedAt() : r.createdAt()).toString(),
                warnings, error);
    }

    private String deriveTitle(RawRow r) {
        String desc = r.description() == null ? "" : r.description().trim();
        if (!desc.isBlank()) {
            String firstLine = desc.lines().findFirst().orElse(desc).trim();
            return truncate(firstLine, 200);
        }
        String id = r.ticketId() == null || r.ticketId().isBlank() ? "row " + r.rowNumber() : r.ticketId().trim();
        String issue = r.issueType() == null ? "" : r.issueType().trim();
        return issue.isBlank() ? "Imported legacy ticket " + id : issue + " (" + id + ")";
    }

    private String nullToDash(String s) {
        return s == null || s.isBlank() ? "—" : s.trim();
    }

    private AppUser resolveRequester(RawRow r, RefData ref) {
        AppUser match = r.email() == null || r.email().isBlank() ? null : findUserByEmail(r.email(), ref);
        return match != null ? match : ref.legacyUser(ref.orgId);
    }

    private AppUser findUserByEmail(String email, RefData ref) {
        return ref.usersByEmail.get(email.trim().toLowerCase(Locale.ROOT));
    }

    private AppUser findUserByName(String name, RefData ref) {
        if (name == null || name.isBlank()) return null;
        String key = name.trim().toLowerCase(Locale.ROOT);
        if (key.contains("@")) return ref.usersByEmail.get(key);
        return ref.usersByName.get(key);
    }

    private Location findLocation(String name, RefData ref) {
        if (name == null || name.isBlank()) return null;
        return ref.locations.get(name.trim().toLowerCase(Locale.ROOT));
    }

    private Category resolveCategory(RawRow r, RefData ref) {
        Category c = r.issueType() == null || r.issueType().isBlank() ? null
                : ref.categories.get(r.issueType().trim().toLowerCase(Locale.ROOT));
        if (c == null && r.impactArea() != null && !r.impactArea().isBlank()) {
            c = ref.categories.get(r.impactArea().trim().toLowerCase(Locale.ROOT));
        }
        return c;
    }

    private Priority resolvePriority(RawRow r, RefData ref) {
        String canonical = isTruthy(r.markedCritical()) ? "Critical"
                : PRIORITY_ALIASES.getOrDefault(r.priority() == null ? "" : r.priority().trim().toLowerCase(Locale.ROOT),
                        r.priority() == null ? "" : r.priority().trim());
        Priority p = canonical.isBlank() ? null : ref.priorities.get(canonical.toLowerCase(Locale.ROOT));
        if (p == null) p = ref.priorities.get("medium");
        if (p == null && !ref.priorities.isEmpty()) p = ref.priorities.values().iterator().next();
        return p;
    }

    private boolean isTruthy(String s) {
        return s != null && TRUTHY.contains(s.trim().toLowerCase(Locale.ROOT));
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    private List<RawRow> parse(InputStream in) {
        try (Workbook wb = new XSSFWorkbook(in)) {
            Sheet sheet = wb.getSheetAt(0);
            Row header = sheet.getRow(0);
            if (header == null) {
                throw new IllegalArgumentException("Excel file has no header row");
            }
            Map<String, Integer> colIndex = new HashMap<>();
            DataFormatter fmt = new DataFormatter();
            for (Cell cell : header) {
                String name = fmt.formatCellValue(cell).trim().toLowerCase(Locale.ROOT);
                if (!name.isBlank()) colIndex.put(name, cell.getColumnIndex());
            }
            Map<String, Integer> cols = new LinkedHashMap<>();
            List<String> missing = new ArrayList<>();
            for (String h : HEADERS) {
                Integer idx = colIndex.get(h.toLowerCase(Locale.ROOT));
                if (idx == null) missing.add(h);
                else cols.put(h, idx);
            }
            if (!missing.isEmpty()) {
                throw new IllegalArgumentException("Missing required column(s): " + String.join(", ", missing));
            }

            List<RawRow> rows = new ArrayList<>();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                RawRow r = new RawRow(i + 1,
                        text(row, cols.get("Ticket ID"), fmt),
                        text(row, cols.get("Created Date"), fmt),
                        text(row, cols.get("Name"), fmt),
                        text(row, cols.get("Email"), fmt),
                        text(row, cols.get("Phone"), fmt),
                        text(row, cols.get("Location"), fmt),
                        text(row, cols.get("Issue Type"), fmt),
                        text(row, cols.get("Impact Area"), fmt),
                        text(row, cols.get("Description"), fmt),
                        text(row, cols.get("Status"), fmt),
                        text(row, cols.get("Priority"), fmt),
                        text(row, cols.get("Marked Critical"), fmt),
                        text(row, cols.get("Assigned To"), fmt),
                        text(row, cols.get("Escalation Level"), fmt),
                        text(row, cols.get("Resolved By"), fmt),
                        text(row, cols.get("Resolved Date"), fmt),
                        parseDate(row, cols.get("Created Date"), fmt),
                        parseDate(row, cols.get("Resolved Date"), fmt));
                if (!r.isEmpty()) rows.add(r);
            }
            return rows;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not parse Excel file: " + e.getMessage(), e);
        }
    }

    private String text(Row row, Integer col, DataFormatter fmt) {
        if (col == null) return null;
        Cell cell = row.getCell(col);
        return cell == null ? null : fmt.formatCellValue(cell);
    }

    private OffsetDateTime parseDate(Row row, Integer col, DataFormatter fmt) {
        if (col == null) return null;
        Cell cell = row.getCell(col);
        if (cell == null) return null;
        try {
            if (DateUtil.isCellDateFormatted(cell)) {
                return OffsetDateTime.ofInstant(cell.getDateCellValue().toInstant(), ZoneOffset.UTC);
            }
        } catch (Exception ignored) {
        }
        String s = fmt.formatCellValue(cell).trim();
        if (s.isBlank()) return null;
        for (DateTimeFormatter f : DATE_FORMATS) {
            try {
                if (f == DateTimeFormatter.ISO_OFFSET_DATE_TIME) {
                    return OffsetDateTime.parse(s, f);
                }
                try {
                    return LocalDateTime.parse(s, f).atOffset(ZoneOffset.UTC);
                } catch (DateTimeParseException e2) {
                    return LocalDate.parse(s, f).atTime(LocalTime.MIDNIGHT).atOffset(ZoneOffset.UTC);
                }
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }

    private class RefData {
        final Map<String, AppUser> usersByEmail;
        final Map<String, AppUser> usersByName;
        final Map<String, Location> locations;
        final Map<String, Category> categories;
        final Map<String, Priority> priorities;
        UUID orgId;
        private AppUser legacyUser;

        private RefData() {
            usersByEmail = new HashMap<>();
            usersByName = new HashMap<>();
            locations = new HashMap<>();
            categories = new HashMap<>();
            priorities = new HashMap<>();
        }

        RefData load(UUID orgId) {
            this.orgId = orgId;
            for (AppUser u : appUserRepository.findByOrgId(orgId)) {
                if (u.getEmail() != null) usersByEmail.putIfAbsent(u.getEmail().toLowerCase(Locale.ROOT), u);
                if (u.getDisplayName() != null) usersByName.putIfAbsent(u.getDisplayName().toLowerCase(Locale.ROOT), u);
            }
            for (Location l : locationRepository.findByOrgIdAndDeletedAtIsNullOrderByName(orgId)) {
                locations.putIfAbsent(l.getName().toLowerCase(Locale.ROOT), l);
            }
            for (Category c : categoryRepository.findByOrgIdAndDeletedAtIsNullOrderByDisplayOrderAsc(orgId)) {
                categories.putIfAbsent(c.getName().toLowerCase(Locale.ROOT), c);
            }
            for (Priority p : priorityRepository.findByOrgIdAndStatusOrderByDisplayOrderAsc(orgId, Priority.Status.ACTIVE)) {
                priorities.putIfAbsent(p.getName().toLowerCase(Locale.ROOT), p);
            }
            return this;
        }

        AppUser legacyUser(UUID orgId) {
            if (legacyUser == null) {
                legacyUser = appUserRepository.findByOrgIdAndEmailIgnoreCase(orgId, LEGACY_USER_EMAIL)
                        .orElseGet(() -> {
                            AppUser u = new AppUser();
                            u.setOrgId(orgId);
                            u.setObjectId("legacy-import");
                            u.setEmail(LEGACY_USER_EMAIL);
                            u.setDisplayName("Legacy Import");
                            u.setStatus(AppUser.Status.INACTIVE);
                            u.setActive(false);
                            return appUserRepository.save(u);
                        });
            }
            return legacyUser;
        }
    }
}
