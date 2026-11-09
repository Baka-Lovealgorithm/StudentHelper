package com.ai.studenthelper.infra.crypto;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class ApiKeyCipherTest {

    private static ApiKeyCipher randomCipher(byte[] key) {
        return new ApiKeyCipher(key);
    }

    private static byte[] randomKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return key;
    }

    @Test
    void encryptThenDecryptReturnsOriginal() {
        byte[] key = randomKey();
        ApiKeyCipher cipher = randomCipher(key);
        String plain = "sk-测试-key-1234567890";
        String encrypted = cipher.encrypt(plain);

        assertNotEquals(plain, encrypted);
        assertFalse(encrypted.contains(plain));
        assertEquals(plain, randomCipher(key).decrypt(encrypted));
    }

    @Test
    void samePlainTextProducesDifferentCipherTextBecauseOfRandomIv() {
        ApiKeyCipher cipher = randomCipher(randomKey());
        String plain = "same-api-key";

        String first = cipher.encrypt(plain);
        String second = cipher.encrypt(plain);

        assertNotEquals(first, second, "随机 IV 必须让两次加密结果不同");
        assertEquals(plain, cipher.decrypt(first));
        assertEquals(plain, cipher.decrypt(second));
    }

    @Test
    void tamperedCipherTextFailsLoudly() {
        ApiKeyCipher cipher = randomCipher(randomKey());
        String encrypted = cipher.encrypt("sk-original");
        byte[] packed = Base64.getDecoder().decode(encrypted);
        packed[packed.length - 1] ^= 0x01;
        String tampered = Base64.getEncoder().encodeToString(packed);

        assertThrows(IllegalStateException.class, () -> cipher.decrypt(tampered));
    }

    @Test
    void wrongMasterKeyFailsLoudly() {
        String encrypted = randomCipher(randomKey()).encrypt("sk-original");
        ApiKeyCipher other = randomCipher(randomKey());

        assertThrows(IllegalStateException.class, () -> other.decrypt(encrypted));
    }

    @Test
    void nullAndBlankInputsAreHandled() {
        ApiKeyCipher cipher = randomCipher(randomKey());
        assertNull(cipher.encrypt(null));
        assertNull(cipher.decrypt(null));
        assertNull(cipher.decrypt("   "));
        assertEquals("", cipher.decrypt(cipher.encrypt("")));
    }

    @Test
    void masterKeyMustBe32Bytes() {
        assertThrows(IllegalArgumentException.class, () -> new ApiKeyCipher(new byte[16]));
        assertDoesNotThrow(() -> new ApiKeyCipher("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8)));
    }
}
