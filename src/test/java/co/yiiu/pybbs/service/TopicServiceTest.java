package co.yiiu.pybbs.service;

import co.yiiu.pybbs.mapper.TopicMapper;
import co.yiiu.pybbs.model.Tag;
import co.yiiu.pybbs.model.Topic;
import co.yiiu.pybbs.model.User;
import co.yiiu.pybbs.service.impl.IndexedService;
import co.yiiu.pybbs.service.impl.TopicService;
import co.yiiu.pybbs.util.MyPage;
import co.yiiu.pybbs.util.SensitiveWordUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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
class TopicServiceTest {

    @Mock
    private TopicMapper topicMapper;
    @Mock
    private ISystemConfigService systemConfigService;
    @Mock
    private ITopicTagService topicTagService;
    @Mock
    private ITagService tagService;
    @Mock
    private ICollectService collectService;
    @Mock
    private ICommentService commentService;
    @Mock
    private IUserService userService;
    @Mock
    private INotificationService notificationService;
    @Mock
    private IndexedService indexedService;

    @InjectMocks
    private TopicService topicService;

    private Map<String, String> configMap;
    private User mockUser;
    private Topic mockTopic;

    @BeforeEach
    void setUp() {
        SensitiveWordUtil.init(new HashSet<>());

        configMap = new HashMap<>();
        configMap.put("page_size", "10");
        configMap.put("content_style", "MD");
        configMap.put("create_topic_score", "5");
        configMap.put("delete_topic_score", "3");
        configMap.put("up_topic_score", "2");

        mockUser = new User();
        mockUser.setId(1);
        mockUser.setUsername("testuser");
        mockUser.setScore(100);

        mockTopic = new Topic();
        mockTopic.setId(1);
        mockTopic.setTitle("Test Topic");
        mockTopic.setContent("Test Content");
        mockTopic.setUserId(1);
        mockTopic.setView(10);
        mockTopic.setCommentCount(5);
        mockTopic.setCollectCount(0);
        mockTopic.setTop(false);
        mockTopic.setGood(false);
        mockTopic.setUpIds("");
    }

    @Test
    void testSelectById() {
        when(topicMapper.selectById(1)).thenReturn(mockTopic);

        Topic result = topicService.selectById(1);

        assertNotNull(result);
        assertEquals(1, result.getId());
        assertEquals("Test Topic", result.getTitle());
        verify(topicMapper).selectById(1);
    }

    @Test
    void testSelectById_notFound() {
        when(topicMapper.selectById(999)).thenReturn(null);

        Topic result = topicService.selectById(999);

        assertNull(result);
        verify(topicMapper).selectById(999);
    }

    @Test
    void testSelectByTitle() {
        when(topicMapper.selectOne(any(QueryWrapper.class))).thenReturn(mockTopic);

        Topic result = topicService.selectByTitle("Test Topic");

        assertNotNull(result);
        assertEquals("Test Topic", result.getTitle());
        verify(topicMapper).selectOne(any(QueryWrapper.class));
    }

    @Test
    void testInsert_withTags() {
        when(systemConfigService.selectAllConfig()).thenReturn(configMap);
        when(topicMapper.insert(any(Topic.class))).thenAnswer(invocation -> {
            Topic t = invocation.getArgument(0);
            t.setId(1);
            return 1;
        });
        when(tagService.insertTag("java,spring")).thenReturn(Arrays.asList(new Tag(), new Tag()));

        Topic result = topicService.insert("Test Topic", "Test Content", "java,spring", mockUser);

        assertNotNull(result);
        assertEquals("Test Topic", result.getTitle());
        assertEquals("MD", result.getStyle());
        assertFalse(result.getTop());
        assertFalse(result.getGood());
        assertEquals(1, result.getView());
        assertEquals(0, result.getCollectCount());
        assertEquals(0, result.getCommentCount());
        verify(topicMapper).insert(any(Topic.class));
        verify(tagService).insertTag("java,spring");
        verify(topicTagService).insertTopicTag(eq(1), anyList());
        verify(indexedService).indexTopic(eq("1"), eq("Test Topic"), eq("Test Content"));
        // 验证用户积分增加
        assertEquals(105, mockUser.getScore());
        verify(userService).update(mockUser);
    }

    @Test
    void testInsert_withoutTags() {
        when(systemConfigService.selectAllConfig()).thenReturn(configMap);
        when(topicMapper.insert(any(Topic.class))).thenAnswer(invocation -> {
            Topic t = invocation.getArgument(0);
            t.setId(2);
            return 1;
        });

        Topic result = topicService.insert("No Tag Topic", "Content", null, mockUser);

        assertNotNull(result);
        assertEquals("No Tag Topic", result.getTitle());
        verify(tagService, never()).insertTag(anyString());
        verify(topicTagService, never()).insertTopicTag(anyInt(), anyList());
        verify(indexedService).indexTopic(eq("2"), eq("No Tag Topic"), eq("Content"));
    }

    @Test
    void testUpdateViewCount() {
        when(topicMapper.updateById(any(Topic.class))).thenReturn(1);
        Topic result = topicService.updateViewCount(mockTopic, "127.0.0.1");

        assertEquals(11, result.getView());
        verify(topicMapper).updateById(mockTopic);
    }

    @Test
    void testUpdate_withTags() {
        when(topicMapper.updateById(any(Topic.class))).thenReturn(1);
        when(tagService.insertTag("newTag")).thenReturn(Collections.singletonList(new Tag()));

        topicService.update(mockTopic, "newTag");

        verify(topicMapper).updateById(mockTopic);
        verify(tagService).reduceTopicCount(mockTopic.getId());
        verify(tagService).insertTag("newTag");
        verify(topicTagService).insertTopicTag(eq(mockTopic.getId()), anyList());
        verify(indexedService).indexTopic(eq("1"), eq("Test Topic"), eq("Test Content"));
    }

    @Test
    void testUpdate_withoutTags() {
        when(topicMapper.updateById(any(Topic.class))).thenReturn(1);

        topicService.update(mockTopic, null);

        verify(topicMapper).updateById(mockTopic);
        verify(tagService, never()).reduceTopicCount(anyInt());
        verify(indexedService).indexTopic(eq("1"), eq("Test Topic"), eq("Test Content"));
    }

    @Test
    void testDelete() {
        when(systemConfigService.selectAllConfig()).thenReturn(configMap);
        when(userService.selectById(1)).thenReturn(mockUser);

        topicService.delete(mockTopic);

        verify(notificationService).deleteByTopicId(1);
        verify(collectService).deleteByTopicId(1);
        verify(commentService).deleteByTopicId(1);
        verify(tagService).reduceTopicCount(1);
        verify(topicTagService).deleteByTopicId(1);
        verify(userService).selectById(1);
        assertEquals(97, mockUser.getScore()); // 100 - 3 (delete_topic_score)
        verify(userService).update(mockUser);
        verify(indexedService).deleteTopicIndex("1");
        verify(topicMapper).deleteById(1);
    }

    @Test
    void testVote_newVote() {
        mockTopic.setUpIds("");
        when(systemConfigService.selectAllConfig()).thenReturn(configMap);
        when(topicMapper.updateById(any(Topic.class))).thenReturn(1);

        int result = topicService.vote(mockTopic, mockUser);

        assertEquals(1, result);
        assertTrue(mockTopic.getUpIds().contains("1"));
        assertEquals(102, mockUser.getScore()); // 100 + 2 (up_topic_score)
        verify(topicMapper).updateById(mockTopic);
        verify(userService).update(mockUser);
    }

    @Test
    void testVote_cancelVote() {
        mockTopic.setUpIds("1");
        when(systemConfigService.selectAllConfig()).thenReturn(configMap);
        when(topicMapper.updateById(any(Topic.class))).thenReturn(1);

        int result = topicService.vote(mockTopic, mockUser);

        assertEquals(0, result);
        assertFalse(mockTopic.getUpIds().contains("1"));
        assertEquals(98, mockUser.getScore()); // 100 - 2 (up_topic_score)
        verify(topicMapper).updateById(mockTopic);
        verify(userService).update(mockUser);
    }

    @Test
    void testVote_multipleUsers() {
        mockTopic.setUpIds("2,3");
        when(systemConfigService.selectAllConfig()).thenReturn(configMap);
        when(topicMapper.updateById(any(Topic.class))).thenReturn(1);

        int result = topicService.vote(mockTopic, mockUser);

        assertEquals(3, result);
        assertTrue(mockTopic.getUpIds().contains("1"));
        assertEquals(102, mockUser.getScore());
    }

    @Test
    void testSelectAll() {
        when(systemConfigService.selectAllConfig()).thenReturn(configMap);
        MyPage<Map<String, Object>> mockPage = new MyPage<>(1, 10);
        mockPage.setRecords(new ArrayList<>());
        when(topicMapper.selectAll(any(MyPage.class), eq("all"))).thenReturn(mockPage);

        MyPage<Map<String, Object>> result = topicService.selectAll(1, "all");

        assertNotNull(result);
        verify(topicMapper).selectAll(any(MyPage.class), eq("all"));
        verify(tagService).selectTagsByTopicId(result);
    }

    @Test
    void testSearch() {
        MyPage<Map<String, Object>> mockPage = new MyPage<>(1, 10);
        mockPage.setRecords(new ArrayList<>());
        when(topicMapper.search(any(MyPage.class), eq("keyword"))).thenReturn(mockPage);

        MyPage<Map<String, Object>> result = topicService.search(1, 10, "keyword");

        assertNotNull(result);
        verify(topicMapper).search(any(MyPage.class), eq("keyword"));
    }

    @Test
    void testSearch_defaultPageSize() {
        when(systemConfigService.selectAllConfig()).thenReturn(configMap);
        MyPage<Map<String, Object>> mockPage = new MyPage<>(1, 10);
        mockPage.setRecords(new ArrayList<>());
        when(topicMapper.search(any(MyPage.class), eq("test"))).thenReturn(mockPage);

        MyPage<Map<String, Object>> result = topicService.search(1, null, "test");

        assertNotNull(result);
        verify(topicMapper).search(any(MyPage.class), eq("test"));
    }

    @Test
    void testSelectAuthorOtherTopic() {
        List<Topic> topics = Arrays.asList(mockTopic);
        when(topicMapper.selectList(any(QueryWrapper.class))).thenReturn(topics);

        List<Topic> result = topicService.selectAuthorOtherTopic(1, 2, 5);

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(topicMapper).selectList(any(QueryWrapper.class));
    }

    @Test
    void testSelectAuthorOtherTopic_withoutTopicIdAndLimit() {
        List<Topic> topics = Arrays.asList(mockTopic);
        when(topicMapper.selectList(any(QueryWrapper.class))).thenReturn(topics);

        List<Topic> result = topicService.selectAuthorOtherTopic(1, null, null);

        assertNotNull(result);
        verify(topicMapper).selectList(any(QueryWrapper.class));
    }

    @Test
    void testCountToday() {
        when(topicMapper.countToday()).thenReturn(5);

        int result = topicService.countToday();

        assertEquals(5, result);
        verify(topicMapper).countToday();
    }

    @Test
    void testSelectAllForAdmin() {
        when(systemConfigService.selectAllConfig()).thenReturn(configMap);
        MyPage<Map<String, Object>> mockPage = new MyPage<>(1, 10);
        mockPage.setRecords(new ArrayList<>());
        when(topicMapper.selectAllForAdmin(any(MyPage.class), eq("2024-01-01"), eq("2024-12-31"), eq("admin")))
                .thenReturn(mockPage);

        MyPage<Map<String, Object>> result = topicService.selectAllForAdmin(1, "2024-01-01", "2024-12-31", "admin");

        assertNotNull(result);
        verify(topicMapper).selectAllForAdmin(any(MyPage.class), eq("2024-01-01"), eq("2024-12-31"), eq("admin"));
    }

    @Test
    void testDeleteByUserId() {
        List<Topic> topics = Arrays.asList(mockTopic);
        when(topicMapper.selectList(any(QueryWrapper.class))).thenReturn(topics);
        when(topicMapper.delete(any(QueryWrapper.class))).thenReturn(1);

        topicService.deleteByUserId(1);

        verify(indexedService).deleteTopicIndex("1");
        verify(commentService).deleteByTopicId(1);
        verify(collectService).deleteByTopicId(1);
        verify(tagService).reduceTopicCount(1);
        verify(topicTagService).deleteByTopicId(1);
        verify(topicMapper).delete(any(QueryWrapper.class));
    }

    @Test
    void testSelectByUserId() {
        MyPage<Map<String, Object>> mockPage = new MyPage<>(1, 10);
        Map<String, Object> record = new HashMap<>();
        record.put("content", "test content");
        mockPage.setRecords(Arrays.asList(record));
        when(topicMapper.selectByUserId(any(MyPage.class), eq(1))).thenReturn(mockPage);

        MyPage<Map<String, Object>> result = topicService.selectByUserId(1, 1, 10);

        assertNotNull(result);
        verify(topicMapper).selectByUserId(any(MyPage.class), eq(1));
    }

    @Test
    void testSelectByUserId_defaultPageSize() {
        when(systemConfigService.selectAllConfig()).thenReturn(configMap);
        MyPage<Map<String, Object>> mockPage = new MyPage<>(1, 10);
        mockPage.setRecords(new ArrayList<>());
        when(topicMapper.selectByUserId(any(MyPage.class), eq(1))).thenReturn(mockPage);

        MyPage<Map<String, Object>> result = topicService.selectByUserId(1, 1, null);

        assertNotNull(result);
    }
}
