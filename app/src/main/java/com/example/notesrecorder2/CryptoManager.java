package com.example.notesrecorder2;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Log;

import com.google.android.gms.common.util.Hex;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
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
import javax.crypto.spec.IvParameterSpec;

// Q: How can we enable encryption across multiple devices?
// A: We will use public key cryptography (ECC) for key exchange.

public class CryptoManager {
    private static final String TAG = "CryptoManager";

    private static volatile CryptoManager instance = null;
    private final boolean isDebug;
    private static final String keyAlias = "encryptKey";
    private static final String provider = "AndroidKeyStore";
    private static final int keySize = 128;
    private KeyStore ks;
    private KeyStore.Entry entry;
    private SecretKey encryptKey = null;

    public CryptoManager(Context c) {

        isDebug = (c.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        if (isDebug) {
            for (Provider provider : Security.getProviders()) {
                Log.i(TAG, "Provider: " + provider.getName());
            }
        }

        // Retrieve key from KeyStore
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

        if (isDebug) {
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
        }

        try {
            entry = ks.getEntry(keyAlias, null);
            encryptKey = (SecretKey) ks.getKey(keyAlias, null);
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
                Log.e(TAG, "KeyStoreException" + e);
            } catch (NoSuchAlgorithmException e) {
                Log.e(TAG, "NoSuchAlgorithmException" + e);
            } catch (UnrecoverableKeyException e) {
                Log.e(TAG, "UnrecoverableKeyException" + e);
            }
            Log.d(TAG, "Key generated");
        } else {
            Log.d(TAG, "Key retrieved");
        }

        if (encryptKey == null) {
            Log.e(TAG, "Failed to get encryptKey");
        }
    }

    public static CryptoManager getInstance(Context c) {
        if (instance == null) {
            instance = new CryptoManager(c);
        }
        return instance;
    }

    // This is for "text" and "audio" notes
    public String encrypt(String data) {
        if (data == null || data.isEmpty())
            return null;

        SecretKey key = null;
        try {
            key = (SecretKey) ks.getKey(keyAlias, null);
        } catch (KeyStoreException | NoSuchAlgorithmException | UnrecoverableKeyException e) {
            Log.e(TAG, "encrypt: Error getting key " + e);
            return null;
        }
        Cipher cipher;

        try {
            cipher = Cipher.getInstance("AES/CBC/PKCS7Padding");
        } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
            Log.e(TAG, "encrypt: Error getInstance " + e);
            return null;
        }

        try {
            cipher.init(Cipher.ENCRYPT_MODE, key);
        } catch (InvalidKeyException e) {
            Log.e(TAG, "encrypt: Cipher.init error " + e);
            return null;
        }

        byte[] out = new byte[0];
        try {
            out = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (BadPaddingException | IllegalBlockSizeException e) {
            Log.e(TAG, "encrypt: Cipher.doFinal error " + e);
            return null;
        }

        // get IV
        byte[] iv = cipher.getIV();

        // Convert to Hex string
        String out_s = Hex.bytesToStringUppercase(out) + "." + Hex.bytesToStringUppercase(iv);
        Log.d(TAG, "encrypt in: " + data + " out: " + out_s + "IV size:" + iv.length);
        return out_s;
    }

    public String decrypt(String data) {
        if (data == null || data.isEmpty())
            return null;

        SecretKey key = ((KeyStore.SecretKeyEntry) entry).getSecretKey();
        Cipher cipher;

        try {
            cipher = Cipher.getInstance("AES/CBC/PKCS7Padding");
        } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
            Log.e(TAG, "decrypt: Cipher.getInstance error " + e);
            return null;
        }

        Log.d(TAG, "decrypt in: " + data);
        String[] arr = data.split("\\.");

        Log.d(TAG, "decrypt in: " + data);
        IvParameterSpec iv = new IvParameterSpec(Hex.stringToBytes(arr[1]));
        try {
            cipher.init(Cipher.DECRYPT_MODE, key, iv);
        } catch (InvalidKeyException | InvalidAlgorithmParameterException e) {
            Log.e(TAG, "decrypt: Cipher.init error " + e);
            return null;
        }

        byte[] out = new byte[0];
        try {
            // Convert Hex string to byte array first
            out = cipher.doFinal(Hex.stringToBytes(arr[0]));
        } catch (BadPaddingException | IllegalBlockSizeException e) {
            Log.e(TAG, "decrypt: Cipher.doFinal error " + e);
            return null;
        }

        String out_s = new String(out);
        Log.d(TAG, "decrypt in: " + data + " out: " + out_s);
        return out_s;
    }

    // encrypt audio file to be uploaded to cloud.
    // Return iv used in encryption.
    //
    public byte[] encryptFile(File inFile, File outFile) {
        SecretKey key = ((KeyStore.SecretKeyEntry) entry).getSecretKey();
        Cipher cipher;

        try {
            cipher = Cipher.getInstance("AES/CBC/PKCS7Padding");
        } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
            Log.e(TAG, "encryptFile: Cipher.GetInstance error " + e);
            return null;
        }

        try {
            cipher.init(Cipher.ENCRYPT_MODE, key);
        } catch (InvalidKeyException e) {
            Log.e(TAG, "encryptFile: Cipher.init error " + e);
            return null;
        }

        try (FileInputStream inStream = new FileInputStream(inFile)) {
            byte[] inputBytes = new byte[(int) inFile.length()];
            inStream.read(inputBytes);

            byte[] outBytes = cipher.doFinal(inputBytes);

            FileOutputStream outStream = new FileOutputStream(outFile);
            outStream.write(outBytes);

            outStream.close();
            inStream.close();
        } catch (FileNotFoundException | IllegalBlockSizeException | BadPaddingException e) {
            Log.e(TAG, "encryptFile: Cipher.doFinal error " + e);
            return null;
        } catch (IOException e) {
            Log.e(TAG, "encryptFile: Cipher.doFinal error " + e);
            return null;
        }

        return cipher.getIV();
    }

    public void decryptFile(File inFile, File outFile, byte[] ivBytes) {
        SecretKey key = ((KeyStore.SecretKeyEntry) entry).getSecretKey();
        Cipher cipher;

        try {
            cipher = Cipher.getInstance("AES/CBC/PKCS7Padding");
        } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
            Log.e(TAG, "decryptFile: Cipher.GetInstance error " + e);
            return;
        }

        IvParameterSpec iv = new IvParameterSpec(ivBytes);
        try {
            cipher.init(Cipher.DECRYPT_MODE, key, iv);
        } catch (InvalidKeyException | InvalidAlgorithmParameterException e) {
            Log.e(TAG, "decryptFile: Cipher.init error " + e);
            return;
        }

        try (FileInputStream inStream = new FileInputStream(inFile)) {
            byte[] inputBytes = new byte[(int) inFile.length()];
            inStream.read(inputBytes);

            byte[] outBytes = cipher.doFinal(inputBytes);

            FileOutputStream outStream = new FileOutputStream(outFile);
            outStream.write(outBytes);

            outStream.close();
            inStream.close();
        } catch (FileNotFoundException | IllegalBlockSizeException | BadPaddingException e) {
            Log.e(TAG, "decryptFile: Cipher.doFinal error " + e);
        } catch (IOException e) {
            Log.e(TAG, "decryptFile: Cipher.doFinal error " + e);
        }
    }

    private void generateKey() {
        // if key does not exists, create it
        KeyGenerator kg = null;
        try {
            kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, provider);
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
