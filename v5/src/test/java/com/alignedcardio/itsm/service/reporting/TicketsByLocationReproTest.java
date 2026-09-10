package com.alignedcardio.itsm.service.reporting;

import com.alignedcardio.itsm.api.reporting.TicketsByLocationResponse;
import com.alignedcardio.itsm.entity.Location;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketsByLocationReproTest {

    @Mock
    private EntityManager entityManager;

    @Mock
    private TypedQuery<Location> locationQuery;

    @Mock
    private TypedQuery<Tuple> incidentQuery;

    @Mock
    private TypedQuery<Tuple> serviceRequestQuery;

    @Mock
    private TypedQuery<Tuple> breachedQuery;

    @Mock
    private Tuple incidentTuple;

    @Test
    void openTicketsByLocationShouldReturnRows() {
        setupMocks();

        ReportingService service = new ReportingService(entityManager);
        UUID orgId = UUID.randomUUID();

        List<TicketsByLocationResponse> result = service.ticketsByLocation(orgId, "OPEN", null, null);

        assertThat(result).isNotEmpty();
    }

    @Test
    void allTicketsByLocationShouldReturnRows() {
        setupMocks();

        ReportingService service = new ReportingService(entityManager);
        UUID orgId = UUID.randomUUID();

        List<TicketsByLocationResponse> result = service.ticketsByLocation(orgId, "ALL", null, null);

        assertThat(result).isNotEmpty();
    }

    @Test
    void ticketsByLocationWithNullDateRangeShouldReturnResultsWithoutThrowing() {
        setupMocks();

        ReportingService service = new ReportingService(entityManager);
        UUID orgId = UUID.randomUUID();

        List<TicketsByLocationResponse> result = assertDoesNotThrow(
                () -> service.ticketsByLocation(orgId, "OPEN", null, null));

        assertThat(result).isNotEmpty();
        verify(incidentQuery, never()).setParameter(anyString(), isNull());
    }

    @SuppressWarnings("unchecked")
    private void setupMocks() {
        UUID locationId = UUID.randomUUID();
        String locationName = "Office A";

        Location location = new Location();
        location.setId(locationId);
        location.setName(locationName);

        when(entityManager.createQuery(argThat((String s) -> s != null && s.contains("FROM Location l")), eq(Location.class)))
                .thenReturn(locationQuery);
        when(locationQuery.setParameter(eq("orgId"), any(UUID.class))).thenReturn(locationQuery);
        when(locationQuery.getResultStream()).thenReturn(Stream.of(location));

        when(entityManager.createQuery(argThat((String s) -> s != null && s.contains("FROM Incident i")), eq(Tuple.class)))
                .thenReturn(incidentQuery);
        lenient().when(incidentQuery.setParameter(anyString(), any())).thenReturn(incidentQuery);
        when(incidentQuery.getResultList()).thenReturn(List.of(incidentTuple));

        when(entityManager.createQuery(argThat((String s) -> s != null && s.contains("FROM ServiceRequest sr")), eq(Tuple.class)))
                .thenReturn(serviceRequestQuery);
        lenient().when(serviceRequestQuery.setParameter(anyString(), any())).thenReturn(serviceRequestQuery);
        when(serviceRequestQuery.getResultList()).thenReturn(List.of());

        when(entityManager.createQuery(argThat((String s) -> s != null && s.contains("FROM SlaInstance si")), eq(Tuple.class)))
                .thenReturn(breachedQuery);
        lenient().when(breachedQuery.setParameter(anyString(), any())).thenReturn(breachedQuery);
        when(breachedQuery.getResultList()).thenReturn(List.of());

        when(incidentTuple.get(0, UUID.class)).thenReturn(locationId);
        when(incidentTuple.get(1, String.class)).thenReturn(locationName);
        when(incidentTuple.get(2, Long.class)).thenReturn(1L);
        when(incidentTuple.get(3, OffsetDateTime.class)).thenReturn(OffsetDateTime.now());
        when(incidentTuple.get(4, Long.class)).thenReturn(0L);
    }
}
