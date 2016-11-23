package org.cloudfoundry.identity.uaa.login;

import org.cloudfoundry.identity.uaa.account.UserAccountStatus;
import org.cloudfoundry.identity.uaa.mock.InjectedMockContextTest;
import org.cloudfoundry.identity.uaa.mock.util.MockMvcUtils;
import org.cloudfoundry.identity.uaa.scim.ScimUser;
import org.cloudfoundry.identity.uaa.security.web.CookieBasedCsrfTokenRepository;
import org.cloudfoundry.identity.uaa.util.JsonUtils;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.common.util.RandomValueStringGenerator;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import javax.servlet.http.Cookie;
import java.net.URLEncoder;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class ForcePasswordChangeControllerMockMvcTest extends InjectedMockContextTest {
    private ScimUser user;
    private String token;

    @Before
    public void setup() throws Exception {
        String username = new RandomValueStringGenerator().generate() + "@test.org";
        user = new ScimUser(null, username, "givenname","familyname");
        user.setPrimaryEmail(username);
        user.setPassword("secret");
        token = MockMvcUtils.utils().getClientCredentialsOAuthAccessToken(getMockMvc(), "admin", "adminsecret", null, null);
        user = MockMvcUtils.utils().createUser(getMockMvc(), token, user);
    }

    @Test
    public void testHandleChangePasswordForUser() throws Exception {
        forcePasswordChangeForUser();
        MockHttpSession session = new MockHttpSession();
        Cookie cookie = new Cookie(CookieBasedCsrfTokenRepository.DEFAULT_CSRF_COOKIE_NAME, "csrf1");

        MockHttpServletRequestBuilder invalidPost = post("/login.do")
                .param("username", user.getUserName())
                .param("password", "secret")
                .session(session)
                .cookie(cookie)
                .param(CookieBasedCsrfTokenRepository.DEFAULT_CSRF_COOKIE_NAME, "csrf1");
        getMockMvc().perform(invalidPost)
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/force_password_change"));

        MockHttpServletRequestBuilder validPost = post("/force_password_change")
                .param("password", "test")
                .param("password_confirmation", "test")
                .session(session)
                .cookie(cookie);
        validPost.with(csrf());
        getMockMvc().perform(validPost)
                .andExpect(status().isFound())
                .andExpect(redirectedUrl(("/")))
                .andExpect(currentUserCookie(user.getId()));
    }

    @Test
    public void testHandleChangePasswordForUserInvalid() throws Exception {
        forcePasswordChangeForUser();

        MockHttpServletRequestBuilder validPost = post("/force_password_change")
                .param("password", "test")
                .param("password_confirmation", "test");
        validPost.with(csrf());
        getMockMvc().perform(validPost)
                .andExpect(status().isFound())
                .andExpect(redirectedUrl(("/login")));
    }

    private void forcePasswordChangeForUser() throws Exception {
        UserAccountStatus userAccountStatus = new UserAccountStatus();
        userAccountStatus.setPasswordChangeRequired(true);
        String jsonStatus = JsonUtils.writeValueAsString(userAccountStatus);
        getMockMvc().perform(
            patch("/Users/"+user.getId()+"/status")
                .header("Authorization", "Bearer "+token)
                .accept(APPLICATION_JSON)
                .contentType(APPLICATION_JSON)
                .content(jsonStatus))
            .andExpect(status().isOk());
    }

    private static ResultMatcher currentUserCookie(String userId) {
        return result -> {
            cookie().value("Current-User", URLEncoder.encode("{\"userId\":\"" + userId + "\"}", "UTF-8")).match(result);
            cookie().maxAge("Current-User", 365*24*60*60);
            cookie().path("Current-User", "").match(result);
        };
    }
}
