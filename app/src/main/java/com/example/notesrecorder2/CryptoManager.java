package com.example.notesrecorder2;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.system.ErrnoException;
import android.util.Log;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.Security;
import java.security.UnrecoverableEntryException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Enumeration;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public class CryptoManager {
    private static final String TAG = "CryptoManager";

    private static final String keyAlias = "encryptKey";
    private static final int keySize = 128;
    private KeyStore ks;
    private KeyStore.Entry entry;
    private SecretKey encryptKey = null;

    // Should KeyStore. But how to support multiple-devices?
    public CryptoManager(Context c) {
        // Retrieve key from KeyStore
        for (Provider provider : Security.getProviders()) {
            Log.i(TAG, "Provider: " + provider.getName());
        }
        try {
            ks = KeyStore.getInstance("AndroidKeyStore");
        } catch (KeyStoreException e) {
            throw new RuntimeException(e);
        }
        try {
            ks.load(null);
        } catch (CertificateException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }

        Enumeration<String> stringEnum = null;
        try {
            stringEnum = ks.aliases();
        } catch (KeyStoreException e) {
            throw new RuntimeException(e);
        }
        while (stringEnum.hasMoreElements()) {
            String s = stringEnum.nextElement();
            Log.d(TAG, "Alias: " + s);
        }

        try {
            entry = ks.getEntry(keyAlias, null);
        } catch (NullPointerException e) {
            Log.w(TAG, "encryptKey not found. Creating one...");
        } catch (UnrecoverableEntryException e) {
            throw new RuntimeException(e);
        } catch (KeyStoreException e) {
            throw new RuntimeException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }

        if (encryptKey == null) {
            generateKey();
            try {
                encryptKey = (SecretKey) ks.getKey(keyAlias, null);
            } catch (KeyStoreException e) {
                throw new RuntimeException(e);
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            } catch (UnrecoverableKeyException e) {
                throw new RuntimeException(e);
            }
        }

        if (encryptKey == null) {
            Log.d(TAG, "Failed to get encryptKey");
        } else {
            Log.d(TAG, "Key retrieved/create");
        }
    }

    public String encrypt(String data) {
        if (data == null || data.isEmpty())
            return null;

        SecretKey key = null;
        try {
            key = (SecretKey) ks.getKey(keyAlias, null);
        } catch (KeyStoreException e) {
            throw new RuntimeException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        } catch (UnrecoverableKeyException e) {
            throw new RuntimeException(e);
        }
        Cipher cipher;

        try {
            cipher = Cipher.getInstance("AES/CBC/PKCS7Padding");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        } catch (NoSuchPaddingException e) {
            throw new RuntimeException(e);
        }

        try {
            cipher.init(Cipher.ENCRYPT_MODE, key);
        } catch (InvalidKeyException e) {
            throw new RuntimeException(e);
        }

        byte[] out = new byte[0];
        try {
            out = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (BadPaddingException e) {
            throw new RuntimeException(e);
        } catch (IllegalBlockSizeException e) {
            throw new RuntimeException(e);
        }

        Log.d(TAG, "encrypt in: " + data + " out: " + out.toString());
        return out.toString();
    }

    public String decrypt(String data) {
        SecretKey key = ((KeyStore.SecretKeyEntry) entry).getSecretKey();
        Cipher cipher;

        try {
            cipher = Cipher.getInstance("AES/CBC/PKCS7Padding");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        } catch (NoSuchPaddingException e) {
            throw new RuntimeException(e);
        }

        try {
            cipher.init(Cipher.DECRYPT_MODE, key);
        } catch (InvalidKeyException e) {
            throw new RuntimeException(e);
        }

        byte[] out = new byte[0];
        try {
            out = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (BadPaddingException e) {
            throw new RuntimeException(e);
        } catch (IllegalBlockSizeException e) {
            throw new RuntimeException(e);
        }

        Log.d(TAG, "decrypt in: " + data + " out: " + out.toString());
        return out.toString();
    }

    private void generateKey() {
        // if key does not exists, create it
        KeyGenerator kg = null;
        try {
            kg = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        } catch (NoSuchAlgorithmException ex) {
            throw new RuntimeException(ex);
        } catch (NoSuchProviderException ex) {
            throw new RuntimeException(ex);
        }

        try {
            kg.init(new KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setKeySize(keySize)
                    .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                    .build());
        } catch (InvalidAlgorithmParameterException ex) {
            throw new RuntimeException(ex);
        }

        encryptKey = kg.generateKey();
        try {
            entry = ks.getEntry(keyAlias, null);
        } catch (KeyStoreException ex) {
            throw new RuntimeException(ex);
        } catch (NoSuchAlgorithmException ex) {
            throw new RuntimeException(ex);
        } catch (UnrecoverableEntryException ex) {
            throw new RuntimeException(ex);
        }
    }
}
