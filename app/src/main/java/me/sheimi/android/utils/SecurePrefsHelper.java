package me.sheimi.android.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

import timber.log.Timber;

/**
 * Stores small secrets (SSH key passphrases) in a private SharedPreferences file, encrypted
 * with AES-256-GCM using a key that never leaves the Android Keystore.
 */
public class SecurePrefsHelper {

    private static final String ANDROID_KEY_STORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "mgit_secrets_aes";
    private static final String PREFS_FILE_NAME = "secrets";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;

    // left behind by the old secure-preferences based implementation
    private static final String LEGACY_PREFS_FILE_NAME = "sec_prefs.xml";
    private static final String LEGACY_KEY_ALIAS = "mgit_prefs";

    private final SharedPreferences mPrefs;
    private final SecretKey mKey;

    public SecurePrefsHelper(Context context) throws SecurePrefsException {
        try {
            KeyStore keyStore = KeyStore.getInstance(ANDROID_KEY_STORE);
            keyStore.load(null);
            removeLegacyStore(context, keyStore);
            mKey = getOrCreateKey(keyStore);
        } catch (Exception e) {
            Timber.e(e, "keystore error");
            throw new SecurePrefsException(e);
        }
        mPrefs = context.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE);
    }

    private static SecretKey getOrCreateKey(KeyStore keyStore) throws Exception {
        KeyStore.Entry entry = keyStore.getEntry(KEY_ALIAS, null);
        if (entry instanceof KeyStore.SecretKeyEntry) {
            return ((KeyStore.SecretKeyEntry) entry).getSecretKey();
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE);
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build());
        return generator.generateKey();
    }

    /**
     * The previous implementation's secrets can't be carried over without its abandoned library,
     * so drop them; affected keys just need their passphrase entered again.
     */
    private static void removeLegacyStore(Context context, KeyStore keyStore) throws Exception {
        File legacyPrefs = new File(context.getApplicationInfo().dataDir, "shared_prefs/" + LEGACY_PREFS_FILE_NAME);
        if (legacyPrefs.exists() && !legacyPrefs.delete()) {
            Timber.w("could not delete legacy secure prefs");
        }
        if (keyStore.containsAlias(LEGACY_KEY_ALIAS)) {
            keyStore.deleteEntry(LEGACY_KEY_ALIAS);
        }
    }

    /**
     * Retrieve a String value from the secured preferences.
     *
     * @param pref
     * @return value of pref or null if no such pref (or it can no longer be decrypted)
     */
    public String get(String pref) {
        String stored = mPrefs.getString(pref, null);
        if (stored == null) {
            return null;
        }
        try {
            ByteBuffer buffer = ByteBuffer.wrap(Base64.decode(stored, Base64.NO_WRAP));
            byte[] iv = new byte[buffer.get()];
            buffer.get(iv);
            byte[] cipherText = new byte[buffer.remaining()];
            buffer.get(cipherText);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, mKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | RuntimeException e) {
            Timber.e(e, "could not decrypt secret");
            return null;
        }
    }

    /**
     * Store a String value into secured preferences.
     * @param name
     * @param value
     */
    public void set(String name, String value) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, mKey);
            byte[] iv = cipher.getIV();
            byte[] cipherText = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buffer = ByteBuffer.allocate(1 + iv.length + cipherText.length);
            buffer.put((byte) iv.length).put(iv).put(cipherText);
            mPrefs.edit().putString(name, Base64.encodeToString(buffer.array(), Base64.NO_WRAP)).apply();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("could not encrypt secret", e);
        }
    }

    public void remove(String name) {
        mPrefs.edit().remove(name).apply();
    }

    public void rename(String from, String to) {
        String stored = mPrefs.getString(from, null);
        SharedPreferences.Editor editor = mPrefs.edit().remove(from);
        if (stored != null) {
            editor.putString(to, stored);
        }
        editor.apply();
    }
}
