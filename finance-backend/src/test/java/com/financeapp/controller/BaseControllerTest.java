package com.financeapp.controller;

import com.financeapp.model.User;
import com.financeapp.security.JwtUtil;
import com.financeapp.util.TestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.mockito.Mockito.when;

/**
 * Base class for controller integration tests using SpringBootTest with H2.
 * Provides authenticated MockMvc setup using real JWT generation.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
public abstract class BaseControllerTest {

    protected static final String EMAIL_A = "user-a@example.com";
    protected static final String EMAIL_B = "user-b@example.com";
    protected static final Long USER_ID_A = 1L;
    protected static final Long USER_ID_B = 2L;

    @Autowired protected WebApplicationContext context;
    @Autowired protected JwtUtil jwtUtil;

    @MockitoBean
    protected UserDetailsService userDetailsService;

    protected MockMvc mockMvc;

    protected User userA;
    protected User userB;
    protected String tokenA;
    protected String tokenB;

    @BeforeEach
    void setupBase() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        userA = TestFactory.buildUser(USER_ID_A, EMAIL_A);
        userB = TestFactory.buildUser(USER_ID_B, EMAIL_B);

        tokenA = jwtUtil.generateToken(EMAIL_A);
        tokenB = jwtUtil.generateToken(EMAIL_B);

        when(userDetailsService.loadUserByUsername(EMAIL_A)).thenReturn(userA);
        when(userDetailsService.loadUserByUsername(EMAIL_B)).thenReturn(userB);
    }

    /** Adds Bearer authorization header for User A. */
    protected MockHttpServletRequestBuilder asUserA(MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + tokenA);
    }

    /** Adds Bearer authorization header for User B. */
    protected MockHttpServletRequestBuilder asUserB(MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + tokenB);
    }

    /** Overload for multipart requests — User A. */
    protected MockMultipartHttpServletRequestBuilder asUserA(MockMultipartHttpServletRequestBuilder request) {
        return (MockMultipartHttpServletRequestBuilder) request.header("Authorization", "Bearer " + tokenA);
    }

    /** Overload for multipart requests — User B. */
    protected MockMultipartHttpServletRequestBuilder asUserB(MockMultipartHttpServletRequestBuilder request) {
        return (MockMultipartHttpServletRequestBuilder) request.header("Authorization", "Bearer " + tokenB);
    }
}
