package com.frameflow.learning.identity.security;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

import org.springframework.stereotype.Component;

import com.nimbusds.jose.jwk.RSAKey;

/**
 * RS256 签名密钥的提供者。
 *
 * 【重要·当前为开发态方案】密钥对在启动时生成、只活在内存里：
 * - 优点：零配置即可跑，私钥永不落盘；
 * - 代价：重启后旧 access token 全部失效（最长影响 = access token 的 15 分钟
 *   有效期；refresh token 不受影响，它走数据库不依赖这把钥匙）。
 * - 生产要求（docs/02）：密钥只能来自环境变量/Secret，届时把本类改为
 *   "从配置加载 PEM"，接口不变。
 */
@Component
public class RsaKeyProvider {

    private final RSAKey rsaKey;

    public RsaKeyProvider() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            // （说明：RSAKey.Builder 不抛受检异常；JWKGenerationException 只在
            // 使用 RSAKeyGenerator 随机生成时出现——这里自己生成密钥对再包装，更直观。）
            // ★ 核心：RSA 公私钥对的用途分工——私钥签名（只有签发方持有），
            // 公钥验签（可以发给任何人，包括未来的微服务/网关）。
            // 这就是选 RS256 而不是 HS256 的根本原因：HS256 的签名和验签
            // 用同一把密钥，验签方就必须也持有能签名的密钥，密钥一泄露
            // 任何人都能签发 token；RS256 泄露的只是"能验签"的能力，无害。
            this.rsaKey = new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                    .privateKey((RSAPrivateKey) pair.getPrivate())
                    .keyID("frameflow-f1")
                    .build();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("无法生成 RSA 密钥对", e);
        }
    }

    public RSAKey rsaKey() {
        return rsaKey;
    }
}
