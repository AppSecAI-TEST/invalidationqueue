/*
Copyright 2017 Michael Chermside

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package com.mcherm.invalidationqueue.util;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;


/**
 * This class provides methods that take a serializable object and convert it into a string
 * of printable ASCII characters, or take that string and converts it back into the original
 * object. The encryption algorithms are chosen to make the string unreadable by anyone who
 * does not possess the key, but also to make it tamper-resistant.
 * <p>
 * In order to make it tamper resistant, we use AWS with GCM mode ("Galois/Counter Mode").
 * Reasons why this is resistant to tampering are fairly well explained in <a
 * href="https://en.wikipedia.org/wiki/Galois/Counter_Mode" the Wikipedia page</a>. More
 * detailed specifications and reasoning for the values chosen can be found in <a
 * href="http://nvlpubs.nist.gov/nistpubs/Legacy/SP/nistspecialpublication800-38d.pdf">this
 * NIST publication</a>.
 * <p>
 * Instances of this class are threadsafe.
 */
public class SecureSerializer {

    // NOTE: both lengths in bytes, not bits
    private final static int TAG_LENGTH = 128/8; // 96 bits will be sufficient for very short messages; 128 would work for longer ones
    private final static int NONCE_LENGTH = 12; // Length in bytes of the "nonce" AKA "initialization vector"
    private final static int MAX_PASSWORDS_TO_CACHE = 10; // we won't cache more than this many; there should only ever be 1

    private final SecureRandom secureRandom; // NOTE: as of Java 1.7, this is guaranteed to be threadsafe
    private final ConcurrentMap<String, byte[]> passwordToKeyMap;

    /**
     * The constructor initializes a SecureRandom instance to be used for generating the nonces.
     */
    public SecureSerializer() {
        // --- Create a SecureRandom we can use ---
        SecureRandom theSecureRandom;
        try {
            theSecureRandom = SecureRandom.getInstanceStrong();
        } catch (NoSuchAlgorithmException err) {
            theSecureRandom = null; // Later on, things will fail with a NullPointerException, but we cannot throw from here
        }
        secureRandom = theSecureRandom;
        passwordToKeyMap = new ConcurrentHashMap<>(MAX_PASSWORDS_TO_CACHE * 2);
    }


    /**
     * This serializes an object to a String of printable ASCII which can be used to restore the
     * original object.
     * <p>
     * This is passed any object that can be Serialized (a String, for instance, is acceptable) and the
     * secret password to use, and it returns a string containing only printable ASCII characters
     * (currently it matches the regex "<code>A-Za-z0-9+/=</code>") which can freely be passed
     * to an adversary to manipulate then return, and the result can be passed to
     * <code>deserializeFromTamperproofAscii()</code> to restore the original with no way for
     * the adversary to modify the result unless they know the <code>secretPassword</code>.
     *
     * @param thingToSerialize any Serializable object
     * @param secretPassword the string to use as a password. Must be long enough.
     * @return printable ASCII containing the encrypted value.
     */
    public String serializeToTamperproofAscii(Serializable thingToSerialize, String secretPassword) {
        // --- Serialize the object ---
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        try {
            new ObjectOutputStream(byteStream).writeObject(thingToSerialize);
        } catch (IOException err) {
            throw new RuntimeException("IOException reading from byte array; this should never happen.", err);
        }
        byte[] plaintextBytes = byteStream.toByteArray();

        // --- Gather other inputs to the encryption ---
        byte[] nonceBytes = new byte[NONCE_LENGTH];
        secureRandom.nextBytes(nonceBytes);

        // --- Perform the encryption ---
        byte[] encryptedBytes = encryptOrDecrypt(Cipher.ENCRYPT_MODE, secretPassword, nonceBytes, plaintextBytes);

        // --- Gather the outputs of the encryption and assemble into one binary block ---
        byte[] allBytes = new byte[NONCE_LENGTH + encryptedBytes.length];
        System.arraycopy(nonceBytes, 0, allBytes, 0, NONCE_LENGTH);
        System.arraycopy(encryptedBytes, 0, allBytes, NONCE_LENGTH, encryptedBytes.length);

        // --- Convert THAT into printable ascii using a Base64 encoding ---
        String encryptedEncodedString = Base64.getEncoder().encodeToString(allBytes);

        // --- Return the result ---
        return encryptedEncodedString;
    }


    /**
     * This restores an object from the String of printable ASCII which
     * <code>serializeToTamperproofAscii</code> generates.
     *
     * @param encryptedEncodedString the string that was generated by serializeToTamperproofAscii
     * @param secretPassword the string to use as a password. Must be long enough.
     * @return the original object
     */
    public Serializable deserializeFromTamperproofAscii(String encryptedEncodedString, String secretPassword) {
        // --- Convert from Base64 into bytes ---
        byte[] allBytes = Base64.getDecoder().decode(encryptedEncodedString);

        // --- Split into nonce and message ---
        byte[] nonceBytes = new byte[NONCE_LENGTH];
        System.arraycopy(allBytes, 0, nonceBytes, 0, NONCE_LENGTH);
        byte[] encryptedBytes = new byte[allBytes.length - NONCE_LENGTH];
        System.arraycopy(allBytes, NONCE_LENGTH, encryptedBytes, 0, allBytes.length - NONCE_LENGTH);

        // --- Perform decryption ---
        byte[] plaintextBytes = encryptOrDecrypt(Cipher.DECRYPT_MODE, secretPassword, nonceBytes, encryptedBytes);

        // --- Deserialize ---
        Serializable serializedThing = null;
        try {
            ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(plaintextBytes));
            serializedThing = (Serializable) objectInputStream.readObject();
        } catch (IOException err) {
            throw new RuntimeException("IOException reading from byte array; this should never happen.", err);
        } catch (ClassNotFoundException err) {
            throw new RuntimeException("Exception deserializing encrypted data.", err);
        }

        // --- Return the result ---
        return serializedThing;
    }


    /**
     * This performs the actual encryption or decryption.
     *
     * @param opMode either Cipher.ENCRYPT_MODE or Cipher.DECRYPT_MODE
     * @param secretPassword the password that will be used to encrypt the data
     * @param nonceBytes the bytes being used as a nonce. No two calls may ever use the same value or security is compromised.
     * @param inputBytes the plaintext (in ENCRYPT_MODE) or cyphertext (in DECRYPT_MODE)
     * @return the cyphertext (in ENCRYPT_MODE) or plaintext (in DECRYPT_MODE)
     * @throws RuntimeException for any issue with the cryptography
     */
    private byte[] encryptOrDecrypt(int opMode, String secretPassword, byte[] nonceBytes, byte[] inputBytes) {
        try {
            // --- Convert the password to a key, caching the result ---
            byte[] keyBytes;
            byte[] cachedKeyBytes = passwordToKeyMap.get(secretPassword);
            if (cachedKeyBytes != null) {
                keyBytes = cachedKeyBytes;
            } else {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                md.update(secretPassword.getBytes());
                byte[] hashOfPassword = md.digest();
                keyBytes = Arrays.copyOf(hashOfPassword, 16); // just grab the first 16 bytes of the hash for the key
                if (passwordToKeyMap.size() < MAX_PASSWORDS_TO_CACHE) {
                    passwordToKeyMap.put(secretPassword, keyBytes);
                }
            }

            // --- Actual encryption / decryption ---
            Key key = new SecretKeySpec(keyBytes, "AES");
            GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH * 8, nonceBytes);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); // Not guaranteed to be provided, but GCM protects message integrity and CBC doesn't (ECB is even worse)
            try {
                cipher.init(opMode, key, parameterSpec);
            } catch (InvalidKeyException err) {
                if (err.getMessage().equals("Illegal key size")) {
                    throw new GeneralSecurityException(
                            "Unable to use high key size. You may need to enable unlimited strength cryptography in your JVM.", err);
                }
            }
            return cipher.doFinal(inputBytes);
        } catch(AEADBadTagException err) {
            throw new TamperedDataException(err);
        } catch(GeneralSecurityException err) {
            throw new RuntimeException("Error utilizing cryptography.", err);
        }
    }


    /**
     * A specific type of exception to throw if the data appears to have been tampered with.
     */
    public static class TamperedDataException extends RuntimeException {
        /** Constructor. */
        public TamperedDataException(Throwable err) {
            super(err);
        }
    }

}
