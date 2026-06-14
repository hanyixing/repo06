package co.yiiu.pybbs.config.websocket;

import co.yiiu.pybbs.util.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MessageDecoderTest {

    private MessageDecoder decoder;

    @BeforeEach
    void setUp() {
        decoder = new MessageDecoder();
    }

    @Test
    void testDecode_validJson() {
        String json = "{\"type\":\"bind\",\"payload\":{\"userId\":1,\"username\":\"testuser\"}}";

        Message message = decoder.decode(json);

        assertNotNull(message);
        assertEquals("bind", message.getType());
        assertNotNull(message.getPayload());
    }

    @Test
    void testDecode_simplePayload() {
        String json = "{\"type\":\"notification\",\"payload\":\"hello\"}";

        Message message = decoder.decode(json);

        assertNotNull(message);
        assertEquals("notification", message.getType());
        assertEquals("hello", message.getPayload());
    }

    @Test
    void testDecode_numericPayload() {
        String json = "{\"type\":\"notification_notread\",\"payload\":5}";

        Message message = decoder.decode(json);

        assertNotNull(message);
        assertEquals("notification_notread", message.getType());
        assertEquals(5, message.getPayload());
    }

    @Test
    void testDecode_nullPayload() {
        String json = "{\"type\":\"test\",\"payload\":null}";

        Message message = decoder.decode(json);

        assertNotNull(message);
        assertEquals("test", message.getType());
        assertNull(message.getPayload());
    }

    @Test
    void testWillDecode_validJson() {
        assertTrue(decoder.willDecode("{\"type\":\"bind\",\"payload\":null}"));
    }

    @Test
    void testWillDecode_validJsonObject() {
        assertTrue(decoder.willDecode("{\"key\":\"value\"}"));
    }

    @Test
    void testWillDecode_invalidJson() {
        assertFalse(decoder.willDecode("not a json string"));
    }

    @Test
    void testWillDecode_emptyString() {
        // Jackson's readTree treats empty string as valid (MissingNode)
        assertTrue(decoder.willDecode(""));
    }

    @Test
    void testWillDecode_malformedJson() {
        assertFalse(decoder.willDecode("{broken json"));
    }

    @Test
    void testInit_and_destroy() {
        // 验证init和destroy方法不抛异常
        assertDoesNotThrow(() -> decoder.init(null));
        assertDoesNotThrow(() -> decoder.destroy());
    }
}
