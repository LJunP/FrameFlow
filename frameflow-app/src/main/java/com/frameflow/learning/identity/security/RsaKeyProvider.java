package com.frameflow.learning.identity.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.nimbusds.jose.jwk.RSAKey;

/**
 * RS256 签名密钥提供者。
 *
 * <p>local 默认允许启动时生成临时密钥，便于零配置开发。远程环境必须关闭
 * {@code allowEphemeralRsaKey}，并从 Secret 文件或环境变量加载稳定密钥对。
 * 否则重启/回滚会使现有 access token 失效，多副本也会互不认证。</p>
 */
@Component
public class RsaKeyProvider {

    private static final int MIN_RSA_BITS = 2048;
    private final RSAKey rsaKey;

    public RsaKeyProvider(SecurityProperties properties) {
        KeyMaterial privateMaterial = material(
                "jwt-private-key", properties.jwtPrivateKeyPem(), properties.jwtPrivateKeyFile());
        KeyMaterial publicMaterial = material(
                "jwt-public-key", properties.jwtPublicKeyPem(), properties.jwtPublicKeyFile());

        if (privateMaterial.empty() && publicMaterial.empty()) {
            if (!properties.allowEphemeralRsaKey()) {
                throw new IllegalStateException(
                        "已禁用临时 RSA 密钥，但未配置完整 JWT 公私钥对");
            }
            this.rsaKey = generateEphemeral();
            return;
        }
        if (privateMaterial.empty() || publicMaterial.empty()) {
            throw new IllegalStateException("JWT RSA 私钥和公钥必须成对配置");
        }
        this.rsaKey = parseConfigured(privateMaterial.value(), publicMaterial.value());
    }

    public RSAKey rsaKey() {
        return rsaKey;
    }

    private RSAKey generateEphemeral() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(MIN_RSA_BITS);
            KeyPair pair = generator.generateKeyPair();
            return buildKey((RSAPrivateKey) pair.getPrivate(), (RSAPublicKey) pair.getPublic());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("无法生成 RSA 密钥对", e);
        }
    }

    private RSAKey parseConfigured(String privatePem, String publicPem) {
        try {
            KeyFactory factory = KeyFactory.getInstance("RSA");
            RSAPrivateKey privateKey = (RSAPrivateKey) factory.generatePrivate(
                    new PKCS8EncodedKeySpec(decodePem(privatePem, "PRIVATE KEY")));
            RSAPublicKey publicKey = (RSAPublicKey) factory.generatePublic(
                    new X509EncodedKeySpec(decodePem(publicPem, "PUBLIC KEY")));
            if (!privateKey.getModulus().equals(publicKey.getModulus())) {
                throw new IllegalStateException("JWT RSA 公私钥不属于同一密钥对");
            }
            if (publicKey.getModulus().bitLength() < MIN_RSA_BITS) {
                throw new IllegalStateException("JWT RSA 密钥长度不得低于 " + MIN_RSA_BITS + " bit");
            }
            return buildKey(privateKey, publicKey);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "无法解析 JWT RSA 密钥：私钥必须为 PKCS#8，公钥必须为 X.509 PEM", e);
        }
    }

    private RSAKey buildKey(RSAPrivateKey privateKey, RSAPublicKey publicKey) {
        // ★ 核心：key id 由公钥指纹稳定派生。同一密钥在重启/回滚/多副本上
        // 产生同一 kid，旋转密钥则自然变化；不记录私钥本身。
        String keyId = "frameflow-" + fingerprint(publicKey).substring(0, 16);
        return new RSAKey.Builder(publicKey).privateKey(privateKey).keyID(keyId).build();
    }

    private String fingerprint(RSAPublicKey publicKey) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(publicKey.getEncoded());
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("系统缺少 SHA-256", e);
        }
    }

    private KeyMaterial material(String name, String inlinePem, String fileName) {
        boolean hasInline = StringUtils.hasText(inlinePem);
        boolean hasFile = StringUtils.hasText(fileName);
        if (hasInline && hasFile) {
            throw new IllegalStateException(name + " 不能同时配置 PEM 文本和文件");
        }
        if (hasInline) {
            return new KeyMaterial(inlinePem);
        }
        if (!hasFile) {
            return KeyMaterial.EMPTY;
        }
        Path path = Path.of(fileName);
        if (!path.isAbsolute()) {
            throw new IllegalStateException(name + " 文件必须使用绝对路径");
        }
        try {
            return new KeyMaterial(Files.readString(path, StandardCharsets.US_ASCII));
        } catch (IOException e) {
            throw new IllegalStateException("无法读取 " + name + " 文件", e);
        }
    }

    private byte[] decodePem(String pem, String type) {
        String begin = "-----BEGIN " + type + "-----";
        String end = "-----END " + type + "-----";
        if (!pem.contains(begin) || !pem.contains(end)) {
            throw new IllegalStateException("JWT " + type + " PEM 头尾不完整");
        }
        String base64 = pem.replace(begin, "").replace(end, "").replaceAll("\\s", "");
        try {
            return Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("JWT " + type + " PEM Base64 无效", e);
        }
    }

    private record KeyMaterial(String value) {
        private static final KeyMaterial EMPTY = new KeyMaterial("");

        boolean empty() {
            return !StringUtils.hasText(value);
        }
    }
}
