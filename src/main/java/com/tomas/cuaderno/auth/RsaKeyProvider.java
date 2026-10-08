package com.tomas.cuaderno.auth;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

@Component
public class RsaKeyProvider {
    private final PrivateKey privateKey;
    private final RSAPublicKey publicKey;
    private final String keyId;

    public RsaKeyProvider(AuthProperties properties) {
        try {
            String pem = properties.getPrivateKeyPem();
            if (pem == null || pem.isBlank()) {
                String file = properties.getPrivateKeyFile();
                if (file == null || file.isBlank()) throw new IllegalArgumentException("AUTH_PRIVATE_KEY_FILE is required");
                pem = Files.readString(Path.of(file));
            }
            String encoded = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "").replaceAll("\\s", "");
            privateKey = KeyFactory.getInstance("RSA").generatePrivate(
                    new PKCS8EncodedKeySpec(Base64.getDecoder().decode(encoded)));
            RSAPrivateCrtKey rsaPrivate = (RSAPrivateCrtKey) privateKey;
            publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(
                    new RSAPublicKeySpec(rsaPrivate.getModulus(), rsaPrivate.getPublicExponent()));
            keyId = HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(publicKey.getEncoded())).substring(0, 16);
        } catch (Exception ex) {
            throw new IllegalStateException("AUTH_PRIVATE_KEY_FILE must contain an RSA PKCS#8 private key", ex);
        }
    }

    public PrivateKey privateKey() { return privateKey; }
    public RSAPublicKey publicKey() { return publicKey; }
    public java.math.BigInteger modulus() { return publicKey.getModulus(); }
    public java.math.BigInteger exponent() { return publicKey.getPublicExponent(); }
    public String keyId() { return keyId; }
}
