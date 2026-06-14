package co.yiiu.pybbs.config.realm;

import co.yiiu.pybbs.model.AdminUser;
import co.yiiu.pybbs.model.Permission;
import co.yiiu.pybbs.model.Role;
import co.yiiu.pybbs.service.IAdminUserService;
import co.yiiu.pybbs.service.IPermissionService;
import co.yiiu.pybbs.service.IRoleService;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.UnknownAccountException;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * MyShiroRealm 认证 / 授权单元测试。
 * 放在与被测类相同的包下，以便访问 protected 的 doGetAuthenticationInfo / doGetAuthorizationInfo。
 * 覆盖正常登录、用户不存在的异常场景，以及角色 / 权限的组装。
 */
@ExtendWith(MockitoExtension.class)
public class MyShiroRealmTest {

    @Mock
    private IAdminUserService adminUserService;
    @Mock
    private IRoleService roleService;
    @Mock
    private IPermissionService permissionService;

    @InjectMocks
    private MyShiroRealm myShiroRealm;

    @Test
    @DisplayName("认证成功: 返回的凭证为数据库中的密码，主体为用户名")
    public void testAuthenticationSuccess() {
        AdminUser adminUser = new AdminUser();
        adminUser.setUsername("admin");
        adminUser.setPassword("secret-hash");
        when(adminUserService.selectByUsername("admin")).thenReturn(adminUser);

        UsernamePasswordToken token = new UsernamePasswordToken("admin", "secret-hash");
        AuthenticationInfo info = myShiroRealm.doGetAuthenticationInfo(token);

        assertNotNull(info);
        assertEquals("secret-hash", info.getCredentials());
        assertEquals("admin", info.getPrincipals().getPrimaryPrincipal());
    }

    @Test
    @DisplayName("认证失败: 用户不存在时抛出 UnknownAccountException")
    public void testAuthenticationUnknownAccount() {
        when(adminUserService.selectByUsername("ghost")).thenReturn(null);

        UsernamePasswordToken token = new UsernamePasswordToken("ghost", "whatever");
        assertThrows(UnknownAccountException.class, () -> myShiroRealm.doGetAuthenticationInfo(token));
    }

    @Test
    @DisplayName("授权: 正确组装角色与权限")
    public void testAuthorizationAssemblesRolesAndPermissions() {
        AdminUser adminUser = new AdminUser();
        adminUser.setUsername("admin");
        adminUser.setRoleId(1);
        when(adminUserService.selectByUsername(any())).thenReturn(adminUser);

        Role role = new Role();
        role.setName("administrator");
        when(roleService.selectById(1)).thenReturn(role);

        Permission p1 = new Permission();
        p1.setValue("user:list");
        Permission p2 = new Permission();
        p2.setValue("user:delete");
        when(permissionService.selectByRoleId(1)).thenReturn(Arrays.asList(p1, p2));

        PrincipalCollection principals = new SimplePrincipalCollection("admin", "myRealm");
        AuthorizationInfo authz = myShiroRealm.doGetAuthorizationInfo(principals);

        assertTrue(authz.getRoles().contains("administrator"));
        assertTrue(authz.getStringPermissions().contains("user:list"));
        assertTrue(authz.getStringPermissions().contains("user:delete"));
    }

    @Test
    @DisplayName("授权: 无权限时只包含角色，权限集合为空")
    public void testAuthorizationWithoutPermissions() {
        AdminUser adminUser = new AdminUser();
        adminUser.setUsername("admin");
        adminUser.setRoleId(2);
        when(adminUserService.selectByUsername(any())).thenReturn(adminUser);

        Role role = new Role();
        role.setName("guest");
        when(roleService.selectById(2)).thenReturn(role);
        when(permissionService.selectByRoleId(2)).thenReturn(Collections.emptyList());

        PrincipalCollection principals = new SimplePrincipalCollection("admin", "myRealm");
        AuthorizationInfo authz = myShiroRealm.doGetAuthorizationInfo(principals);

        assertTrue(authz.getRoles().contains("guest"));
        assertTrue(authz.getStringPermissions().isEmpty());
    }
}
