package co.yiiu.pybbs.service.impl;

import co.yiiu.pybbs.mapper.TopicMapper;
import co.yiiu.pybbs.model.Tag;
import co.yiiu.pybbs.model.Topic;
import co.yiiu.pybbs.model.User;
import co.yiiu.pybbs.service.ICollectService;
import co.yiiu.pybbs.service.ICommentService;
import co.yiiu.pybbs.service.INotificationService;
import co.yiiu.pybbs.service.ISystemConfigService;
import co.yiiu.pybbs.service.ITagService;
import co.yiiu.pybbs.service.ITopicTagService;
import co.yiiu.pybbs.service.IUserService;
import co.yiiu.pybbs.util.MyPage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TopicService 单元测试。
 * 使用纯 Mockito（不加载 Spring 上下文），不依赖数据库 / ES / Redis。
 */
@ExtendWith(MockitoExtension.class)
public class TopicServiceTest {

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

    private Map<String, String> config(String key, String value) {
        Map<String, String> map = new HashMap<>();
        map.put(key, value);
        return map;
    }

    @Test
    @DisplayName("发布话题(带标签): 默认字段正确、积分增加、标签与索引被处理")
    public void testInsertWithTags() {
        Map<String, String> config = new HashMap<>();
        config.put("content_style", "MD");
        config.put("create_topic_score", "10");
        when(systemConfigService.selectAllConfig()).thenReturn(config);

        List<Tag> tagList = Collections.singletonList(new Tag());
        when(tagService.insertTag("java,test")).thenReturn(tagList);

        User user = new User();
        user.setId(1);
        user.setScore(100);

        Topic result = topicService.insert("hello title", "hello content", "java,test", user);

        // 校验话题字段
        assertEquals("hello title", result.getTitle());
        assertEquals("MD", result.getStyle());
        assertEquals("hello content", result.getContent());
        assertEquals(1, result.getUserId().intValue());
        assertEquals(1, result.getView().intValue());
        assertEquals(0, result.getCollectCount().intValue());
        assertEquals(0, result.getCommentCount().intValue());
        assertFalse(result.getTop());
        assertFalse(result.getGood());
        assertNotNull(result.getInTime());

        verify(topicMapper).insert(any(Topic.class));
        // 积分增加 100 + 10 = 110
        assertEquals(110, user.getScore().intValue());
        verify(userService).update(user);
        // 标签处理
        verify(tagService).insertTag("java,test");
        verify(topicTagService).insertTopicTag(any(), eq(tagList));
        // 索引
        verify(indexedService).indexTopic(anyString(), eq("hello title"), eq("hello content"));
    }

    @Test
    @DisplayName("发布话题(无标签): 不处理标签关联")
    public void testInsertWithoutTags() {
        Map<String, String> config = new HashMap<>();
        config.put("content_style", "RICH");
        config.put("create_topic_score", "5");
        when(systemConfigService.selectAllConfig()).thenReturn(config);

        User user = new User();
        user.setId(2);
        user.setScore(0);

        Topic result = topicService.insert("t", "c", "", user);

        assertEquals("RICH", result.getStyle());
        assertEquals(5, user.getScore().intValue());
        verify(topicMapper).insert(any(Topic.class));
        verify(tagService, never()).insertTag(anyString());
        verify(topicTagService, never()).insertTopicTag(any(), any());
    }

    @Test
    @DisplayName("话题点赞: 新增点赞，积分增加，返回点赞数")
    public void testVoteAdd() {
        when(systemConfigService.selectAllConfig()).thenReturn(config("up_topic_score", "5"));

        Topic topic = new Topic();
        topic.setUpIds(null);
        User user = new User();
        user.setId(7);
        user.setScore(50);

        int count = topicService.vote(topic, user);

        assertEquals(1, count);
        assertTrue(topic.getUpIds().contains("7"));
        assertEquals(55, user.getScore().intValue());
        verify(topicMapper).updateById(topic);
        verify(userService).update(user);
    }

    @Test
    @DisplayName("话题点赞: 再次点赞视为取消，积分减少")
    public void testVoteCancel() {
        when(systemConfigService.selectAllConfig()).thenReturn(config("up_topic_score", "5"));

        Topic topic = new Topic();
        topic.setUpIds("7");
        User user = new User();
        user.setId(7);
        user.setScore(50);

        int count = topicService.vote(topic, user);

        assertEquals(0, count);
        assertEquals("", topic.getUpIds());
        assertEquals(45, user.getScore().intValue());
    }

    @Test
    @DisplayName("更新访问量: view+1 并更新")
    public void testUpdateViewCount() {
        Topic topic = new Topic();
        topic.setView(5);

        Topic result = topicService.updateViewCount(topic, "1.2.3.4");

        assertEquals(6, result.getView().intValue());
        verify(topicMapper).updateById(topic);
    }

    @Test
    @DisplayName("删除话题: 级联删除关联数据并扣除积分")
    public void testDelete() {
        when(systemConfigService.selectAllConfig()).thenReturn(config("delete_topic_score", "3"));

        Topic topic = new Topic();
        topic.setId(10);
        topic.setUserId(2);

        User user = new User();
        user.setId(2);
        user.setScore(100);
        when(userService.selectById(2)).thenReturn(user);

        topicService.delete(topic);

        verify(notificationService).deleteByTopicId(10);
        verify(collectService).deleteByTopicId(10);
        verify(commentService).deleteByTopicId(10);
        verify(tagService).reduceTopicCount(10);
        verify(topicTagService).deleteByTopicId(10);
        verify(indexedService).deleteTopicIndex("10");
        verify(topicMapper).deleteById(10);
        assertEquals(97, user.getScore().intValue());
        verify(userService).update(user);
    }

    @Test
    @DisplayName("根据id查询话题: 委托给 mapper")
    public void testSelectById() {
        Topic topic = new Topic();
        when(topicMapper.selectById(1)).thenReturn(topic);
        assertSame(topic, topicService.selectById(1));
    }

    @Test
    @DisplayName("根据标题查询话题: 委托给 mapper")
    public void testSelectByTitle() {
        Topic topic = new Topic();
        when(topicMapper.selectOne(any())).thenReturn(topic);
        assertSame(topic, topicService.selectByTitle("title"));
    }

    @Test
    @DisplayName("搜索: pageSize 为空时从配置读取 page_size")
    public void testSearchWithNullPageSize() {
        when(systemConfigService.selectAllConfig()).thenReturn(config("page_size", "20"));
        MyPage<Map<String, Object>> page = new MyPage<>();
        when(topicMapper.search(any(), eq("kw"))).thenReturn(page);

        assertSame(page, topicService.search(1, null, "kw"));
    }

    @Test
    @DisplayName("分页查询话题: 查询后填充标签")
    public void testSelectAll() {
        when(systemConfigService.selectAllConfig()).thenReturn(config("page_size", "20"));
        MyPage<Map<String, Object>> page = new MyPage<>();
        when(topicMapper.selectAll(any(), eq("tab"))).thenReturn(page);

        MyPage<Map<String, Object>> result = topicService.selectAll(1, "tab");

        assertSame(page, result);
        verify(tagService).selectTagsByTopicId(page);
    }

    @Test
    @DisplayName("查询作者其它话题: 委托给 mapper")
    public void testSelectAuthorOtherTopic() {
        List<Topic> list = Arrays.asList(new Topic(), new Topic());
        when(topicMapper.selectList(any())).thenReturn(list);
        assertSame(list, topicService.selectAuthorOtherTopic(1, 2, 5));
    }

    @Test
    @DisplayName("统计今日新增话题数")
    public void testCountToday() {
        when(topicMapper.countToday()).thenReturn(3);
        assertEquals(3, topicService.countToday());
    }

    @Test
    @DisplayName("发布话题: 写入 mapper 的对象包含传入的标题与内容")
    public void testInsertPersistsTopic() {
        Map<String, String> config = new HashMap<>();
        config.put("content_style", "MD");
        config.put("create_topic_score", "1");
        when(systemConfigService.selectAllConfig()).thenReturn(config);

        User user = new User();
        user.setId(1);
        user.setScore(0);

        topicService.insert("captured-title", "captured-content", null, user);

        ArgumentCaptor<Topic> captor = ArgumentCaptor.forClass(Topic.class);
        verify(topicMapper).insert(captor.capture());
        assertEquals("captured-title", captor.getValue().getTitle());
        assertEquals("captured-content", captor.getValue().getContent());
    }
}
