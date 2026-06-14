package co.yiiu.pybbs.config.websocket;

import co.yiiu.pybbs.util.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MessageEncoderTest {

    private MessageEncoder encoder;

    @BeforeEach
    void setUp() {
        encoder = new MessageEncoder();
    }

    @Test
    void testEncode_messageWithPayload() {
        Message message = new Message("bind", "testPayload");

        String json = encoder.encode(message);

        assertNotNull(json);
        assertTrue(json.contains("\"type\""));
        assertTrue(json.contains("\"bind\""));
        assertTrue(json.contains("\"payload\""));
        assertTrue(json.contains("\"testPayload\""));
    }

    @Test
    void testEncode_messageWithNullPayload() {
        Message message = new Message("notification", null);

        String json = encoder.encode(message);

        assertNotNull(json);
        assertTrue(json.contains("\"type\""));
        assertTrue(json.contains("\"notification\""));
    }

    @Test
    void testEncode_messageWithNumericPayload() {
        Message message = new Message("notification_notread", 5);

        String json = encoder.encode(message);

        assertNotNull(json);
        assertTrue(json.contains("\"notification_notread\""));
        assertTrue(json.contains("5"));
    }

    @Test
    void testEncode_emptyMessage() {
        Message message = new Message();

        String json = encoder.encode(message);

        assertNotNull(json);
    }
}
