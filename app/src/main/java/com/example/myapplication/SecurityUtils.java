package com.example.myapplication;

import android.content.Context;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import androidx.security.crypto.EncryptedFile;
import androidx.security.crypto.MasterKey;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;

public class SecurityUtils {

    public static MasterKey getOrCreateMasterKey(Context context) throws GeneralSecurityException, IOException {
        KeyGenParameterSpec.Builder keyGenParameterSpecBuilder = new KeyGenParameterSpec.Builder(
                MasterKey.DEFAULT_MASTER_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(true);

        keyGenParameterSpecBuilder.setInvalidatedByBiometricEnrollment(false);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            keyGenParameterSpecBuilder.setUserAuthenticationParameters(10, KeyProperties.AUTH_DEVICE_CREDENTIAL);
        } else {
            keyGenParameterSpecBuilder.setUserAuthenticationValidityDurationSeconds(10);
        }

        return new MasterKey.Builder(context)
                .setKeyGenParameterSpec(keyGenParameterSpecBuilder.build())
                .build();
    }

    public static EncryptedFile getEncryptedFile(Context context, File file, MasterKey masterKey) throws GeneralSecurityException, IOException {
        return new EncryptedFile.Builder(
                context,
                file,
                masterKey,
                EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build();
    }

}