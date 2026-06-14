package co.yiiu.pybbs.service;

import co.yiiu.pybbs.mapper.UserMapper;
import co.yiiu.pybbs.model.User;
import co.yiiu.pybbs.service.impl.UserService;
import co.yiiu.pybbs.util.MyPage;
import co.yiiu.pybbs.util.identicon.Identicon;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

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

    private User mockUser;

    @BeforeEach
    void setUp() {
        mockUser = new User();
        mockUser.setId(1);
        mockUser.setUsername("testuser");
        mockUser.setToken("test-token-uuid");
        mockUser.setScore(100);
        mockUser.setEmail("test@example.com");
        mockUser.setMobile("13800138000");
        mockUser.setActive(true);
        mockUser.setAvatar("/avatar/test.png");
    }

    @Test
    void testSelectByUsername() {
        when(userMapper.selectOne(any(QueryWrapper.class))).thenReturn(mockUser);

        User result = userService.selectByUsername("testuser");

        assertNotNull(result);
        assertEquals("testuser", result.getUsername());
        verify(userMapper).selectOne(any(QueryWrapper.class));
    }

    @Test
    void testSelectByUsername_notFound() {
        when(userMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

        User result = userService.selectByUsername("nonexistent");

        assertNull(result);
    }

    @Test
    void testSelectByToken() {
        when(userMapper.selectOne(any(QueryWrapper.class))).thenReturn(mockUser);

        User result = userService.selectByToken("test-token-uuid");

        assertNotNull(result);
        assertEquals("test-token-uuid", result.getToken());
    }

    @Test
    void testSelectByMobile() {
        when(userMapper.selectOne(any(QueryWrapper.class))).thenReturn(mockUser);

        User result = userService.selectByMobile("13800138000");

        assertNotNull(result);
        assertEquals("13800138000", result.getMobile());
    }

    @Test
    void testSelectByEmail() {
        when(userMapper.selectOne(any(QueryWrapper.class))).thenReturn(mockUser);

        User result = userService.selectByEmail("test@example.com");

        assertNotNull(result);
        assertEquals("test@example.com", result.getEmail());
    }

    @Test
    void testSelectById() {
        when(userMapper.selectById(1)).thenReturn(mockUser);

        User result = userService.selectById(1);

        assertNotNull(result);
        assertEquals(1, result.getId());
        assertEquals("testuser", result.getUsername());
        verify(userMapper).selectById(1);
    }

    @Test
    void testSelectById_notFound() {
        when(userMapper.selectById(999)).thenReturn(null);

        User result = userService.selectById(999);

        assertNull(result);
    }

    @Test
    void testSelectByIdWithoutCache() {
        when(userMapper.selectById(1)).thenReturn(mockUser);

        User result = userService.selectByIdWithoutCache(1);

        assertNotNull(result);
        assertEquals(1, result.getId());
    }

    @Test
    void testSelectTop() {
        List<User> users = Arrays.asList(mockUser);
        when(userMapper.selectList(any(QueryWrapper.class))).thenReturn(users);

        List<User> result = userService.selectTop(10);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("testuser", result.get(0).getUsername());
        verify(userMapper).selectList(any(QueryWrapper.class));
    }

    @Test
    void testAddUser() {
        when(userMapper.selectOne(any(QueryWrapper.class))).thenReturn(null); // token不重复
        when(userMapper.insert(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(1);
            return 1;
        });
        when(identicon.generator("newuser")).thenReturn("/avatar/newuser.png");
        when(userMapper.selectById(1)).thenReturn(mockUser);

        User result = userService.addUser("newuser", "password123", null, "new@test.com", "bio", "http://website.com", false);

        assertNotNull(result);
        verify(userMapper).insert(any(User.class));
        verify(identicon).generator("newuser");
    }

    @Test
    void testAddUser_withAvatar() {
        when(userMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);
        when(userMapper.insert(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(2);
            return 1;
        });
        when(userMapper.selectById(2)).thenReturn(mockUser);

        User result = userService.addUser("newuser", "password123", "/custom/avatar.png", "new@test.com", null, null, false);

        assertNotNull(result);
        verify(identicon, never()).generator(anyString());
    }

    @Test
    void testAddUserWithMobile_existingUser() {
        when(userMapper.selectOne(any(QueryWrapper.class))).thenReturn(mockUser);

        User result = userService.addUserWithMobile("13800138000");

        assertNotNull(result);
        assertEquals("testuser", result.getUsername());
        verify(userMapper, never()).insert(any(User.class));
    }

    @Test
    void testAddUserWithMobile_newUser() {
        // First call for selectByMobile returns null (user not found)
        // Second call for selectByUsername (generateUsername check) also returns null
        when(userMapper.selectOne(any(QueryWrapper.class)))
                .thenReturn(null) // selectByMobile: no existing user
                .thenReturn(null) // selectByToken: token not duplicate
                .thenReturn(null) // selectByUsername: username not duplicate
        ;
        when(userMapper.insert(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(3);
            return 1;
        });
        when(identicon.generator(anyString())).thenReturn("/avatar/generated.png");
        when(userMapper.selectById(3)).thenReturn(mockUser);

        User result = userService.addUserWithMobile("13900139000");

        assertNotNull(result);
        verify(userMapper).insert(any(User.class));
        verify(identicon).generator(anyString());
    }

    @Test
    void testCountToday() {
        when(userMapper.countToday()).thenReturn(3);

        int result = userService.countToday();

        assertEquals(3, result);
        verify(userMapper).countToday();
    }

    @Test
    void testSelectAll() {
        Map<String, String> configMap = new HashMap<>();
        configMap.put("page_size", "10");
        when(systemConfigService.selectAllConfig()).thenReturn(configMap);
        MyPage<User> mockPage = new MyPage<>(1, 10);
        mockPage.setRecords(Arrays.asList(mockUser));
        when(userMapper.selectPage(any(MyPage.class), any(QueryWrapper.class))).thenReturn(mockPage);

        IPage<User> result = userService.selectAll(1, "testuser");

        assertNotNull(result);
        verify(userMapper).selectPage(any(MyPage.class), any(QueryWrapper.class));
    }

    @Test
    void testSelectAll_withoutUsername() {
        Map<String, String> configMap = new HashMap<>();
        configMap.put("page_size", "10");
        when(systemConfigService.selectAllConfig()).thenReturn(configMap);
        MyPage<User> mockPage = new MyPage<>(1, 10);
        mockPage.setRecords(Arrays.asList(mockUser));
        when(userMapper.selectPage(any(MyPage.class), any(QueryWrapper.class))).thenReturn(mockPage);

        IPage<User> result = userService.selectAll(1, null);

        assertNotNull(result);
    }

    @Test
    void testSelectByIdNoCatch() {
        when(userMapper.selectById(1)).thenReturn(mockUser);

        User result = userService.selectByIdNoCatch(1);

        assertNotNull(result);
        assertEquals(1, result.getId());
    }
}
