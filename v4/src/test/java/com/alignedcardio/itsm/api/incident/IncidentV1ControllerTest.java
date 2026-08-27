package com.alignedcardio.itsm.api.incident;

import com.alignedcardio.itsm.config.GlobalExceptionHandler;
import com.alignedcardio.itsm.config.SecurityConfig;
import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.entity.BaseEntity;
import com.alignedcardio.itsm.entity.Incident;
import com.alignedcardio.itsm.service.IncidentAttachmentService;
import com.alignedcardio.itsm.service.IncidentCommentService;
import com.alignedcardio.itsm.service.IncidentService;
import com.alignedcardio.itsm.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IncidentV1Controller.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class IncidentV1ControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private UserService userService;

    @MockBean
    private IncidentService incidentService;

    @MockBean
    private IncidentCommentService commentService;

    @MockBean
    private IncidentAttachmentService attachmentService;

    @Test
    @WithMockUser(roles = "AGENT")
    void newToClosedReturns409() throws Exception {
        AppUser user = new AppUser();
        user.setOrgId(BaseEntity.DEFAULT_ORG_ID);

        when(userService.syncFromJwt(any())).thenReturn(user);
        when(incidentService.updateStatus(eq(user), eq(BaseEntity.DEFAULT_ORG_ID), any(UUID.class), eq(Incident.Status.CLOSED)))
                .thenThrow(new IllegalStateException("Illegal status transition: NEW -> CLOSED"));

        mvc.perform(patch("/api/v1/incidents/{id}/status", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CLOSED\"}"))
                .andExpect(status().isConflict());
    }
}
