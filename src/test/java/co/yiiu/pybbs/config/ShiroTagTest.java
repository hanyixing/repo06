package co.yiiu.pybbs.config;

import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShiroTagTest {

    @Mock
    private Subject subject;
    @Mock
    private SecurityManager securityManager;

    private ShiroTag shiroTag;

    @BeforeEach
    void setUp() {
        ThreadContext.bind(securityManager);
        ThreadContext.bind(subject);
        shiroTag = new ShiroTag();
    }

    @AfterEach
    void tearDown() {
        ThreadContext.unbindSubject();
        ThreadContext.unbindSecurityManager();
        ThreadContext.remove();
    }

    @Test
    void testIsAuthenticated_true() {
        when(subject.isAuthenticated()).thenReturn(true);

        assertTrue(shiroTag.isAuthenticated());
    }

    @Test
    void testIsAuthenticated_false() {
        when(subject.isAuthenticated()).thenReturn(false);

        assertFalse(shiroTag.isAuthenticated());
    }

    @Test
    void testGetPrincipal() {
        when(subject.getPrincipal()).thenReturn("testuser");

        String principal = shiroTag.getPrincipal();

        assertEquals("testuser", principal);
    }

    @Test
    void testGetPrincipal_null() {
        when(subject.getPrincipal()).thenReturn(null);

        String principal = shiroTag.getPrincipal();

        assertNull(principal);
    }

    @Test
    void testHasRole_true() {
        when(subject.hasRole("admin")).thenReturn(true);

        assertTrue(shiroTag.hasRole("admin"));
    }

    @Test
    void testHasRole_false() {
        when(subject.hasRole("admin")).thenReturn(false);

        assertFalse(shiroTag.hasRole("admin"));
    }

    @Test
    void testHasPermission_true() {
        when(subject.isPermitted("topic:list")).thenReturn(true);

        assertTrue(shiroTag.hasPermission("topic:list"));
    }

    @Test
    void testHasPermission_false() {
        when(subject.isPermitted("topic:delete")).thenReturn(false);

        assertFalse(shiroTag.hasPermission("topic:delete"));
    }

    @Test
    void testHasPermission_emptyName() {
        assertFalse(shiroTag.hasPermission(""));
        verify(subject, never()).isPermitted(anyString());
    }

    @Test
    void testHasPermission_nullName() {
        assertFalse(shiroTag.hasPermission(null));
        verify(subject, never()).isPermitted(anyString());
    }

    @Test
    void testHasPermissionOr_onePermitted() {
        when(subject.isPermitted("topic:list", "topic:delete")).thenReturn(new boolean[]{true, false});

        assertTrue(shiroTag.hasPermissionOr("topic:list", "topic:delete"));
    }

    @Test
    void testHasPermissionOr_nonePermitted() {
        when(subject.isPermitted("topic:list", "topic:delete")).thenReturn(new boolean[]{false, false});

        assertFalse(shiroTag.hasPermissionOr("topic:list", "topic:delete"));
    }

    @Test
    void testHasPermissionOr_allPermitted() {
        when(subject.isPermitted("topic:list", "topic:delete")).thenReturn(new boolean[]{true, true});

        assertTrue(shiroTag.hasPermissionOr("topic:list", "topic:delete"));
    }

    @Test
    void testHasPermissionAnd_allPermitted() {
        when(subject.isPermitted("topic:list", "topic:add")).thenReturn(new boolean[]{true, true});

        assertTrue(shiroTag.hasPermissionAnd("topic:list", "topic:add"));
    }

    @Test
    void testHasPermissionAnd_oneNotPermitted() {
        when(subject.isPermitted("topic:list", "topic:delete")).thenReturn(new boolean[]{true, false});

        assertFalse(shiroTag.hasPermissionAnd("topic:list", "topic:delete"));
    }

    @Test
    void testHasPermissionAnd_nonePermitted() {
        when(subject.isPermitted("topic:list", "topic:delete")).thenReturn(new boolean[]{false, false});

        assertFalse(shiroTag.hasPermissionAnd("topic:list", "topic:delete"));
    }

    @Test
    void testHasAllPermission_true() {
        when(subject.isPermittedAll("topic:list", "topic:add", "topic:delete")).thenReturn(true);

        assertTrue(shiroTag.hasAllPermission("topic:list", "topic:add", "topic:delete"));
    }

    @Test
    void testHasAllPermission_false() {
        when(subject.isPermittedAll("topic:list", "topic:add", "topic:delete")).thenReturn(false);

        assertFalse(shiroTag.hasAllPermission("topic:list", "topic:add", "topic:delete"));
    }
}
