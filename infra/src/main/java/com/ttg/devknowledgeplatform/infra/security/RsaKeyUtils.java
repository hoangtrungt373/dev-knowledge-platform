package com.ttg.devknowledgeplatform.infra.security;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import org.springframework.core.io.Resource;

/**
 * Parses PEM-encoded RSA key material into JDK {@link PrivateKey}/{@link PublicKey} instances.
 *
 * <p>Lives here (not {@code identity-service}, not {@code ecommerce-service}) because both modules
 * need it and neither can depend on the other: {@code identity-service} loads both keys to sign and
 * verify the tokens it issues, while {@code ecommerce-service}'s {@code JwtVerifier} loads only the
 * public key to verify tokens issued elsewhere. Same "utility needed by two siblings" reasoning as
 * this module's {@code StorageService}/{@code CacheNames} — see {@code infra/CLAUDE.md}.
 *
 * <p>Expects the private key in PKCS#8 PEM ({@code -----BEGIN PRIVATE KEY-----}, the format
 * {@code openssl genpkey}/{@code openssl pkcs8} produce — not the legacy PKCS#1
 * {@code -----BEGIN RSA PRIVATE KEY-----} form) and the public key in X.509 SubjectPublicKeyInfo
 * PEM ({@code -----BEGIN PUBLIC KEY-----}, what {@code openssl rsa -pubout} produces).
 */
public final class RsaKeyUtils {

    private RsaKeyUtils() {
    }

    /**
     * Reads and parses a PKCS#8 PEM-encoded RSA private key.
     *
     * @param resource the PEM file (classpath or filesystem)
     * @return the parsed private key
     * @throws IllegalStateException if the resource can't be read or doesn't contain a valid key
     */
    public static PrivateKey readPrivateKey(Resource resource) {
        byte[] der = decodePem(resource);
        try {
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Invalid RSA private key at " + resource, e);
        }
    }

    /**
     * Reads and parses an X.509 PEM-encoded RSA public key.
     *
     * @param resource the PEM file (classpath or filesystem)
     * @return the parsed public key
     * @throws IllegalStateException if the resource can't be read or doesn't contain a valid key
     */
    public static PublicKey readPublicKey(Resource resource) {
        byte[] der = decodePem(resource);
        try {
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Invalid RSA public key at " + resource, e);
        }
    }

    private static byte[] decodePem(Resource resource) {
        String pem;
        try (InputStream in = resource.getInputStream()) {
            pem = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read key resource " + resource, e);
        }

        String base64 = pem.lines()
                .filter(line -> !line.startsWith("-----"))
                .reduce("", String::concat);
        return Base64.getDecoder().decode(base64);
    }
}
