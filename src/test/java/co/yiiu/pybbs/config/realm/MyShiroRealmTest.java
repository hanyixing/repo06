package co.yiiu.pybbs.config.realm;

import co.yiiu.pybbs.model.AdminUser;
import co.yiiu.pybbs.model.Permission;
import co.yiiu.pybbs.model.Role;
import co.yiiu.pybbs.service.IAdminUserService;
import co.yiiu.pybbs.service.IPermissionService;
import co.yiiu.pybbs.service.IRoleService;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.authc.UnknownAccountException;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MyShiroRealmTest {

    @Mock
    private IAdminUserService adminUserService;
    @Mock
    private IRoleService roleService;
    @Mock
    private IPermissionService permissionService;

    @InjectMocks
    private MyShiroRealm myShiroRealm;

    private AdminUser adminUser;
    private Role role;

    @BeforeEach
    void setUp() {
        adminUser = new AdminUser();
        adminUser.setId(1);
        adminUser.setUsername("admin");
        adminUser.setPassword("encodedPassword123");
        adminUser.setRoleId(1);

        role = new Role();
        role.setId(1);
        role.setName("administrator");
    }

    @Test
    void testDoGetAuthenticationInfo_success() {
        when(adminUserService.selectByUsername("admin")).thenReturn(adminUser);

        AuthenticationToken token = new UsernamePasswordToken("admin", "password");
        AuthenticationInfo info = myShiroRealm.doGetAuthenticationInfo(token);

        assertNotNull(info);
        assertEquals("admin", info.getPrincipals().getPrimaryPrincipal());
        assertEquals("encodedPassword123", info.getCredentials());
        verify(adminUserService).selectByUsername("admin");
    }

    @Test
    void testDoGetAuthenticationInfo_unknownAccount() {
        when(adminUserService.selectByUsername("nonexistent")).thenReturn(null);

        AuthenticationToken token = new UsernamePasswordToken("nonexistent", "password");

        assertThrows(UnknownAccountException.class, () -> myShiroRealm.doGetAuthenticationInfo(token));
        verify(adminUserService).selectByUsername("nonexistent");
    }

    @Test
    void testDoGetAuthorizationInfo() {
        when(adminUserService.selectByUsername("admin")).thenReturn(adminUser);
        when(roleService.selectById(1)).thenReturn(role);

        Permission perm1 = new Permission();
        perm1.setId(1);
        perm1.setName("Topic Management");
        perm1.setValue("topic:list");

        Permission perm2 = new Permission();
        perm2.setId(2);
        perm2.setName("Comment Management");
        perm2.setValue("comment:list");

        when(permissionService.selectByRoleId(1)).thenReturn(Arrays.asList(perm1, perm2));

        PrincipalCollection principals = new SimplePrincipalCollection("admin", "testRealm");
        AuthorizationInfo info = myShiroRealm.doGetAuthorizationInfo(principals);

        assertNotNull(info);
        assertTrue(info.getRoles().contains("administrator"));
        assertTrue(info.getStringPermissions().contains("topic:list"));
        assertTrue(info.getStringPermissions().contains("comment:list"));
        assertEquals(2, info.getStringPermissions().size());

        verify(adminUserService).selectByUsername("admin");
        verify(roleService).selectById(1);
        verify(permissionService).selectByRoleId(1);
    }

    @Test
    void testDoGetAuthorizationInfo_noPermissions() {
        when(adminUserService.selectByUsername("admin")).thenReturn(adminUser);
        when(roleService.selectById(1)).thenReturn(role);
        when(permissionService.selectByRoleId(1)).thenReturn(Collections.emptyList());

        PrincipalCollection principals = new SimplePrincipalCollection("admin", "testRealm");
        AuthorizationInfo info = myShiroRealm.doGetAuthorizationInfo(principals);

        assertNotNull(info);
        assertTrue(info.getRoles().contains("administrator"));
        assertTrue(info.getStringPermissions().isEmpty());
    }

    @Test
    void testDoGetAuthorizationInfo_singlePermission() {
        when(adminUserService.selectByUsername("admin")).thenReturn(adminUser);
        when(roleService.selectById(1)).thenReturn(role);

        Permission perm = new Permission();
        perm.setValue("system:config");
        when(permissionService.selectByRoleId(1)).thenReturn(Collections.singletonList(perm));

        PrincipalCollection principals = new SimplePrincipalCollection("admin", "testRealm");
        AuthorizationInfo info = myShiroRealm.doGetAuthorizationInfo(principals);

        assertNotNull(info);
        assertEquals(1, info.getStringPermissions().size());
        assertTrue(info.getStringPermissions().contains("system:config"));
    }
}
