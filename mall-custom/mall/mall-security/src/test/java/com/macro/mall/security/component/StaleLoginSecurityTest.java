package com.macro.mall.security.component;

import com.macro.mall.security.config.IgnoreUrlsConfig;
import com.macro.mall.security.config.SecurityConfig;
import com.macro.mall.security.util.JwtTokenUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.util.List;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebAppConfiguration
@SpringJUnitConfig({SecurityConfig.class, StaleLoginSecurityTest.TestConfig.class})
@TestPropertySource(properties = {"jwt.tokenHeader=Authorization", "jwt.tokenHead=Bearer ",
        "jwt.secret=test-only-secret", "jwt.expiration=3600"})
class StaleLoginSecurityTest {
    @Autowired WebApplicationContext context;
    @Autowired UserDetailsService users;
    @Autowired JwtTokenUtil tokens;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        SecurityContextHolder.clearContext();
        reset(users, tokens);
        when(tokens.getUserNameFromToken("old-token")).thenReturn("old-user");
        when(tokens.validateToken(eq("old-token"), any(UserDetails.class))).thenReturn(true);
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean(FilterChainProxy.class)).build();
    }

    @Test
    void missingAccountInAnOldLoginDoesNotBreakPublicProductsAndCategories() throws Exception {
        when(users.loadUserByUsername("old-user")).thenThrow(new UsernameNotFoundException("账号已不存在"));
        for (String path : List.of("/product/zone/PICK", "/product/categoryTreeList")) {
            mvc.perform(get(path).header("Authorization", "Bearer old-token"))
                    .andExpect(status().isOk()).andExpect(content().string("ok"));
        }
    }

    @Test
    void missingAccountGetsUnauthorizedForOrdersAndProfileUpdates() throws Exception {
        when(users.loadUserByUsername("old-user")).thenThrow(new UsernameNotFoundException("账号已不存在"));
        mvc.perform(get("/order/detail/10").header("Authorization", "Bearer old-token"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/sso/profile/nickname").header("Authorization", "Bearer old-token")
                        .contentType("application/json").content("{\"nickname\":\"测试昵称\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void activeAccountsKeepAccessToAuthenticatedActions() throws Exception {
        when(users.loadUserByUsername("old-user")).thenReturn(
                User.withUsername("old-user").password("test").authorities("TEST").build());
        mvc.perform(get("/order/detail/10").header("Authorization", "Bearer old-token"))
                .andExpect(status().isOk());
        mvc.perform(post("/sso/profile/nickname").header("Authorization", "Bearer old-token"))
                .andExpect(status().isOk());
    }

    @Test
    void disabledAccountsCanBrowsePublicProductsButCannotReadOrders() throws Exception {
        when(users.loadUserByUsername("old-user")).thenReturn(
                User.withUsername("old-user").password("test").authorities("TEST").disabled(true).build());
        mvc.perform(get("/product/zone/PICK").header("Authorization", "Bearer old-token"))
                .andExpect(status().isOk());
        mvc.perform(get("/order/detail/10").header("Authorization", "Bearer old-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidOrMissingTokensRetainGuestBrowsingAndCannotUpdateNickname() throws Exception {
        mvc.perform(get("/product/zone/PICK").header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isOk());
        mvc.perform(get("/product/zone/PICK")).andExpect(status().isOk());
        mvc.perform(post("/sso/profile/nickname")).andExpect(status().isUnauthorized());
        verifyNoInteractions(users);
    }

    @Configuration
    @EnableWebMvc
    static class TestConfig {
        @Bean UserDetailsService users() { return mock(UserDetailsService.class); }
        @Bean JwtTokenUtil tokens() { return mock(JwtTokenUtil.class); }
        @Bean JwtAuthenticationTokenFilter jwtFilter() { return new JwtAuthenticationTokenFilter(); }
        @Bean RestAuthenticationEntryPoint authenticationEntryPoint() { return new RestAuthenticationEntryPoint(); }
        @Bean RestfulAccessDeniedHandler accessDeniedHandler() { return new RestfulAccessDeniedHandler(); }
        @Bean IgnoreUrlsConfig ignoredUrls() {
            IgnoreUrlsConfig config = new IgnoreUrlsConfig();
            config.setUrls(List.of("/product/**", "/sso/**"));
            return config;
        }
        @Bean Endpoints endpoints() { return new Endpoints(); }
    }

    @RestController
    static class Endpoints {
        @GetMapping({"/product/zone/PICK", "/product/categoryTreeList", "/order/detail/10"})
        String get() { return "ok"; }
        @PostMapping("/sso/profile/nickname")
        String update() { return "updated"; }
    }
}
