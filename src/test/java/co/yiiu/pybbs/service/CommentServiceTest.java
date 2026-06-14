package co.yiiu.pybbs.service;

import co.yiiu.pybbs.config.service.EmailService;
import co.yiiu.pybbs.config.service.TelegramBotService;
import co.yiiu.pybbs.mapper.CommentMapper;
import co.yiiu.pybbs.model.Comment;
import co.yiiu.pybbs.model.Topic;
import co.yiiu.pybbs.model.User;
import co.yiiu.pybbs.model.vo.CommentsByTopic;
import co.yiiu.pybbs.service.impl.CommentService;
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
class CommentServiceTest {

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

    private Map<String, String> configMap;
    private Comment mockComment;
    private Topic mockTopic;
    private User mockUser;

    @BeforeEach
    void setUp() {
        SensitiveWordUtil.init(new HashSet<>());

        configMap = new HashMap<>();
        configMap.put("page_size", "10");
        configMap.put("comment_need_examine", "0");
        configMap.put("create_comment_score", "3");
        configMap.put("delete_comment_score", "2");
        configMap.put("up_comment_score", "1");
        configMap.put("websocket", "0");
        configMap.put("base_url", "http://localhost:8080");
        configMap.put("content_style", "MD");

        mockComment = new Comment();
        mockComment.setId(1);
        mockComment.setTopicId(1);
        mockComment.setUserId(2);
        mockComment.setContent("Test comment content");
        mockComment.setUpIds("");
        mockComment.setStatus(true);

        mockTopic = new Topic();
        mockTopic.setId(1);
        mockTopic.setTitle("Test Topic");
        mockTopic.setUserId(1);
        mockTopic.setCommentCount(5);

        mockUser = new User();
        mockUser.setId(2);
        mockUser.setUsername("commenter");
        mockUser.setScore(50);
        mockUser.setEmail("test@test.com");
        mockUser.setEmailNotification(false);
    }

    @Test
    void testSelectByTopicId() {
        CommentsByTopic cbt = new CommentsByTopic();
        cbt.setId(1);
        cbt.setContent("Normal comment");
        cbt.setUsername("user1");
        List<CommentsByTopic> comments = Arrays.asList(cbt);
        when(commentMapper.selectByTopicId(1)).thenReturn(comments);

        List<CommentsByTopic> result = commentService.selectByTopicId(1);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Normal comment", result.get(0).getContent());
        verify(commentMapper).selectByTopicId(1);
    }

    @Test
    void testSelectByTopicId_emptyList() {
        when(commentMapper.selectByTopicId(999)).thenReturn(new ArrayList<>());

        List<CommentsByTopic> result = commentService.selectByTopicId(999);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testSelectById() {
        when(commentMapper.selectById(1)).thenReturn(mockComment);

        Comment result = commentService.selectById(1);

        assertNotNull(result);
        assertEquals(1, result.getId());
        assertEquals("Test comment content", result.getContent());
        verify(commentMapper).selectById(1);
    }

    @Test
    void testSelectById_notFound() {
        when(commentMapper.selectById(999)).thenReturn(null);

        Comment result = commentService.selectById(999);

        assertNull(result);
    }

    @Test
    void testSelectByTgMessageId_found() {
        List<Comment> comments = Arrays.asList(mockComment);
        when(commentMapper.selectList(any(QueryWrapper.class))).thenReturn(comments);

        Comment result = commentService.selectByTgMessageId(12345);

        assertNotNull(result);
        assertEquals(1, result.getId());
    }

    @Test
    void testSelectByTgMessageId_notFound() {
        when(commentMapper.selectList(any(QueryWrapper.class))).thenReturn(new ArrayList<>());

        Comment result = commentService.selectByTgMessageId(99999);

        assertNull(result);
    }

    @Test
    void testInsert_withoutExamine() {
        configMap.put("comment_need_examine", "0");
        when(systemConfigService.selectAllConfig()).thenReturn(configMap);
        when(commentMapper.insert(any(Comment.class))).thenReturn(1);
        User topicAuthor = new User();
        topicAuthor.setId(1);
        topicAuthor.setEmail(null);
        when(userService.selectById(anyInt())).thenReturn(topicAuthor);

        Comment result = commentService.insert(mockComment, mockTopic, mockUser);

        assertNotNull(result);
        assertTrue(result.getStatus()); // 不需要审核，status为true
        verify(commentMapper).insert(mockComment);
        assertEquals(6, mockTopic.getCommentCount()); // 5 + 1
        verify(topicService).update(mockTopic, null);
        assertEquals(53, mockUser.getScore()); // 50 + 3 (create_comment_score)
        verify(userService).update(mockUser);
    }

    @Test
    void testInsert_withExamine() {
        configMap.put("comment_need_examine", "1");
        when(systemConfigService.selectAllConfig()).thenReturn(configMap);
        when(commentMapper.insert(any(Comment.class))).thenReturn(1);
        User topicAuthor = new User();
        topicAuthor.setId(1);
        topicAuthor.setEmail(null);
        when(userService.selectById(anyInt())).thenReturn(topicAuthor);

        Comment newComment = new Comment();
        newComment.setTopicId(1);
        newComment.setUserId(2);
        newComment.setContent("New comment under examine");

        Comment result = commentService.insert(newComment, mockTopic, mockUser);

        assertNotNull(result);
        assertFalse(result.getStatus()); // 需要审核，status为false
    }

    @Test
    void testUpdate() {
        when(commentMapper.updateById(any(Comment.class))).thenReturn(1);

        mockComment.setContent("Updated content");
        commentService.update(mockComment);

        verify(commentMapper).updateById(mockComment);
    }

    @Test
    void testVote_newVote() {
        mockComment.setUpIds("");
        when(systemConfigService.selectAllConfig()).thenReturn(configMap);
        when(commentMapper.updateById(any(Comment.class))).thenReturn(1);

        int result = commentService.vote(mockComment, mockUser);

        assertEquals(1, result);
        assertTrue(mockComment.getUpIds().contains("2"));
        assertEquals(51, mockUser.getScore()); // 50 + 1 (up_comment_score)
        verify(commentMapper).updateById(mockComment);
        verify(userService).update(mockUser);
    }

    @Test
    void testVote_cancelVote() {
        mockComment.setUpIds("2");
        when(systemConfigService.selectAllConfig()).thenReturn(configMap);
        when(commentMapper.updateById(any(Comment.class))).thenReturn(1);

        int result = commentService.vote(mockComment, mockUser);

        assertEquals(0, result);
        assertFalse(mockComment.getUpIds().contains("2"));
        assertEquals(49, mockUser.getScore()); // 50 - 1 (up_comment_score)
    }

    @Test
    void testDelete() {
        when(systemConfigService.selectAllConfig()).thenReturn(configMap);
        when(topicService.selectById(1)).thenReturn(mockTopic);
        User commentAuthor = new User();
        commentAuthor.setId(3);
        commentAuthor.setScore(30);
        when(userService.selectById(2)).thenReturn(commentAuthor);

        commentService.delete(mockComment);

        assertEquals(4, mockTopic.getCommentCount()); // 5 - 1
        verify(topicService).update(mockTopic, null);
        assertEquals(28, commentAuthor.getScore()); // 30 - 2 (delete_comment_score)
        verify(userService).update(commentAuthor);
        verify(commentMapper).deleteById(1);
    }

    @Test
    void testDelete_nullComment() {
        commentService.delete(null);

        verify(commentMapper, never()).deleteById(anyInt());
    }

    @Test
    void testDeleteByTopicId() {
        when(commentMapper.delete(any(QueryWrapper.class))).thenReturn(3);

        commentService.deleteByTopicId(1);

        verify(commentMapper).delete(any(QueryWrapper.class));
    }

    @Test
    void testDeleteByUserId() {
        when(commentMapper.delete(any(QueryWrapper.class))).thenReturn(2);

        commentService.deleteByUserId(1);

        verify(commentMapper).delete(any(QueryWrapper.class));
    }

    @Test
    void testCountToday() {
        when(commentMapper.countToday()).thenReturn(10);

        int result = commentService.countToday();

        assertEquals(10, result);
        verify(commentMapper).countToday();
    }

    @Test
    void testSelectAllForAdmin() {
        when(systemConfigService.selectAllConfig()).thenReturn(configMap);
        MyPage<Map<String, Object>> mockPage = new MyPage<>(1, 10);
        mockPage.setRecords(new ArrayList<>());
        when(commentMapper.selectAllForAdmin(any(MyPage.class), eq("2024-01-01"), eq("2024-12-31"), eq("admin")))
                .thenReturn(mockPage);

        MyPage<Map<String, Object>> result = commentService.selectAllForAdmin(1, "2024-01-01", "2024-12-31", "admin");

        assertNotNull(result);
        verify(commentMapper).selectAllForAdmin(any(MyPage.class), eq("2024-01-01"), eq("2024-12-31"), eq("admin"));
    }

    @Test
    void testSelectByUserId() {
        MyPage<Map<String, Object>> mockPage = new MyPage<>(1, 10);
        Map<String, Object> record = new HashMap<>();
        record.put("content", "user comment");
        mockPage.setRecords(Arrays.asList(record));
        when(commentMapper.selectByUserId(any(MyPage.class), eq(1))).thenReturn(mockPage);

        MyPage<Map<String, Object>> result = commentService.selectByUserId(1, 1, 10);

        assertNotNull(result);
        verify(commentMapper).selectByUserId(any(MyPage.class), eq(1));
    }

    @Test
    void testSelectByUserId_defaultPageSize() {
        when(systemConfigService.selectAllConfig()).thenReturn(configMap);
        MyPage<Map<String, Object>> mockPage = new MyPage<>(1, 10);
        mockPage.setRecords(new ArrayList<>());
        when(commentMapper.selectByUserId(any(MyPage.class), eq(1))).thenReturn(mockPage);

        MyPage<Map<String, Object>> result = commentService.selectByUserId(1, 1, null);

        assertNotNull(result);
    }
}
