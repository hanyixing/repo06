package co.yiiu.pybbs.config.realm;

import co.yiiu.pybbs.util.bcrypt.BCrypt;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MyCredentialsMatcherTest {

    private MyCredentialsMatcher matcher;

    @BeforeEach
    void setUp() {
        matcher = new MyCredentialsMatcher();
    }

    private AuthenticationToken createToken(String rawPassword) {
        UsernamePasswordToken token = new UsernamePasswordToken();
        token.setUsername("admin");
        token.setPassword(rawPassword.toCharArray());
        return token;
    }

    private AuthenticationInfo createInfo(String rawPassword) {
        String encoded = BCrypt.hashpw(rawPassword, BCrypt.gensalt());
        return new SimpleAuthenticationInfo("admin", encoded, "testRealm");
    }

    @Test
    void testDoCredentialsMatch_correctPassword() {
        String password = "correctPassword";
        AuthenticationToken token = createToken(password);
        AuthenticationInfo info = createInfo(password);

        boolean result = matcher.doCredentialsMatch(token, info);

        assertTrue(result);
    }

    @Test
    void testDoCredentialsMatch_wrongPassword() {
        AuthenticationToken token = createToken("wrongPassword");
        AuthenticationInfo info = createInfo("correctPassword");

        boolean result = matcher.doCredentialsMatch(token, info);

        assertFalse(result);
    }

    @Test
    void testDoCredentialsMatch_emptyPassword() {
        AuthenticationToken token = createToken("");
        AuthenticationInfo info = createInfo("correctPassword");

        boolean result = matcher.doCredentialsMatch(token, info);

        assertFalse(result);
    }

    @Test
    void testDoCredentialsMatch_differentEncodedPasswords() {
        AuthenticationToken token = createToken("correctPassword");
        AuthenticationInfo info = createInfo("anotherPassword");

        boolean result = matcher.doCredentialsMatch(token, info);

        assertFalse(result);
    }

    @Test
    void testDoCredentialsMatch_specialCharacters() {
        String specialPassword = "p@ss_w0rd";
        AuthenticationToken token = createToken(specialPassword);
        AuthenticationInfo info = createInfo(specialPassword);

        boolean result = matcher.doCredentialsMatch(token, info);

        assertTrue(result);
    }

    @Test
    void testDoCredentialsMatch_stringCredentials() {
        String password = "testPassword";
        String encoded = BCrypt.hashpw(password, BCrypt.gensalt());
        AuthenticationToken token = createToken(password);
        // Store credentials as String instead of char[]
        AuthenticationInfo info = new SimpleAuthenticationInfo("admin", encoded, "testRealm");

        boolean result = matcher.doCredentialsMatch(token, info);

        assertTrue(result);
    }

    @Test
    void testBCryptDirectly() {
        // Test BCrypt independently to verify it works
        String password = "test123";
        String salt = BCrypt.gensalt();
        String hash1 = BCrypt.hashpw(password, salt);
        String hash2 = BCrypt.hashpw(password, salt);

        // Same salt should produce same hash
        assertEquals(hash1, hash2);
        // checkpw should verify correctly
        assertTrue(BCrypt.checkpw(password, hash1));
        assertFalse(BCrypt.checkpw("wrong", hash1));
    }
}
