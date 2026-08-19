package com.frameflow.identity.support;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import org.springframework.test.context.DynamicPropertyRegistry;

/** Throwaway RS256 keypair for tests only; never used in production. */
public final class TestJwtKeys {

    public static final String KID = "test-kid-0001";
    private static final KeyPair KEY_PAIR = generate();

    private TestJwtKeys() {
    }

    private static KeyPair generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate test RSA key", e);
        }
    }

    public static String privatePem() {
        return pem("PRIVATE KEY", ((RSAPrivateKey) KEY_PAIR.getPrivate()).getEncoded());
    }

    public static String publicPem() {
        return pem("PUBLIC KEY", ((RSAPublicKey) KEY_PAIR.getPublic()).getEncoded());
    }

    public static void register(DynamicPropertyRegistry registry) {
        registry.add("frameflow.jwt.kid", () -> KID);
        registry.add("frameflow.jwt.private-key-pem", TestJwtKeys::privatePem);
        registry.add("frameflow.jwt.public-key-pem", TestJwtKeys::publicPem);
    }

    public static java.security.KeyPair keyPair() {
        return KEY_PAIR;
    }

    private static String pem(String label, byte[] der) {
        return "-----BEGIN " + label + "-----\n"
                + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(der)
                + "\n-----END " + label + "-----\n";
    }
}