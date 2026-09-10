package com.alignedcardio.itsm.api.location;

import com.alignedcardio.itsm.config.GlobalExceptionHandler;
import com.alignedcardio.itsm.config.SecurityConfig;
import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.entity.BaseEntity;
import com.alignedcardio.itsm.service.LocationService;
import com.alignedcardio.itsm.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LocationController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class LocationControllerSecurityTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private UserService userService;

    @MockBean
    private LocationService locationService;

    @Test
    @WithMockUser(roles = "END_USER")
    void endUserCanListLocations() throws Exception {
        AppUser user = new AppUser();
        user.setOrgId(BaseEntity.DEFAULT_ORG_ID);
        when(userService.syncFromJwt(any())).thenReturn(user);
        when(locationService.list(BaseEntity.DEFAULT_ORG_ID)).thenReturn(List.of());

        mvc.perform(get("/api/v1/locations"))
                .andExpect(status().isOk());
    }

    @Test
    void unauthenticatedCannotListLocations() throws Exception {
        mvc.perform(get("/api/v1/locations"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "END_USER")
    void endUserCannotCreateLocation() throws Exception {
        mvc.perform(post("/api/v1/locations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test Site\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(locationService);
    }

    @Test
    @WithMockUser(roles = "AGENT")
    void agentCannotCreateLocation() throws Exception {
        mvc.perform(post("/api/v1/locations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test Site\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(locationService);
    }

    @Test
    @WithMockUser(roles = "AGENT")
    void agentCannotUpdateLocation() throws Exception {
        mvc.perform(patch("/api/v1/locations/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Renamed\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(locationService);
    }

    @Test
    @WithMockUser(roles = "AGENT")
    void agentCannotDeleteLocation() throws Exception {
        mvc.perform(delete("/api/v1/locations/{id}", UUID.randomUUID()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(locationService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanCreateLocation() throws Exception {
        AppUser user = new AppUser();
        user.setOrgId(BaseEntity.DEFAULT_ORG_ID);
        when(userService.syncFromJwt(any())).thenReturn(user);
        when(locationService.create(any(), eq(BaseEntity.DEFAULT_ORG_ID), any(LocationRequest.class)))
                .thenReturn(new LocationResponse(UUID.randomUUID(), "Test Site", null, null, null,
                        OffsetDateTime.now(), OffsetDateTime.now()));

        mvc.perform(post("/api/v1/locations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test Site\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void superAdminCanDeleteLocation() throws Exception {
        AppUser user = new AppUser();
        user.setOrgId(BaseEntity.DEFAULT_ORG_ID);
        when(userService.syncFromJwt(any())).thenReturn(user);

        mvc.perform(delete("/api/v1/locations/{id}", UUID.randomUUID()))
                .andExpect(status().isNoContent());
    }
}
