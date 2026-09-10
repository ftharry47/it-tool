package com.alignedcardio.itsm.api.auth;

import com.alignedcardio.itsm.config.SecurityConfig;
import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import org.springframework.http.MediaType;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminController.class)
@Import(SecurityConfig.class)
class AdminControllerSecurityTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private UserService userService;

    @Test
    @WithMockUser(roles = "END_USER")
    void endUserCannotListUsers() throws Exception {
        mvc.perform(get("/api/admin/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanListUsers() throws Exception {
        when(userService.findAllByOrgIdDefault()).thenReturn(List.of());
        mvc.perform(get("/api/admin/users"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "END_USER")
    void endUserCannotUpdateUser() throws Exception {
        mvc.perform(patch("/api/admin/users/00000000-0000-0000-0000-000000000000")
                        .content("{\"isActive\":false}")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanUpdateUser() throws Exception {
        AppUser actor = new AppUser();
        actor.setId(UUID.randomUUID());
        when(userService.syncFromJwt(any())).thenReturn(actor);
        when(userService.updateUser(any(UUID.class), any(UpdateUserRequest.class), any(UUID.class)))
                .thenReturn(new CurrentUser(UUID.randomUUID(), "oid", "e", "n", "j", "d",
                        List.of(), true, false, null, false, List.of(), List.of()));
        mvc.perform(patch("/api/admin/users/00000000-0000-0000-0000-000000000000")
                        .content("{\"isActive\":false}")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }
}
