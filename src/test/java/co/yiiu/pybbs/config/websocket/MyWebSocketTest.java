package co.yiiu.pybbs.config.websocket;

import co.yiiu.pybbs.model.vo.UserWithWebSocketVO;
import co.yiiu.pybbs.util.Message;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.websocket.EncodeException;
import javax.websocket.RemoteEndpoint;
import javax.websocket.Session;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MyWebSocketTest {

    @Mock
    private Session session;
    @Mock
    private RemoteEndpoint.Basic basicRemote;

    private MyWebSocket webSocket;

    @BeforeEach
    void setUp() {
        webSocket = new MyWebSocket();
        // 清理之前的状态
        MyWebSocket.webSockets.clear();
    }

    @AfterEach
    void tearDown() {
        MyWebSocket.webSockets.clear();
    }

    @Test
    void testOnOpen() {
        webSocket.onOpen(session);

        assertTrue(MyWebSocket.webSockets.containsKey(session));
        assertNotNull(MyWebSocket.webSockets.get(session));
    }

    @Test
    void testOnClose() {
        // 先打开连接
        webSocket.onOpen(session);
        assertTrue(MyWebSocket.webSockets.containsKey(session));

        // 关闭连接
        webSocket.onClose(session);

        assertFalse(MyWebSocket.webSockets.containsKey(session));
    }

    @Test
    void testOnMessage_bind() throws IOException, EncodeException {
        // 先建立连接
        webSocket.onOpen(session);
        when(session.getBasicRemote()).thenReturn(basicRemote);

        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", 1);
        payload.put("username", "testuser");
        Message bindMessage = new Message("bind", payload);

        webSocket.onMessage(bindMessage, session);

        UserWithWebSocketVO vo = MyWebSocket.webSockets.get(session);
        assertNotNull(vo);
        assertEquals(Integer.valueOf(1), vo.getUserId());
        assertEquals("testuser", vo.getUsername());
        verify(basicRemote).sendObject(any(Message.class));
    }

    @Test
    void testOnMessage_nullMessage() {
        // null消息不应该抛出异常
        assertDoesNotThrow(() -> webSocket.onMessage(null, session));
    }

    @Test
    void testOnMessage_unknownType() {
        webSocket.onOpen(session);
        Message unknownMessage = new Message("unknownType", null);

        // 未知类型消息不应抛出异常
        assertDoesNotThrow(() -> webSocket.onMessage(unknownMessage, session));
    }

    @Test
    void testEmit_userOnline() throws IOException, EncodeException {
        // 模拟一个在线用户
        webSocket.onOpen(session);
        UserWithWebSocketVO vo = new UserWithWebSocketVO("testuser", 1);
        MyWebSocket.webSockets.put(session, vo);
        when(session.getBasicRemote()).thenReturn(basicRemote);

        Message message = new Message("notifications", "You have a new message");
        MyWebSocket.emit(1, message);

        verify(basicRemote).sendObject(message);
    }

    @Test
    void testEmit_userOffline() {
        // 用户不在线，不应该抛出异常
        Message message = new Message("notifications", "You have a new message");

        assertDoesNotThrow(() -> MyWebSocket.emit(999, message));
    }

    @Test
    void testEmit_multipleSessions() throws IOException, EncodeException {
        // 模拟多个session，只有目标用户收到消息
        Session session2 = mock(Session.class);
        RemoteEndpoint.Basic basicRemote2 = mock(RemoteEndpoint.Basic.class);

        webSocket.onOpen(session);
        webSocket.onOpen(session2);

        UserWithWebSocketVO vo1 = new UserWithWebSocketVO("user1", 1);
        UserWithWebSocketVO vo2 = new UserWithWebSocketVO("user2", 2);
        MyWebSocket.webSockets.put(session, vo1);
        MyWebSocket.webSockets.put(session2, vo2);

        when(session.getBasicRemote()).thenReturn(basicRemote);

        Message message = new Message("notifications", "Hello user1");
        MyWebSocket.emit(1, message);

        verify(basicRemote).sendObject(message);
    }

    @Test
    void testMultipleOpenClose() {
        Session session2 = mock(Session.class);

        webSocket.onOpen(session);
        webSocket.onOpen(session2);
        assertEquals(2, MyWebSocket.webSockets.size());

        webSocket.onClose(session);
        assertEquals(1, MyWebSocket.webSockets.size());
        assertFalse(MyWebSocket.webSockets.containsKey(session));
        assertTrue(MyWebSocket.webSockets.containsKey(session2));
    }

    @Test
    void testOnMessage_bind_ioException() throws IOException, EncodeException {
        webSocket.onOpen(session);
        when(session.getBasicRemote()).thenReturn(basicRemote);
        doThrow(new IOException("Connection closed")).when(basicRemote).sendObject(any(Message.class));

        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", 1);
        payload.put("username", "testuser");
        Message bindMessage = new Message("bind", payload);

        // IOException应该被捕获而不是抛出
        assertDoesNotThrow(() -> webSocket.onMessage(bindMessage, session));
    }
}
