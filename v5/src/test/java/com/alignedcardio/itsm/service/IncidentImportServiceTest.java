package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.entity.AuditLog;
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
import jakarta.persistence.Query;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IncidentImportServiceTest {

    private static final UUID ORG_ID = UUID.randomUUID();

    private static final String[] HEADERS = {
            "Ticket ID", "Created Date", "Name", "Email", "Phone", "Location",
            "Issue Type", "Impact Area", "Description", "Status", "Priority",
            "Marked Critical", "Assigned To", "Escalation Level", "Resolved By", "Resolved Date"};

    @Mock private IncidentRepository incidentRepository;
    @Mock private AppUserRepository appUserRepository;
    @Mock private LocationRepository locationRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private PriorityRepository priorityRepository;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private EntityManager em;

    private IncidentImportService service;

    private AppUser requester;
    private AppUser agent;
    private AppUser legacyUser;
    private Location location;
    private Category category;
    private Priority critical;
    private Priority medium;
    private Priority low;

    @BeforeEach
    void setUp() {
        service = new IncidentImportService(incidentRepository, appUserRepository,
                locationRepository, categoryRepository, priorityRepository, auditLogRepository, em);

        requester = user("Jane Doe", "jane@example.com");
        agent = user("Bob Agent", "bob@example.com");
        legacyUser = user("Legacy Import", "legacy-import@alignedcardio.local");
        location = new Location();
        location.setName("HQ");
        category = new Category();
        category.setName("Hardware");
        critical = priority("Critical");
        medium = priority("Medium");
        low = priority("Low");

        when(appUserRepository.findByOrgId(ORG_ID)).thenReturn(List.of(requester, agent, legacyUser));
        lenient().when(appUserRepository.findByOrgIdAndEmailIgnoreCase(ORG_ID, "legacy-import@alignedcardio.local"))
                .thenReturn(Optional.of(legacyUser));
        when(locationRepository.findByOrgIdAndDeletedAtIsNullOrderByName(ORG_ID)).thenReturn(List.of(location));
        when(categoryRepository.findByOrgIdAndDeletedAtIsNullOrderByDisplayOrderAsc(ORG_ID)).thenReturn(List.of(category));
        when(priorityRepository.findByOrgIdAndStatusOrderByDisplayOrderAsc(ORG_ID, Priority.Status.ACTIVE))
                .thenReturn(List.of(critical, medium, low));
    }

    private AppUser user(String name, String email) {
        AppUser u = new AppUser();
        u.setDisplayName(name);
        u.setEmail(email);
        return u;
    }

    private Priority priority(String name) {
        Priority p = new Priority();
        p.setName(name);
        return p;
    }

    private InputStream xlsx(String[][] rows) throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet();
            Row header = sheet.createRow(0);
            for (int c = 0; c < HEADERS.length; c++) header.createCell(c).setCellValue(HEADERS[c]);
            for (int r = 0; r < rows.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < rows[r].length; c++) {
                    if (rows[r][c] != null) row.createCell(c).setCellValue(rows[r][c]);
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }

    @Test
    void previewMapsFullyMatchedRow() throws Exception {
        InputStream file = xlsx(new String[][]{{
                "HD-1001", "2023-05-10 09:30", "Jane Doe", "jane@example.com", "555-1234", "HQ",
                "Hardware", "Workstations", "Printer jam on floor 3", "Closed", "Medium",
                "No", "Bob Agent", "L1", "Bob Agent", "2023-05-11 14:00"}});

        IncidentImportService.ImportPreview preview = service.preview(file, ORG_ID);

        assertEquals(1, preview.totalRows());
        assertEquals(1, preview.importable());
        IncidentImportService.PreviewRow row = preview.rows().get(0);
        assertNull(row.error());
        assertEquals("HD-1001", row.legacyTicketId());
        assertEquals("Printer jam on floor 3", row.title());
        assertTrue(row.requesterMatched());
        assertTrue(row.locationMatched());
        assertTrue(row.categoryMatched());
        assertEquals("Hardware", row.categoryName());
        assertEquals("Medium", row.priorityName());
        assertTrue(row.assigneeMatched());
        assertEquals("CLOSED", row.status());
        assertTrue(row.warnings().isEmpty());
    }

    @Test
    void previewWarnsOnUnmatchedLocationAndCriticalOverride() throws Exception {
        InputStream file = xlsx(new String[][]{{
                "HD-1002", "2022-01-15", "Unknown Person", "nobody@old-system.com", "", "Branch 99",
                "Networking", "", "VPN outage", "Resolved", "Low",
                "Yes", "Ghost Agent", "L2", "", ""}});

        IncidentImportService.ImportPreview preview = service.preview(file, ORG_ID);

        IncidentImportService.PreviewRow row = preview.rows().get(0);
        assertNull(row.error());
        assertFalse(row.locationMatched());
        assertFalse(row.requesterMatched());
        assertFalse(row.assigneeMatched());
        // Marked Critical=Yes overrides Priority=Low
        assertTrue(row.markedCritical());
        assertEquals("Critical", row.priorityName());
        assertTrue(row.warnings().stream().anyMatch(w -> w.contains("Location 'Branch 99'")));
        assertTrue(row.warnings().stream().anyMatch(w -> w.contains("Critical")));
        // no Resolved Date -> Created Date fallback warning
        assertTrue(row.warnings().stream().anyMatch(w -> w.contains("Resolved Date")));
    }

    @Test
    void commitPersistsClosedLegacyIncidentWithoutSla() throws Exception {
        when(incidentRepository.save(any(Incident.class))).thenAnswer(inv -> {
            Incident i = inv.getArgument(0);
            i.setId(UUID.randomUUID());
            i.setOrgId(ORG_ID);
            return i;
        });
        Query nativeQuery = mock(Query.class);
        when(nativeQuery.setParameter(anyString(), any())).thenReturn(nativeQuery);
        when(em.createNativeQuery(anyString())).thenReturn(nativeQuery);

        InputStream file = xlsx(new String[][]{{
                "HD-2001", "2021-03-01 10:00", "Jane Doe", "jane@example.com", "555-9999", "HQ",
                "Hardware", "", "Laptop won't boot", "Fixed", "High",
                "1", "Bob Agent", "L2", "Bob Agent", "2021-03-02"}});

        IncidentImportService.ImportResult result = service.commit(file, ORG_ID, agent);

        assertEquals(1, result.imported());
        assertEquals(0, result.skipped());

        ArgumentCaptor<Incident> captor = ArgumentCaptor.forClass(Incident.class);
        verify(incidentRepository).save(captor.capture());
        Incident saved = captor.getValue();
        assertEquals(Incident.Status.CLOSED, saved.getStatus());
        assertTrue(saved.isLegacyImport());
        assertEquals("HD-2001", saved.getLegacyTicketId());
        assertEquals("Jane Doe <jane@example.com>", saved.getLegacyRequester());
        assertEquals(requester, saved.getRequester());
        assertEquals(agent, saved.getAssignee());
        assertEquals(critical, saved.getPriority()); // Marked Critical "1" overrides High
        assertNotNull(saved.getClosingNotes());
        assertTrue(saved.getClosingNotes().contains("Escalation: L2"));
        assertTrue(saved.getClosingNotes().contains("Resolved By: Bob Agent"));
        assertNull(saved.getAssignmentTeam()); // historical escalation never wires a live team
        // createdAt written via native update (insertable=false column)
        verify(em).createNativeQuery("UPDATE incident SET created_at = :created WHERE id = :id");
        // audit IMPORT row recorded
        ArgumentCaptor<AuditLog> audit = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(audit.capture());
        assertEquals("IMPORT", audit.getValue().getAction());
        assertEquals("INCIDENT", audit.getValue().getEntityType());
    }

    @Test
    void commitUsesLegacyPlaceholderForUnmatchedRequester() throws Exception {
        when(incidentRepository.save(any(Incident.class))).thenAnswer(inv -> {
            Incident i = inv.getArgument(0);
            i.setId(UUID.randomUUID());
            i.setOrgId(ORG_ID);
            return i;
        });
        Query nativeQuery = mock(Query.class);
        lenient().when(nativeQuery.setParameter(anyString(), any())).thenReturn(nativeQuery);
        lenient().when(em.createNativeQuery(anyString())).thenReturn(nativeQuery);

        InputStream file = xlsx(new String[][]{{
                "HD-3001", "2020-06-01", "Departed User", "gone@old.com", "", "",
                "", "", "Old ticket", "Closed", "", "", "", "", "", ""}});

        service.commit(file, ORG_ID, agent);

        ArgumentCaptor<Incident> captor = ArgumentCaptor.forClass(Incident.class);
        verify(incidentRepository).save(captor.capture());
        assertEquals(legacyUser, captor.getValue().getRequester());
        assertEquals("Departed User <gone@old.com>", captor.getValue().getLegacyRequester());
        assertEquals(medium, captor.getValue().getPriority()); // default
    }

    @Test
    void commitSkipsRowsWithBadCreatedDate() throws Exception {
        when(incidentRepository.save(any(Incident.class))).thenAnswer(inv -> {
            Incident i = inv.getArgument(0);
            i.setId(UUID.randomUUID());
            i.setOrgId(ORG_ID);
            return i;
        });
        Query nativeQuery = mock(Query.class);
        lenient().when(nativeQuery.setParameter(anyString(), any())).thenReturn(nativeQuery);
        lenient().when(em.createNativeQuery(anyString())).thenReturn(nativeQuery);

        InputStream file = xlsx(new String[][]{
                {"HD-4001", "not-a-date", "Jane", "jane@example.com", "", "", "", "", "Bad date row", "", "", "", "", "", "", ""},
                {"HD-4002", "2020-01-01", "Jane", "jane@example.com", "", "", "", "", "Good row", "", "", "", "", "", "", ""}});

        IncidentImportService.ImportResult result = service.commit(file, ORG_ID, agent);

        assertEquals(1, result.imported());
        assertEquals(1, result.skipped());
        assertEquals(2, result.skippedRows().get(0).rowNumber());
        assertTrue(result.skippedRows().get(0).reason().contains("Created Date"));
    }

    @Test
    void missingHeaderColumnFailsValidation() throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet();
            Row header = sheet.createRow(0);
            for (int c = 0; c < HEADERS.length; c++) {
                header.createCell(c).setCellValue(HEADERS[c].equals("Phone") ? "Telephone" : HEADERS[c]);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            InputStream file = new ByteArrayInputStream(out.toByteArray());
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.preview(file, ORG_ID));
            assertTrue(ex.getMessage().contains("Phone"));
        }
    }
}
