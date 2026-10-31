package dev.queiroz.relay.delivery.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/** Encrypts persisted operational secrets; the key is supplied only by the runtime environment. */
@Component
public class SecretProtector {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final byte[] key;

    public SecretProtector(@Value("${relay.secrets.encryption-key}") String encodedKey) {
        try {
            key = Base64.getDecoder().decode(encodedKey);
            if (key.length != 32) throw new IllegalArgumentException();
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("relay.secrets.encryption-key must be a base64-encoded 256-bit key", exception);
        }
    }

    public String encrypt(String value) { return crypt(Cipher.ENCRYPT_MODE, value.getBytes(java.nio.charset.StandardCharsets.UTF_8), null); }
    public String decrypt(String value) {
        byte[] packed = Base64.getDecoder().decode(value);
        return crypt(Cipher.DECRYPT_MODE, Arrays.copyOfRange(packed, 12, packed.length), Arrays.copyOf(packed, 12));
    }
    private String crypt(int mode, byte[] input, byte[] nonce) {
        try {
            byte[] iv = nonce == null ? new byte[12] : nonce;
            if (nonce == null) RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(mode, new javax.crypto.spec.SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            byte[] output = cipher.doFinal(input);
            if (mode == Cipher.DECRYPT_MODE) return new String(output, java.nio.charset.StandardCharsets.UTF_8);
            byte[] packed = new byte[iv.length + output.length]; System.arraycopy(iv, 0, packed, 0, iv.length); System.arraycopy(output, 0, packed, iv.length, output.length);
            return Base64.getEncoder().encodeToString(packed);
        } catch (Exception exception) { throw new IllegalStateException("unable to protect secret", exception); }
    }
}
