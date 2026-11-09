package com.ai.studenthelper.infra.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * API Key 加密存储：AES-256-GCM，每条配置随机 12 字节 IV，库里存 Base64(IV || ciphertext)。
 */
public final class ApiKeyCipher {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private static final int KEY_LENGTH = 32;

    private final SecretKeySpec keySpec;
    private final SecureRandom random = new SecureRandom();

    public ApiKeyCipher(byte[] masterKey) {
        if (masterKey == null || masterKey.length != KEY_LENGTH) {
            throw new IllegalArgumentException("主密钥必须是 32 字节");
        }
        this.keySpec = new SecretKeySpec(Arrays.copyOf(masterKey, KEY_LENGTH), "AES");
    }

    public String encrypt(String plainText) {
        if (plainText == null) {
            return null;
        }
        byte[] iv = new byte[IV_LENGTH];
        random.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, iv));
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] packed = new byte[IV_LENGTH + cipherText.length];
            System.arraycopy(iv, 0, packed, 0, IV_LENGTH);
            System.arraycopy(cipherText, 0, packed, IV_LENGTH, cipherText.length);
            return Base64.getEncoder().encodeToString(packed);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("API Key 加密失败", e);
        }
    }

    public String decrypt(String packedBase64) {
        if (packedBase64 == null || packedBase64.isBlank()) {
            return null;
        }
        final byte[] packed;
        try {
            packed = Base64.getDecoder().decode(packedBase64);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("API Key 密文不是合法 Base64", e);
        }
        if (packed.length <= IV_LENGTH) {
            throw new IllegalStateException("API Key 密文长度异常");
        }
        byte[] iv = Arrays.copyOfRange(packed, 0, IV_LENGTH);
        byte[] cipherText = Arrays.copyOfRange(packed, IV_LENGTH, packed.length);
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("API Key 解密失败：密文被篡改或主密钥不匹配", e);
        }
    }
}
