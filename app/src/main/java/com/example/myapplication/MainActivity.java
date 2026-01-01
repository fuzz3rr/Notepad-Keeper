package com.example.myapplication;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.security.crypto.EncryptedFile;
import androidx.security.crypto.MasterKey;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executor;

public class MainActivity extends AppCompatActivity {

    private EditText editTextNote;
    private BiometricPrompt biometricPrompt;
    private BiometricPrompt.PromptInfo promptInfo;
    private Runnable actionToExecute;
    private View privacyCover;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_main);

        privacyCover = findViewById(R.id.privacy_cover);
        editTextNote = findViewById(R.id.editTextNote);
        Button buttonSave = findViewById(R.id.buttonSave);
        Button buttonLoad = findViewById(R.id.buttonLoad);
        Button buttonExport = findViewById(R.id.buttonExport);
        Button buttonImport = findViewById(R.id.buttonImport);
        Button buttonClear = findViewById(R.id.buttonClear);

        Executor executor = ContextCompat.getMainExecutor(this);
        biometricPrompt = new BiometricPrompt(MainActivity.this,
                executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                actionToExecute = null;
                Toast.makeText(getApplicationContext(),
                        "Authentication error: " + errString, Toast.LENGTH_SHORT)
                        .show();
            }

            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                if (actionToExecute != null) {
                    actionToExecute.run();
                    actionToExecute = null;
                }
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                actionToExecute = null;
                Toast.makeText(getApplicationContext(), "Authentication failed",
                        Toast.LENGTH_SHORT)
                        .show();
            }
        });

        promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Authentication required")
                .setSubtitle("Log in using your screen lock PIN, pattern, or password")
                .setAllowedAuthenticators(BiometricManager.Authenticators.DEVICE_CREDENTIAL | BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .build();

        buttonSave.setOnClickListener(v -> authenticateAndPerformAction(this::saveNote));
        buttonLoad.setOnClickListener(v -> showFileSelectionDialog(this::loadNote));
        buttonClear.setOnClickListener(v -> showFileSelectionDialog(this::clearNote));
        buttonExport.setOnClickListener(v -> showFileSelectionDialog(this::exportNote));
        buttonImport.setOnClickListener(v -> showImportFileSelectionDialog());
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (privacyCover != null) {
            privacyCover.setVisibility(hasFocus ? View.GONE : View.VISIBLE);
        }
    }

    private void authenticateAndPerformAction(Runnable action) {
        this.actionToExecute = action;
        biometricPrompt.authenticate(promptInfo);
    }

    private void showFileSelectionDialog(FileAction action) {
        File[] files = getFilesDir().listFiles((dir, name) -> name.startsWith("note_") && name.endsWith(".txt"));
        if (files == null || files.length == 0) {
            Toast.makeText(this, "No notes found", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] fileNames = new String[files.length];
        for (int i = 0; i < files.length; i++) {
            fileNames[i] = files[i].getName();
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select a note")
                .setItems(fileNames, (dialog, which) -> {
                    File selectedFile = files[which];
                    authenticateAndPerformAction(() -> action.perform(selectedFile));
                });
        builder.show();
    }

    private void showImportFileSelectionDialog() {
        File[] files = getCacheDir().listFiles((dir, name) -> name.startsWith("exported_") && name.endsWith(".txt"));
        if (files == null || files.length == 0) {
            Toast.makeText(this, "No exported notes to import found", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] fileNames = new String[files.length];
        for (int i = 0; i < files.length; i++) {
            fileNames[i] = files[i].getName();
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select a note to import")
                .setItems(fileNames, (dialog, which) -> {
                    File selectedFile = files[which];
                    authenticateAndPerformAction(() -> importNote(selectedFile));
                });
        builder.show();
    }

    private void saveNote() {
        try {
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String fileName = "note_" + timeStamp + ".txt";
            File file = new File(getFilesDir(), fileName);

            MasterKey masterKey = SecurityUtils.getOrCreateMasterKey(this);
            EncryptedFile encryptedFile = SecurityUtils.getEncryptedFile(this, file, masterKey);

            try (FileOutputStream fileOutputStream = encryptedFile.openFileOutput()) {
                fileOutputStream.write(editTextNote.getText().toString().getBytes(StandardCharsets.UTF_8));
                Toast.makeText(this, "Note saved as " + fileName, Toast.LENGTH_SHORT).show();
                editTextNote.setText(""); // Clear the text field after saving
            }
        } catch (GeneralSecurityException | IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error saving note", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadNote(File file) {
        try {
            MasterKey masterKey = SecurityUtils.getOrCreateMasterKey(this);
            EncryptedFile encryptedFile = SecurityUtils.getEncryptedFile(this, file, masterKey);

            try (FileInputStream fileInputStream = encryptedFile.openFileInput()) {
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                int nextByte;
                while ((nextByte = fileInputStream.read()) != -1) {
                    byteArrayOutputStream.write(nextByte);
                }
                editTextNote.setText(byteArrayOutputStream.toString(StandardCharsets.UTF_8.name()));
                Toast.makeText(this, "Loaded: " + file.getName(), Toast.LENGTH_SHORT).show();
            }        } catch (GeneralSecurityException | IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error loading note", Toast.LENGTH_SHORT).show();
        }
    }

    private void clearNote(File file) {
        if (file.exists()) {
            if (file.delete()) {
                Toast.makeText(this, "Deleted: " + file.getName(), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Error deleting note", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "File not found", Toast.LENGTH_SHORT).show();
        }
        editTextNote.setText("");
    }

    private void exportNote(File file) {
        // A real implementation would ask for a password and encrypt the file.
        File exportFile = new File(getCacheDir(), "exported_" + file.getName());
        try (FileInputStream fis = SecurityUtils.getEncryptedFile(this, file, SecurityUtils.getOrCreateMasterKey(this)).openFileInput();
             FileOutputStream fos = new FileOutputStream(exportFile)) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = fis.read(buffer)) > 0) {
                fos.write(buffer, 0, length);
            }
            Toast.makeText(this, "Note exported to " + exportFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (GeneralSecurityException | IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error exporting note", Toast.LENGTH_SHORT).show();
        }
    }

    private void importNote(File fileToImport) {
        if (!fileToImport.exists()) {
            Toast.makeText(this, "Import file not found", Toast.LENGTH_SHORT).show();
            return;
        }

        try (FileInputStream fis = new FileInputStream(fileToImport)) {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            int nextByte;
            while ((nextByte = fis.read()) != -1) {
                byteArrayOutputStream.write(nextByte);
            }
            editTextNote.setText(byteArrayOutputStream.toString(StandardCharsets.UTF_8.name()));
            Toast.makeText(this, "Note imported from " + fileToImport.getName(), Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    interface FileAction {
        void perform(File file);
    }
}
