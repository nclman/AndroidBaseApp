package com.example.notesrecorder2;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnCanceledListener;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.storage.FileDownloadTask;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.ListResult;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class CloudDataBaseManager {
    private static final String TAG = "CloudDataBaseManager";
    private static final String CollectionName = "users";
    private final FirebaseFirestore cloudDb;
    private final FirebaseStorage cloudStore;
    private final StorageReference audioRef;
    private final String Uid;
    private final Context context;

    public CloudDataBaseManager(Context c) {
        context = c;
        FirebaseUser fireUser = FirebaseAuth.getInstance().getCurrentUser();
        Uid = fireUser.getUid();
        cloudDb  = FirebaseFirestore.getInstance();
        Log.d(TAG, "Init " + fireUser.getUid());

        cloudStore = FirebaseStorage.getInstance();
        audioRef = cloudStore.getReference().child("users/" + Uid + "/audio");
    }

    // version 1: call this only when SQL db is empty
    public void sync() {
        cloudDb.collection(CollectionName).document(Uid).collection("notes")
                .get()
                .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<QuerySnapshot> task) {
                        DatabaseManager db = DatabaseManager.getInstance(context);
                        db.open();
                        if (task.isSuccessful()) {
                            for (QueryDocumentSnapshot document : task.getResult()) {
                                String text = (String) document.get("text");
                                String audio = (String) document.get("audio");

                                db.insertDbOnly(text, audio, document.getId());
                            }
                        } else {
                            Log.w(TAG, "Failed to sync from cloud");
                        }
                        db.close();
                    }
                });

        // sync audio files
        audioRef.listAll()
                .addOnSuccessListener(new OnSuccessListener<ListResult>() {
                    @Override
                    public void onSuccess(ListResult listResult) {
                        for (StorageReference ref : listResult.getItems()) {
                            File f = new File(context.getFilesDir(), "audio" + ref.getName());
                            ref.getFile(f)
                                    .addOnSuccessListener(new OnSuccessListener<FileDownloadTask.TaskSnapshot>() {
                                        @Override
                                        public void onSuccess(FileDownloadTask.TaskSnapshot taskSnapshot) {
                                            Log.d(TAG, f.getPath() + "downloaded");
                                        }
                                    })
                                    .addOnFailureListener(new OnFailureListener() {
                                        @Override
                                        public void onFailure(@NonNull Exception e) {
                                            Log.w(TAG, "download " + ref.getName() + "failed");
                                        }
                                    });
                        }
                    }
                });
    }

    // This method adds an entry in Firebase DB.
    // If there is a media file, we need to store it in Firebase Cloud Storage
    public void insert(String text, String audio_path) {
        Map<String, Object> user = new HashMap<>();
        user.put("text", text);
        user.put("audio", audio_path);
        user.put("timestamp", FieldValue.serverTimestamp());

        cloudDb.collection(CollectionName).document(Uid).collection("notes").add(user)
                .addOnSuccessListener(new OnSuccessListener<DocumentReference>() {
                    @Override
                    public void onSuccess(DocumentReference docRef) {
                        Log.d(TAG, "DocumentSnapshot added with ID: " + docRef.getId());
                        DatabaseManager db = DatabaseManager.getInstance(context);
                        db.open();
                        db.insertDbOnly(text, audio_path, docRef.getId());
                        db.close();
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.w(TAG, "Error adding document", e);
                    }
                });

        // Send audio file to cloud store
        if (audio_path != null && audio_path.isEmpty() == false) {
            Uri file = Uri.fromFile(new File(audio_path));
            StorageReference ref = audioRef.child(file.getLastPathSegment());
            ref.putFile(file)
                    .addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                        @Override
                        public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                            Log.d(TAG, "file uploaded");
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            Log.d(TAG, "File upload failed");
                        }
                    });
        }
    }

    public void delete(String id) {
        cloudDb.collection(CollectionName).document(Uid).collection("notes")
                .document(id)
                .delete()
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void unused) {
                        Log.i(TAG, "Cloud delete success");
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.w(TAG, "Cloud delete failed: " + e);
                    }
                });
    }

    public void deleteFile(String path) {
        if (path != null && path.isEmpty() == false) {
            File f = new File(path);
            if (f.exists() == true) {
                Uri file = Uri.fromFile(f);
                StorageReference ref = audioRef.child(file.getLastPathSegment());
                ref.delete()
                        .addOnSuccessListener(new OnSuccessListener<Void>() {
                            @Override
                            public void onSuccess(Void unused) {
                                Log.d(TAG, "Cloud file deleted");
                            }
                        })
                        .addOnFailureListener(new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                Log.w(TAG, "Cloud file delete failed");
                            }
                        });
            }
        }
    }
}
