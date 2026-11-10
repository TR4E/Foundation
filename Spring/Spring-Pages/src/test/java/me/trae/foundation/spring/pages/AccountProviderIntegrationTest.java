package me.trae.foundation.spring.pages;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import me.trae.foundation.spring.common.account.AccountIdentity;
import me.trae.foundation.spring.common.account.AccountRoleDefinition;
import me.trae.foundation.spring.pages.annotation.Render;
import me.trae.foundation.spring.pages.role.AccountPageAccessResolver;
import me.trae.foundation.spring.security.account.EmptySecurityAccountProvider;
import me.trae.foundation.spring.security.account.SecurityAccountProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@SpringBootTest
final class AccountProviderIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SecurityAccountProvider<?> accountProvider;

    @Test
    void theApplicationAccountManagerReplacesTheDefaultProvider() {
        assertInstanceOf(AccountManager.class, this.accountProvider);
    }

    @Test
    void anonymousAccountsRedirectToLogin() throws Exception {
        this.mockMvc.perform(get("/admin"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/auth?redirect=%2Fadmin"));
    }

    @Test
    void authenticationAndRoleCheckReuseTheAccountResolvedOnTheRequest() {
        final AccountManager accountManager = new AccountManager();
        final AccountPageAccessResolver accountPageAccessResolver = new AccountPageAccessResolver(accountManager);
        final MockHttpServletRequest httpServletRequest = new MockHttpServletRequest();
        httpServletRequest.addHeader("Test-Role", "ADMIN");
        final MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();

        assertTrue(accountPageAccessResolver.isAuthenticated(httpServletRequest, httpServletResponse));
        assertTrue(accountPageAccessResolver.hasRole(httpServletRequest, httpServletResponse, AccountRole.ADMIN));
        assertEquals(1, accountManager.resolutionCount.get());
    }

    @Test
    void anAnonymousResolutionIsAlsoReusedWithinTheRequest() {
        final AccountManager accountManager = new AccountManager();
        final AccountPageAccessResolver accountPageAccessResolver = new AccountPageAccessResolver(accountManager);
        final MockHttpServletRequest httpServletRequest = new MockHttpServletRequest();
        final MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();

        assertFalse(accountPageAccessResolver.isAuthenticated(httpServletRequest, httpServletResponse));
        assertFalse(accountPageAccessResolver.hasRole(httpServletRequest, httpServletResponse, AccountRole.ADMIN));
        assertEquals(1, accountManager.resolutionCount.get());
    }

    @Test
    void authenticatedAccountsWithoutTheRoleAreForbidden() throws Exception {
        this.mockMvc.perform(get("/admin").header("Test-Role", "MEMBER"))
                .andExpect(status().isForbidden());
    }

    @Test
    void applicationAccountsAndRoleEnumsGrantPageAccess() throws Exception {
        this.mockMvc.perform(get("/admin").header("Test-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(content().string("admin page"));
    }

    @Test
    void higherRolesInheritPageAccess() throws Exception {
        this.mockMvc.perform(get("/admin").header("Test-Role", "OWNER"))
                .andExpect(status().isOk())
                .andExpect(content().string("admin page"));
    }

    @Test
    void moderatorsCannotAccessAdminPages() throws Exception {
        this.mockMvc.perform(get("/admin").header("Test-Role", "MODERATOR"))
                .andExpect(status().isForbidden());
    }

    @Test
    void applicationAuthorizationDecisionsAreRespected() throws Exception {
        this.mockMvc.perform(get("/admin").header("Test-Role", "OWNER").header("Test-Deny-Role", "true"))
                .andExpect(status().isForbidden());
    }

    @Test
    void protectedAssetsAreHiddenWithoutTheRole() throws Exception {
        this.mockMvc.perform(get("/js/admin.js").header("Test-Role", "MEMBER"))
                .andExpect(status().isNotFound());
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestConfiguration {

        @Bean
        public AccountManager accountManager() {
            return new AccountManager();
        }

        @Bean
        public Page adminPage() {
            return new AdminPage();
        }
    }

    static final class AccountManager extends EmptySecurityAccountProvider {

        private final AtomicInteger resolutionCount = new AtomicInteger();

        @Override
        public Optional<Account> getAccountByRequest(final HttpServletRequest httpServletRequest, final HttpServletResponse httpServletResponse) {
            this.resolutionCount.incrementAndGet();
            final String role = httpServletRequest.getHeader("Test-Role");
            if (role == null) {
                return Optional.empty();
            }

            return Optional.of(new Account(UUID.fromString("00000000-0000-0000-0000-000000000001"), AccountRole.valueOf(role), Boolean.parseBoolean(httpServletRequest.getHeader("Test-Deny-Role"))));
        }
    }

    static final class AdminPage extends Page {

        private AdminPage() {
            super("/admin", AccountRole.ADMIN);
        }

        @Override
        public Set<String> getAssetPaths() {
            return Set.of("/js/admin.js");
        }

        @Render
        @ResponseBody
        public String render() {
            return "admin page";
        }
    }

    @EqualsAndHashCode(onlyExplicitlyIncluded = true)
    @AllArgsConstructor
    @Getter
    static final class Account implements AccountIdentity<AccountRole> {

        @EqualsAndHashCode.Include
        private final UUID id;

        private final AccountRole role;

        private final boolean roleAccessDenied;

        @Override
        public boolean hasRole(final AccountRoleDefinition accountRoleDefinition) {
            return !this.roleAccessDenied && AccountIdentity.super.hasRole(accountRoleDefinition);
        }
    }

    enum AccountRole implements AccountRoleDefinition {

        MEMBER, MODERATOR, ADMIN, OWNER
    }
}