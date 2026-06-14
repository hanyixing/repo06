package co.yiiu.pybbs.service.impl;

import co.yiiu.pybbs.mapper.UserMapper;
import co.yiiu.pybbs.model.User;
import co.yiiu.pybbs.service.ICodeService;
import co.yiiu.pybbs.service.ICollectService;
import co.yiiu.pybbs.service.ICommentService;
import co.yiiu.pybbs.service.INotificationService;
import co.yiiu.pybbs.service.ISystemConfigService;
import co.yiiu.pybbs.service.ITopicService;
import co.yiiu.pybbs.util.MyPage;
import co.yiiu.pybbs.util.identicon.Identicon;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UserService 单元测试。
 * 使用纯 Mockito（不加载 Spring 上下文）。
 * 注意：update() 与 deleteUser() 依赖 RequestContextHolder / SpringContextUtil（需要 Spring 容器与请求上下文），
 * 属于集成测试范畴，这里不做单元测试覆盖。
 */
@ExtendWith(MockitoExtension.class)
public class UserServiceTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private ICollectService collectService;
    @Mock
    private ITopicService topicService;
    @Mock
    private ICommentService commentService;
    @Mock
    private Identicon identicon;
    @Mock
    private INotificationService notificationService;
    @Mock
    private ISystemConfigService systemConfigService;
    @Mock
    private ICodeService codeService;

    @InjectMocks
    private UserService userService;

    @Test
    @DisplayName("根据用户名查询: 委托给 mapper")
    public void testSelectByUsername() {
        User user = new User();
        when(userMapper.selectOne(any())).thenReturn(user);
        assertSame(user, userService.selectByUsername("bob"));
    }

    @Test
    @DisplayName("根据token查询: 委托给 mapper")
    public void testSelectByToken() {
        User user = new User();
        when(userMapper.selectOne(any())).thenReturn(user);
        assertSame(user, userService.selectByToken("token-123"));
    }

    @Test
    @DisplayName("根据手机号查询: 委托给 mapper")
    public void testSelectByMobile() {
        User user = new User();
        when(userMapper.selectOne(any())).thenReturn(user);
        assertSame(user, userService.selectByMobile("13800000000"));
    }

    @Test
    @DisplayName("根据邮箱查询: 委托给 mapper")
    public void testSelectByEmail() {
        User user = new User();
        when(userMapper.selectOne(any())).thenReturn(user);
        assertSame(user, userService.selectByEmail("e@x.com"));
    }

    @Test
    @DisplayName("根据id查询: 委托给 mapper")
    public void testSelectById() {
        User user = new User();
        when(userMapper.selectById(1)).thenReturn(user);
        assertSame(user, userService.selectById(1));
    }

    @Test
    @DisplayName("注册用户(无头像): 自动生成头像、密码 BCrypt 加密、默认激活")
    public void testAddUserGeneratesAvatarAndEncodesPassword() {
        when(userMapper.selectOne(any())).thenReturn(null); // token 唯一
        when(identicon.generator("newuser")).thenReturn("gen-avatar.png");
        User saved = new User();
        when(userMapper.selectById(any())).thenReturn(saved);

        User result = userService.addUser("newuser", "pwd123", null, "e@x.com", "bio", "http://x", false);

        assertSame(saved, result); // 返回的是重新查询出来的对象

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(captor.capture());
        User inserted = captor.getValue();
        assertEquals("newuser", inserted.getUsername());
        assertEquals("gen-avatar.png", inserted.getAvatar());
        assertEquals("e@x.com", inserted.getEmail());
        assertNotNull(inserted.getToken());
        assertTrue(inserted.getActive());
        // 密码被加密：不等于原文且为 BCrypt 哈希
        assertNotEquals("pwd123", inserted.getPassword());
        assertTrue(inserted.getPassword().startsWith("$2"));
        verify(identicon).generator("newuser");
    }

    @Test
    @DisplayName("注册用户(指定头像): 不调用头像生成器")
    public void testAddUserWithProvidedAvatar() {
        when(userMapper.selectOne(any())).thenReturn(null);
        User saved = new User();
        when(userMapper.selectById(any())).thenReturn(saved);

        userService.addUser("u2", "pwd", "custom.png", "e2@x.com", null, null, false);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(captor.capture());
        assertEquals("custom.png", captor.getValue().getAvatar());
        verify(identicon, never()).generator(anyString());
    }

    @Test
    @DisplayName("注册用户(空密码): 密码保持为 null")
    public void testAddUserEmptyPassword() {
        when(userMapper.selectOne(any())).thenReturn(null);
        when(identicon.generator(anyString())).thenReturn("a.png");
        User saved = new User();
        when(userMapper.selectById(any())).thenReturn(saved);

        userService.addUser("u3", "", null, "e3@x.com", null, null, false);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(captor.capture());
        assertNull(captor.getValue().getPassword());
    }

    @Test
    @DisplayName("手机号注册(新用户): 生成用户名与头像并入库")
    public void testAddUserWithMobileNewUser() {
        when(userMapper.selectOne(any())).thenReturn(null); // 手机号/token/用户名查询都返回 null
        when(identicon.generator(anyString())).thenReturn("m.png");
        User saved = new User();
        when(userMapper.selectById(any())).thenReturn(saved);

        User result = userService.addUserWithMobile("13900000000");

        assertSame(saved, result);
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(captor.capture());
        User inserted = captor.getValue();
        assertNotNull(inserted.getUsername());
        assertNotNull(inserted.getToken());
        assertTrue(inserted.getActive());
        assertEquals("m.png", inserted.getAvatar());
    }

    @Test
    @DisplayName("手机号注册(已存在): 直接返回，不再入库")
    public void testAddUserWithMobileExistingUser() {
        User existing = new User();
        existing.setId(1);
        when(userMapper.selectOne(any())).thenReturn(existing);

        User result = userService.addUserWithMobile("13900000000");

        assertSame(existing, result);
        verify(userMapper, never()).insert(any());
    }

    @Test
    @DisplayName("查询积分榜: 委托给 mapper")
    public void testSelectTop() {
        List<User> list = Arrays.asList(new User(), new User());
        when(userMapper.selectList(any())).thenReturn(list);
        assertSame(list, userService.selectTop(10));
    }

    @Test
    @DisplayName("分页查询用户: 读取 page_size 配置并分页")
    public void testSelectAll() {
        Map<String, String> config = new HashMap<>();
        config.put("page_size", "20");
        when(systemConfigService.selectAllConfig()).thenReturn(config);
        MyPage<User> page = new MyPage<>();
        when(userMapper.selectPage(any(), any())).thenReturn(page);

        assertSame(page, userService.selectAll(1, null));
    }

    @Test
    @DisplayName("统计今日新增用户数")
    public void testCountToday() {
        when(userMapper.countToday()).thenReturn(4);
        assertEquals(4, userService.countToday());
    }

    @Test
    @DisplayName("删除 redis 缓存: 空实现，调用不抛异常")
    public void testDelRedisUser() {
        userService.delRedisUser(new User());
    }
}
