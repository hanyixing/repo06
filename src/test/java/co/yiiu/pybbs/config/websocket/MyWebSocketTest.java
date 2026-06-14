package co.yiiu.pybbs.config.websocket;

import co.yiiu.pybbs.model.vo.UserWithWebSocketVO;
import co.yiiu.pybbs.util.Message;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.websocket.RemoteEndpoint;
import javax.websocket.Session;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MyWebSocket 单元测试。
 * MyWebSocket 使用静态集合维护连接，这里在每个用例前后清空静态集合以保证用例相互隔离。
 * 不测试 notReadCount 分支：它依赖 SpringContextUtil.getBean(...)（需要 Spring 容器），属于集成测试范畴。
 */
public class MyWebSocketTest {

    private MyWebSocket myWebSocket;

    @BeforeEach
    public void setUp() {
        myWebSocket = new MyWebSocket();
        MyWebSocket.webSockets.clear();
    }

    @AfterEach
    public void tearDown() {
        MyWebSocket.webSockets.clear();
    }

    @Test
    @DisplayName("建立连接: session 被加入在线集合")
    public void testOnOpen() {
        Session session = mock(Session.class);

        myWebSocket.onOpen(session);

        assertTrue(MyWebSocket.webSockets.containsKey(session));
        assertEquals(1, MyWebSocket.webSockets.size());
    }

    @Test
    @DisplayName("关闭连接: session 从在线集合移除")
    public void testOnClose() {
        Session session = mock(Session.class);
        myWebSocket.onOpen(session);

        myWebSocket.onClose(session);

        assertFalse(MyWebSocket.webSockets.containsKey(session));
        assertEquals(0, MyWebSocket.webSockets.size());
    }

    @Test
    @DisplayName("收到 bind 消息: 绑定用户信息并回发确认消息")
    public void testOnMessageBind() throws Exception {
        Session session = mock(Session.class);
        RemoteEndpoint.Basic basic = mock(RemoteEndpoint.Basic.class);
        when(session.getBasicRemote()).thenReturn(basic);
        myWebSocket.onOpen(session);

        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", 42);
        payload.put("username", "neo");
        Message message = new Message("bind", payload);

        myWebSocket.onMessage(message, session);

        UserWithWebSocketVO vo = MyWebSocket.webSockets.get(session);
        assertEquals(42, vo.getUserId().intValue());
        assertEquals("neo", vo.getUsername());
        // 绑定成功后回发一条 bind 消息
        verify(basic).sendObject(any(Message.class));
    }

    @Test
    @DisplayName("收到 null 消息: 安全忽略，不抛异常")
    public void testOnMessageNull() {
        Session session = mock(Session.class);
        assertDoesNotThrow(() -> myWebSocket.onMessage(null, session));
        verify(session, never()).getBasicRemote();
    }

    @Test
    @DisplayName("收到未知类型消息: 走 default 分支，不处理")
    public void testOnMessageUnknownType() {
        Session session = mock(Session.class);
        Message message = new Message("unknown-type", null);

        assertDoesNotThrow(() -> myWebSocket.onMessage(message, session));
        verify(session, never()).getBasicRemote();
    }

    @Test
    @DisplayName("emit: 向已绑定用户的 session 推送消息")
    public void testEmitToExistingUser() throws Exception {
        Session session = mock(Session.class);
        RemoteEndpoint.Basic basic = mock(RemoteEndpoint.Basic.class);
        when(session.getBasicRemote()).thenReturn(basic);
        // 直接放入一个已绑定 userId 的 VO
        MyWebSocket.webSockets.put(session, new UserWithWebSocketVO("neo", 42));

        MyWebSocket.emit(42, new Message("notifications", "hi"));

        verify(basic).sendObject(any(Message.class));
    }

    @Test
    @DisplayName("emit: 目标用户不在线时不推送")
    public void testEmitToNonExistingUser() {
        Session session = mock(Session.class);
        MyWebSocket.webSockets.put(session, new UserWithWebSocketVO("neo", 42));

        MyWebSocket.emit(999, new Message("notifications", "hi"));

        // 没有匹配的 session，不会去获取远端通道
        verify(session, never()).getBasicRemote();
    }
}
