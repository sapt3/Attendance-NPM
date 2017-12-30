package com.hash.android.npmattendance;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.androidhiddencamera.CameraConfig;
import com.androidhiddencamera.CameraError;
import com.androidhiddencamera.HiddenCameraActivity;
import com.androidhiddencamera.HiddenCameraUtils;
import com.androidhiddencamera.config.CameraFacing;
import com.androidhiddencamera.config.CameraImageFormat;
import com.androidhiddencamera.config.CameraResolution;
import com.androidhiddencamera.config.CameraRotation;
import com.bumptech.glide.Glide;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.hash.android.npmattendance.model.Employee;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

import static android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
import static android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE;

public class EmployeeRecordActivity extends HiddenCameraActivity implements View.OnClickListener {

    private static final String TAG = EmployeeRecordActivity.class.getSimpleName();
    private FirebaseDatabase mDatabase = FirebaseDatabase.getInstance();
    private Employee employee;
    private String timeFormat;
    private String dateFormat;
    private Camera camera;
    private CameraConfig mCameraConfig;
    private String state = ""; //0 = unassigned, 1 = entering, 2 = exiting
    private static final String ENTERING = "entering";
    private static final String EXITING = "exiting";
    private static final String UNASSIGNED = "unassigned";
    private ProgressDialog pd;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_employee_record);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        getWindow().getDecorView().setSystemUiVisibility(SYSTEM_UI_FLAG_LAYOUT_STABLE | SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
//        changeStatusBarColor();


        ImageView profilePic = findViewById(R.id.employeeProfilePictureImageView);
        TextView date = findViewById(R.id.dateTextView);
        TextView time = findViewById(R.id.timeTextView);
        FloatingActionButton entranceFAB = findViewById(R.id.entranceAttendanceFAB);
        FloatingActionButton exitFAB = findViewById(R.id.exitAttendanceFAB);
        RecyclerView mRecyclerView = findViewById(R.id.activityRecyclerView);

        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mRecyclerView.setItemAnimator(new DefaultItemAnimator());


        Intent i = getIntent();
        employee = i.getParcelableExtra(DashboardActivity.INTENT_KEY);
        if (employee != null) {

            setTitle(employee.getName()); //Set the title bar to identify the employee

            //TODO: Application code goes here
            Glide.with(this)
                    .load(employee.getAvatar())
                    .into(profilePic); //Load the image into the profile pic Image View

            //TODO: Get the latest time and set it in the textView
            Calendar calendar = Calendar.getInstance();
            //TODO: Remove the day suffix from the date format while uploading to the database
            SimpleDateFormat format = new SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault());
            dateFormat = format.format(calendar.getTime());
            date.setText(dateFormat);
            format = new SimpleDateFormat("hh:mm aaa", Locale.getDefault());
            timeFormat = format.format(calendar.getTime());
            time.setText(timeFormat);


            entranceFAB.setOnClickListener(this);
            exitFAB.setOnClickListener(this);

            mRecyclerView.setFocusable(false);

            mCameraConfig = new CameraConfig()
                    .getBuilder(this)
                    .setCameraFacing(CameraFacing.FRONT_FACING_CAMERA)
                    .setCameraResolution(CameraResolution.LOW_RESOLUTION)
                    .setImageFormat(CameraImageFormat.FORMAT_JPEG)
                    .setImageRotation(CameraRotation.ROTATION_270)
                    .build();

            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {

                //Start camera preview
                startCamera(mCameraConfig);
            } else {
                ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.CAMERA}, 101);
            }


        } else {
            //In case the model loads with incomplete data remove the activity from the stack
            startActivity(new Intent(EmployeeRecordActivity.this, DashboardActivity.class));
            finish();
        }

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.entranceAttendanceFAB) {
            //TODO: Write to the db as follows -   "activity"-"id"-"date"-"in":time
            //TODO: Write to the db as follows -   "attendance"-"date"-"name"-"in":time
            Log.d(TAG, "onClick: Entrance");
            new AlertDialog.Builder(this)
                    .setTitle("Do you confirm?")
                    .setMessage("Confirm entrance at " + timeFormat + " today?")
                    .setPositiveButton("YES", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            mDatabase.getReference().child("activity").child(employee.getId()).child(dateFormat.split(",")[1]).child("in").setValue(timeFormat);
                            mDatabase.getReference().child("attendance").child(dateFormat.split(",")[1]).child(employee.getName()).child("in").setValue(timeFormat);
                            mDatabase.getReference().child("attendance_machine_read").child(dateFormat.split(",")[1]).child(employee.getId()).child("in").setValue(timeFormat);

                            savePicture(EmployeeRecordActivity.ENTERING);
                            Toast.makeText(EmployeeRecordActivity.this, "Action completed!", Toast.LENGTH_SHORT).show();
                            onBackPressed();
                        }
                    })
                    .setNegativeButton("NO", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            Toast.makeText(EmployeeRecordActivity.this, "Action cancelled!", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .show();


        }
        if (view.getId() == R.id.exitAttendanceFAB) {
            //TODO: Write to the db as follows -   "activity"-"id"-"date"-"out":time
            //TODO: Write to the db as follows -   "attendance"-"date"-"name"-"out":time
            Log.d(TAG, "onClick: Exit");
            new AlertDialog.Builder(this)
                    .setTitle("Do you confirm?")
                    .setMessage("Confirm exit at " + timeFormat + " today?")
                    .setPositiveButton("YES", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            mDatabase.getReference().child("activity").child(employee.getId()).child(dateFormat.split(",")[1]).child("out").setValue(timeFormat);
                            mDatabase.getReference().child("attendance").child(dateFormat.split(",")[1]).child(employee.getName()).child("out").setValue(timeFormat);
                            mDatabase.getReference().child("attendance_machine_read").child(dateFormat.split(",")[1]).child(employee.getId()).child("out").setValue(timeFormat);
                            savePicture(EmployeeRecordActivity.EXITING);
                            Toast.makeText(EmployeeRecordActivity.this, "Action completed!", Toast.LENGTH_SHORT).show();
                            onBackPressed();
                        }
                    })
                    .setNegativeButton("NO", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            Toast.makeText(EmployeeRecordActivity.this, "Action cancelled!", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .show();


        }
    }

    private void savePicture(String string) {
        switch (string) {
            case EmployeeRecordActivity.ENTERING:
                takePicture(); //NOTE: This goes to the callback onImageCapture. Deal the rest uploading there.
                state = EmployeeRecordActivity.ENTERING;
                break;

            case EmployeeRecordActivity.EXITING:
                takePicture(); //NOTE: This goes to the callback onImageCapture. Deal the rest uploading there.
                state = EmployeeRecordActivity.EXITING;
                break;
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        } else return false;
    }


    @Override
    public void onImageCapture(@NonNull File imageFile) {
        String fileName = "";

        Log.d(TAG, "onImageCapture: ");
        StorageReference storageReference;
        StorageReference rootReference = FirebaseStorage.getInstance().getReference().child("record").child(dateFormat.split(",")[1]).child(employee.getName());
        if (state.equalsIgnoreCase(EmployeeRecordActivity.ENTERING))
            storageReference = rootReference.child("in.jpg");
        else
            storageReference = rootReference.child("out.jpg");

        pd = new ProgressDialog(this);
        pd.setMessage("Uploading to server...");
        pd.show();
        Uri uri = Uri.fromFile(imageFile);
        UploadTask task = storageReference.putFile(uri);
        task.addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception exception) {
                // Handle unsuccessful uploads
            }
        }).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                Uri downloadUrl = taskSnapshot.getDownloadUrl();
                // taskSnapshot.getMetadata() contains file metadata such as size, content-type, and download URL.

                if (pd != null) {
                    pd.cancel();
                    pd.dismiss();
                }
//                new SendToDatabase().execute(state, String.valueOf(downloadUrl)); //Do in background
                if (state.equalsIgnoreCase(EmployeeRecordActivity.ENTERING)) {
                    FirebaseDatabase.getInstance().getReference().child("activity").child(employee.getId()).child(dateFormat.split(",")[1]).child("inPic").setValue(String.valueOf(downloadUrl));
                    FirebaseDatabase.getInstance().getReference().child("attendance").child(dateFormat.split(",")[1]).child(employee.getName()).child("inPic").setValue(String.valueOf(downloadUrl));

                } else {
                    FirebaseDatabase.getInstance().getReference().child("activity").child(employee.getId()).child(dateFormat.split(",")[1]).child("outPic").setValue(String.valueOf(downloadUrl));
                    FirebaseDatabase.getInstance().getReference().child("attendance").child(dateFormat.split(",")[1]).child(employee.getName()).child("outPic").setValue(String.valueOf(downloadUrl));

                }
//                startActivity(new Intent(EmployeeRecordActivity.this, DashboardActivity.class));
                //TODO: Write to the db as follows - "activity"-"id"-"date"-inPic":downloadURL
                //TODO: Write to the db as follows - "attendance"-"date"-"name"-"inPic":downloadURL

                Log.d(TAG, "onSuccess: downloadUrl::" + downloadUrl);
            }
        });
    }

    @Override
    public void onCameraError(int errorCode) {
        switch (errorCode) {
            case CameraError.ERROR_CAMERA_OPEN_FAILED:
                Toast.makeText(this, "Camera open failed", Toast.LENGTH_LONG).show();

                //Camera open failed. Probably because another application
                //is using the camera
                break;
            case CameraError.ERROR_IMAGE_WRITE_FAILED:
                Toast.makeText(this, "Image write failed", Toast.LENGTH_LONG).show();

                //Image write failed. Please check if you have provided WRITE_EXTERNAL_STORAGE permission
                break;
            case CameraError.ERROR_CAMERA_PERMISSION_NOT_AVAILABLE:
                //camera permission is not available
                //Ask for the camra permission before initializing it.
                break;
            case CameraError.ERROR_DOES_NOT_HAVE_OVERDRAW_PERMISSION:
                //Display information dialog to the user with steps to grant "Draw over other app"
                //permission for the app.
                HiddenCameraUtils.openDrawOverPermissionSetting(this);
                break;
            case CameraError.ERROR_DOES_NOT_HAVE_FRONT_CAMERA:
                Toast.makeText(this, "Your device does not have front camera.", Toast.LENGTH_LONG).show();
                break;
        }
    }

    public class SendToDatabase extends AsyncTask<String, Void, Void> {


        @Override
        protected Void doInBackground(String... strings) {
            String state = strings[0];
            String downloadURL = strings[1];

            if (state.equalsIgnoreCase(EmployeeRecordActivity.ENTERING)) {
                FirebaseDatabase.getInstance().getReference().child("activity").child(employee.getId()).child(dateFormat.split(",")[1]).child("inPic").setValue(downloadURL);
                FirebaseDatabase.getInstance().getReference().child("attendance").child(dateFormat.split(",")[1]).child(employee.getName()).child("inPic").setValue(downloadURL);
            } else {
                FirebaseDatabase.getInstance().getReference().child("activity").child(employee.getId()).child(dateFormat.split(",")[1]).child("outPic").setValue(downloadURL);
                FirebaseDatabase.getInstance().getReference().child("attendance").child(dateFormat.split(",")[1]).child(employee.getName()).child("outPic").setValue(downloadURL);
            }

            return null;
        }


    }

    @Override
    protected void onStop() {
        super.onStop();
        if (pd != null) pd.dismiss();

    }
}

