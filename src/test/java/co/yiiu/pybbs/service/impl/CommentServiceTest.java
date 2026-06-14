package co.yiiu.pybbs.service.impl;

import co.yiiu.pybbs.config.service.EmailService;
import co.yiiu.pybbs.config.service.TelegramBotService;
import co.yiiu.pybbs.mapper.CommentMapper;
import co.yiiu.pybbs.model.Comment;
import co.yiiu.pybbs.model.Topic;
import co.yiiu.pybbs.model.User;
import co.yiiu.pybbs.model.vo.CommentsByTopic;
import co.yiiu.pybbs.service.INotificationService;
import co.yiiu.pybbs.service.ISystemConfigService;
import co.yiiu.pybbs.service.ITopicService;
import co.yiiu.pybbs.service.IUserService;
import co.yiiu.pybbs.util.SensitiveWordUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CommentService 单元测试。
 * 使用纯 Mockito（不加载 Spring 上下文）。
 * 说明：insert() 末尾会异步启动一个发送 TG 通知的线程，这里对 TelegramBotService 使用 lenient 桩，
 * 既避免异步线程 NPE，也避免严格桩模式把可能未及时执行的桩判定为多余。
 */
@ExtendWith(MockitoExtension.class)
public class CommentServiceTest {

    @Mock
    private CommentMapper commentMapper;
    @Mock
    private ITopicService topicService;
    @Mock
    private ISystemConfigService systemConfigService;
    @Mock
    private IUserService userService;
    @Mock
    private INotificationService notificationService;
    @Mock
    private EmailService emailService;
    @Mock
    private TelegramBotService telegramBotService;

    @InjectMocks
    private CommentService commentService;

    @BeforeEach
    public void initSensitiveWords() {
        // 初始化敏感词库，避免 SensitiveWordUtil 内部静态 Map 为 null 导致 NPE
        SensitiveWordUtil.init(new HashSet<>(Collections.singletonList("敏感词")));
    }

    // 让异步 TG 线程的调用都为安全无害的 lenient 桩
    private void stubTelegramBotLenient() {
        lenient().when(telegramBotService.init()).thenReturn(telegramBotService);
        lenient().when(telegramBotService.sendMessage(anyString(), anyBoolean(), any())).thenReturn(null);
    }

    @Test
    @DisplayName("发布评论(无需审核): status=true，话题评论数+1，积分增加")
    public void testInsertNoExamine() {
        stubTelegramBotLenient();
        Map<String, String> config = new HashMap<>();
        config.put("comment_need_examine", "0");
        config.put("create_comment_score", "2");
        config.put("content_style", "RICH");
        config.put("base_url", "http://x");
        when(systemConfigService.selectAllConfig()).thenReturn(config);

        Comment comment = new Comment();
        comment.setContent("nice comment");
        comment.setCommentId(null);

        Topic topic = new Topic();
        topic.setId(1);
        topic.setUserId(5);
        topic.setTitle("a topic");
        topic.setCommentCount(3);

        User user = new User();
        user.setId(5); // 自己评论自己的话题，跳过所有通知分支
        user.setUsername("bob");
        user.setScore(10);

        Comment result = commentService.insert(comment, topic, user);

        assertTrue(result.getStatus()); // 无需审核
        verify(commentMapper).insert(comment);
        assertEquals(4, topic.getCommentCount().intValue());
        verify(topicService).update(topic, null);
        assertEquals(12, user.getScore().intValue());
        verify(userService).update(user);
        // 自评不发任何通知
        verify(notificationService, never()).insert(any(), any(), any(), anyString(), anyString());
    }

    @Test
    @DisplayName("发布评论(需要审核): status=false 进入审核中状态")
    public void testInsertNeedExamine() {
        stubTelegramBotLenient();
        Map<String, String> config = new HashMap<>();
        config.put("comment_need_examine", "1"); // 开启审核
        config.put("create_comment_score", "2");
        config.put("content_style", "RICH");
        config.put("base_url", "http://x");
        when(systemConfigService.selectAllConfig()).thenReturn(config);

        Comment comment = new Comment();
        comment.setContent("need examine");
        comment.setCommentId(null);

        Topic topic = new Topic();
        topic.setId(1);
        topic.setUserId(5);
        topic.setTitle("t");
        topic.setCommentCount(0);

        User user = new User();
        user.setId(5);
        user.setUsername("bob");
        user.setScore(0);

        Comment result = commentService.insert(comment, topic, user);

        assertFalse(result.getStatus()); // 审核中
        verify(commentMapper).insert(comment);
    }

    @Test
    @DisplayName("发布评论(评论他人话题): 给话题作者发送 COMMENT 通知")
    public void testInsertNotifyTopicAuthor() {
        stubTelegramBotLenient();
        Map<String, String> config = new HashMap<>();
        config.put("comment_need_examine", "0");
        config.put("create_comment_score", "1");
        config.put("websocket", "0"); // 关闭 websocket，避免静态调用
        config.put("content_style", "RICH");
        config.put("base_url", "http://x");
        when(systemConfigService.selectAllConfig()).thenReturn(config);

        Comment comment = new Comment();
        comment.setContent("hi there");
        comment.setCommentId(null);

        Topic topic = new Topic();
        topic.setId(1);
        topic.setUserId(5);
        topic.setTitle("t");
        topic.setCommentCount(0);

        User user = new User();
        user.setId(9); // 评论者不是话题作者
        user.setUsername("alice");
        user.setScore(0);

        User topicAuthor = new User();
        topicAuthor.setId(5);
        topicAuthor.setEmail(null); // 无邮箱，跳过邮件分支
        when(userService.selectById(5)).thenReturn(topicAuthor);

        commentService.insert(comment, topic, user);

        verify(notificationService).insert(9, 5, 1, "COMMENT", "hi there");
    }

    @Test
    @DisplayName("评论点赞: 新增点赞，积分增加")
    public void testVoteAdd() {
        Map<String, String> config = new HashMap<>();
        config.put("up_comment_score", "3");
        when(systemConfigService.selectAllConfig()).thenReturn(config);

        Comment comment = new Comment();
        comment.setUpIds(null);
        User user = new User();
        user.setId(4);
        user.setScore(20);

        int count = commentService.vote(comment, user);

        assertEquals(1, count);
        assertTrue(comment.getUpIds().contains("4"));
        assertEquals(23, user.getScore().intValue());
        verify(commentMapper).updateById(comment);
        verify(userService).update(user);
    }

    @Test
    @DisplayName("评论点赞: 再次点赞视为取消，积分减少")
    public void testVoteCancel() {
        Map<String, String> config = new HashMap<>();
        config.put("up_comment_score", "3");
        when(systemConfigService.selectAllConfig()).thenReturn(config);

        Comment comment = new Comment();
        comment.setUpIds("4");
        User user = new User();
        user.setId(4);
        user.setScore(20);

        int count = commentService.vote(comment, user);

        assertEquals(0, count);
        assertEquals("", comment.getUpIds());
        assertEquals(17, user.getScore().intValue());
    }

    @Test
    @DisplayName("删除评论: 话题评论数-1，扣积分，删除记录")
    public void testDelete() {
        Map<String, String> config = new HashMap<>();
        config.put("delete_comment_score", "2");
        when(systemConfigService.selectAllConfig()).thenReturn(config);

        Comment comment = new Comment();
        comment.setId(11);
        comment.setTopicId(1);
        comment.setUserId(3);

        Topic topic = new Topic();
        topic.setId(1);
        topic.setCommentCount(5);
        when(topicService.selectById(1)).thenReturn(topic);

        User user = new User();
        user.setId(3);
        user.setScore(10);
        when(userService.selectById(3)).thenReturn(user);

        commentService.delete(comment);

        assertEquals(4, topic.getCommentCount().intValue());
        verify(topicService).update(topic, null);
        assertEquals(8, user.getScore().intValue());
        verify(userService).update(user);
        verify(commentMapper).deleteById(11);
    }

    @Test
    @DisplayName("删除评论: 传入 null 时什么都不做")
    public void testDeleteNull() {
        commentService.delete(null);
        verify(commentMapper, never()).deleteById(any());
        verify(topicService, never()).update(any(), any());
    }

    @Test
    @DisplayName("根据TG消息id查询评论: 命中返回第一条")
    public void testSelectByTgMessageIdFound() {
        Comment comment = new Comment();
        when(commentMapper.selectList(any())).thenReturn(Collections.singletonList(comment));
        assertSame(comment, commentService.selectByTgMessageId(100));
    }

    @Test
    @DisplayName("根据TG消息id查询评论: 未命中返回 null")
    public void testSelectByTgMessageIdNotFound() {
        when(commentMapper.selectList(any())).thenReturn(Collections.emptyList());
        assertNull(commentService.selectByTgMessageId(100));
    }

    @Test
    @DisplayName("根据id查询评论: 委托给 mapper")
    public void testSelectById() {
        Comment comment = new Comment();
        when(commentMapper.selectById(1)).thenReturn(comment);
        assertSame(comment, commentService.selectById(1));
    }

    @Test
    @DisplayName("更新评论: 委托给 mapper")
    public void testUpdate() {
        Comment comment = new Comment();
        commentService.update(comment);
        verify(commentMapper).updateById(comment);
    }

    @Test
    @DisplayName("统计今日新增评论数")
    public void testCountToday() {
        when(commentMapper.countToday()).thenReturn(7);
        assertEquals(7, commentService.countToday());
    }

    @Test
    @DisplayName("根据话题id查询评论: 对内容做敏感词过滤")
    public void testSelectByTopicId() {
        CommentsByTopic c = new CommentsByTopic();
        c.setContent("clean text");
        when(commentMapper.selectByTopicId(1)).thenReturn(Collections.singletonList(c));

        List<CommentsByTopic> result = commentService.selectByTopicId(1);

        assertEquals(1, result.size());
        // 没有敏感词，内容保持不变
        assertEquals("clean text", result.get(0).getContent());
    }
}
