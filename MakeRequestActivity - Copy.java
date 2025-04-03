package com.rishabh.bloodbank.Activities;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentUris;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.preference.PreferenceManager;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.androidnetworking.AndroidNetworking;
import com.androidnetworking.common.Priority;
import com.androidnetworking.error.ANError;
import com.androidnetworking.interfaces.StringRequestListener;
import com.androidnetworking.interfaces.UploadProgressListener;
import com.bumptech.glide.Glide;
import com.rishabh.bloodbank.R;
import com.rishabh.bloodbank.Utils.Endpoints;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;

public class MakeRequestActivity extends AppCompatActivity {

  private static final int REQUEST_READ_EXTERNAL_STORAGE = 401;
  private static final int REQUEST_PICK_IMAGE = 101;

  private EditText messageText;
  private TextView chooseImageText;
  private ImageView postImage;
  private Button submit_button;
  private Uri imageUri;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_make_request);

    AndroidNetworking.initialize(getApplicationContext());

    messageText = findViewById(R.id.message);
    chooseImageText = findViewById(R.id.choose_text);
    postImage = findViewById(R.id.post_image);
    submit_button = findViewById(R.id.submit_button);

    submit_button.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View view) {
        if (isValid()) {
          // Code to upload this post
          uploadRequest(messageText.getText().toString());
        }
      }
    });

    chooseImageText.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View view) {
        // Code to pick an image
        permission();
      }
    });
  }

  private void pickImage() {
    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
    intent.setType("image/*");
    startActivityForResult(intent, REQUEST_PICK_IMAGE);
  }

  private void permission() {
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) {
      // Permission not granted, request it
      ActivityCompat.requestPermissions(this,
              new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
              REQUEST_READ_EXTERNAL_STORAGE);
    } else {
      // Permission already granted
      pickImage();
    }
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                         @NonNull int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    if (requestCode == REQUEST_READ_EXTERNAL_STORAGE) {
      if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
        // Permission granted
        pickImage();
      } else {
        // Permission denied
        showMessage("Permission Declined");
      }
    }
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode == REQUEST_PICK_IMAGE && resultCode == RESULT_OK) {
      if (data != null) {
        imageUri = data.getData();
        Glide.with(getApplicationContext()).load(imageUri).into(postImage);
      }
    }
  }

  private boolean isValid() {
    String message = messageText.getText().toString().trim();

    if (message.isEmpty()) {
      showMessage("Please enter a message");
      return false;
    } else if (imageUri == null) {
      showMessage("Please choose an image");
      return false;
    }

    return true;
  }

  private void uploadRequest(String message) {
    String path = getPath(imageUri);
    if (path == null) {
      showMessage("Could not find the file path");
      return;
    }

    File file = new File(path);
    String number = PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
            .getString("number", "12345");
    AndroidNetworking.upload(Endpoints.upload_request)
            .addMultipartFile("file", file)
            .addMultipartParameter("message", message)
            .addMultipartParameter("number", number)
            .setPriority(Priority.HIGH)
            .build()
            .setUploadProgressListener(new UploadProgressListener() {
              @Override
              public void onProgress(long bytesUploaded, long totalBytes) {
                long progress = (bytesUploaded * 100 / totalBytes);
                showMessage("Uploading: " + progress + "%");
              }
            })
            .getAsString(new StringRequestListener() {
              @Override
              public void onResponse(String response) {
                if (response.startsWith("<br")) {
                  showMessage("Request Upload Failed");
                  return;
                }

                try {
                  JSONObject jsonObject = new JSONObject(response);
                  boolean success = jsonObject.getBoolean("success");
                  if (success) {
                    showMessage(jsonObject.getString("message"));
                    finish();
                  } else {
                    showMessage("Request Upload Failed");
                  }
                } catch (JSONException e) {
                  showMessage("Invalid response from the server");
                }
              }

              @Override
              public void onError(ANError anError) {
                showMessage("Error: " + anError.getErrorBody());
              }
            });
  }

  private void showMessage(String message) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
  }

  @SuppressLint("NewApi")
  private String getPath(Uri uri) {
    String filePath = null;
    String wholeID = null;
    try {
      if (DocumentsContract.isDocumentUri(this, uri)) {
        // DocumentProvider
        wholeID = DocumentsContract.getDocumentId(uri);
        String[] split = wholeID.split(":");
        String id = split[1];

        String[] column = {MediaStore.Images.Media.DATA};

        // where id is equal to
        String sel = MediaStore.Images.Media._ID + "=?";

        Cursor cursor = getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                column, sel, new String[]{id}, null);

        int columnIndex = cursor.getColumnIndex(column[0]);

        if (cursor.moveToFirst()) {
          filePath = cursor.getString(columnIndex);
        }

        cursor.close();
      } else {
        // MediaStore (and general)
        String[] projection = {MediaStore.Images.Media.DATA};
        Cursor cursor = getContentResolver().query(uri, projection, null, null, null);
        if (cursor != null) {
          int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
          if (cursor.moveToFirst()) {
            filePath = cursor.getString(column_index);
          }
          cursor.close();
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }

    return filePath;
  }
}
