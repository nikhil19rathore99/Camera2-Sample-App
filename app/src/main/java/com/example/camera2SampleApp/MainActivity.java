package com.example.camera2SampleApp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Chronometer;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class MainActivity extends Activity implements View.OnClickListener {
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    private static int LIGHTOPENORCLOSE = CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH;
    private static int FLASHOPENORCLOSE = CameraMetadata.FLASH_MODE_OFF;
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }
    private ImageButton ib_takePhoto;
    private ImageButton ib_cameraFlip;
    private ImageButton iv_videoFlash;
    private ImageView iv_photos;
    private Chronometer mChronometer;
    private Spinner mEffectSpinner;
    private ImageButton btn_startRecord;
    private AutofitSurfaceView mSurfaceView;
    private SurfaceHolder mSurfaceHolder;
    private CameraManager mCameraManager;
    private Handler handler, mainHandler, handler1;
    private ImageReader mImageReader;
    private CameraCaptureSession mCameraCaptureSession;
    private CameraDevice mCameraDevice;
    private CaptureRequest.Builder previewRequestBuilder;
    private CaptureRequest.Builder picturesRequestBuilder;
    private static final int LIGHTOPEN = 0;
    private static final int LIGHTCLOSE = 1;
    private static final int FRONTORREAR=3;
    private static final int PICTURES = 2;
    private static final int MSG_SURFACE_CREATED=4;
    private static final int MSG_CAMERA_OPENED=5;
    private boolean mIsSurfaceCreated=false;
    private static String SELECTED_CAM_ID="0";
    private ImageButton mFlashControl;
    private ArrayList<String> picturesPath = new ArrayList<>();
    private String bitmapPath = null;
    private int name = 0;
    private int SENSORORIENTATION;
    private MediaRecorder mediaRecorder;
    private boolean isStartRecord = false;
    private boolean VIDEO_FLASH_STATE =false;
    private String mFileName;
    private CaptureRequest.Builder mRecordBuilder;

    @SuppressLint("HandlerLeak")
    private Handler myHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what){
                case LIGHTOPEN:
                    mFlashControl.setBackground(getDrawable(R.drawable.ic_flash_auto));
                    break;
                case LIGHTCLOSE:
                    mFlashControl.setBackground(getDrawable(R.drawable.ic_flash_off));
                    break;
                case PICTURES:
                    takePictureUtil();
                    break;
                case FRONTORREAR:
                    closeCamera();
                    if(SELECTED_CAM_ID=="0"){
                        SELECTED_CAM_ID="1";
                        ib_cameraFlip.setBackground(getDrawable(R.drawable.ic_camera_front));
                    }else if(SELECTED_CAM_ID=="1"){
                        SELECTED_CAM_ID="0";
                        ib_cameraFlip.setBackground(getDrawable(R.drawable.ic_camera_rear));
                    }
                    Log.d("************","Reopening Camera");
                    initCamera();
                    break;
                case MSG_SURFACE_CREATED:
                case MSG_CAMERA_OPENED:
                    if(mIsSurfaceCreated && mCameraDevice != null){
                        takePreview();
                    }
                    break;
            }
        }
    };
    @Override
    protected void onStart() {
        super.onStart();
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA,Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.RECORD_AUDIO}, 25);
        }else{
            initCamera();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 25) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initCamera();
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();
    }
    private void initView() {
        ib_cameraFlip = findViewById(R.id.ib_camera_flip);
        ib_takePhoto = findViewById(R.id.ib_takephoto);
        iv_photos = findViewById(R.id.iv_photos);
        iv_photos.setVisibility(View.GONE);
        mFlashControl = findViewById(R.id.ib_flash_control);
        btn_startRecord = findViewById(R.id.btn_startRecord);
        iv_videoFlash = findViewById(R.id.video_flash);
        iv_videoFlash.setVisibility(View.GONE);
        mChronometer = findViewById(R.id.video_chronometer);
        mEffectSpinner = findViewById(R.id.effect_spinner);
        List<String> spinnerItems;
        spinnerItems = new ArrayList<>(Arrays.asList("Off", "Aqua", "BlackBoard", "MonoColor", "Negative", "Posterization", "Sepia", "Solarization", "WhiteBoard"));
        ArrayAdapter<String> dataAdapter = new ArrayAdapter<String>(getApplicationContext(), android.R.layout.simple_spinner_item, spinnerItems);
        dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mEffectSpinner.setAdapter(dataAdapter);
        ib_takePhoto.setOnClickListener(this);
        mFlashControl.setOnClickListener(this);
        btn_startRecord.setOnClickListener(this);
        iv_videoFlash.setOnClickListener(this);
        ib_takePhoto.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                Toast.makeText(MainActivity.this, "Take long shots at delay", Toast.LENGTH_SHORT).show();
                takePictures();
                return true;
            }
        });
        iv_photos.setOnClickListener(this);
        ib_cameraFlip.setOnClickListener(this);
        initSurface();
    }
    public void populateSpinner(){
        mEffectSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                previewRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
                switch (i) {
                    case 0:
                        previewRequestBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE, CameraMetadata.CONTROL_EFFECT_MODE_OFF);
                        break;
                    case 1:
                        previewRequestBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE, CameraMetadata.CONTROL_EFFECT_MODE_AQUA);
                        break;
                    case 2:
                        previewRequestBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE, CameraMetadata.CONTROL_EFFECT_MODE_BLACKBOARD);
                        break;
                    case 3:
                        previewRequestBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE, CameraMetadata.CONTROL_EFFECT_MODE_MONO);
                        break;
                    case 4:
                        previewRequestBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE, CameraMetadata.CONTROL_EFFECT_MODE_NEGATIVE);
                        break;
                    case 5:
                        previewRequestBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE, CameraMetadata.CONTROL_EFFECT_MODE_POSTERIZE);
                        break;
                    case 6:
                        previewRequestBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE, CameraMetadata.CONTROL_EFFECT_MODE_SEPIA);
                        break;
                    case 7:
                        previewRequestBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE, CameraMetadata.CONTROL_EFFECT_MODE_SOLARIZE);
                        break;
                    case 8:
                        previewRequestBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE, CameraMetadata.CONTROL_EFFECT_MODE_WHITEBOARD);
                        break;
                }
                try {
                    mCameraDevice.createCaptureSession(Arrays.asList(mSurfaceHolder.getSurface(), mImageReader.getSurface()),mCameraCaptureSessionStateCallback, handler);
                  } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });
    }
    private void initSurface() {
        Log.d("********","initSurface");
        mSurfaceView = findViewById(R.id.surface_view);

        mSurfaceView.setOnClickListener(this);
        mSurfaceHolder = mSurfaceView.getHolder();
        mSurfaceHolder.setKeepScreenOn(true);
        mSurfaceHolder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                mIsSurfaceCreated=true;
                populateSpinner();
                myHandler.sendEmptyMessage(MSG_SURFACE_CREATED);
            }
            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

            }
            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                mIsSurfaceCreated = false;
            }
        });
    }
    // just getting 10 shots on 100ms delay //for testing purpose
    private void takePictures() {
        for (int i = 0; i < 10; i++) {
            myHandler.sendEmptyMessageDelayed(PICTURES, 100);
        }
    }

    private void takePictureUtil() {
        try {
            picturesRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            picturesRequestBuilder.addTarget(mImageReader.getSurface());
            picturesRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            picturesRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, LIGHTOPENORCLOSE);
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            picturesRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));
            CaptureRequest mCaptureRequest = picturesRequestBuilder.build();
            mCameraCaptureSession.capture(mCaptureRequest, null, handler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.surface_view:
                Log.d("********","Surface Click");
                previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_STATE_FOCUSED_LOCKED);
                break;
            case R.id.ib_takephoto:
                Log.d("***********","Take photo Button Click");
                takePicture();
                break;
            case R.id.iv_photos:
                showPictures();
                break;
            case R.id.ib_flash_control:
                Toast.makeText(MainActivity.this, "flash Settings Changed", Toast.LENGTH_SHORT).show();
                if (LIGHTOPENORCLOSE == CaptureRequest.CONTROL_AE_MODE_OFF) {
                    LIGHTOPENORCLOSE = CameraMetadata.CONTROL_AE_MODE_ON_AUTO_FLASH;
                    myHandler.sendEmptyMessage(LIGHTOPEN);
                }else {
                    LIGHTOPENORCLOSE = CaptureRequest.CONTROL_AE_MODE_OFF;
                    myHandler.sendEmptyMessage(LIGHTCLOSE);
                }
                break;
            case R.id.ib_camera_flip:
                myHandler.sendEmptyMessage(FRONTORREAR);
                break;
            case R.id.btn_startRecord:
                if (isStartRecord) {
                    stopRecordingUtilforButton();
                } else{
                    Toast.makeText(MainActivity.this, "Start Recording", Toast.LENGTH_SHORT).show();
                    mChronometer.setVisibility(View.VISIBLE);
                    mChronometer.setBase(SystemClock.elapsedRealtime());
                    mChronometer.start();
                    btn_startRecord.setBackground(getDrawable(R.drawable.ic_video_start));
                    startRecord();
                    iv_videoFlash.setVisibility(View.VISIBLE);
                    mEffectSpinner.setSelection(0);
                    isStartRecord = true;
                    mEffectSpinner.setVisibility(View.GONE);
                    ib_takePhoto.setVisibility(View.GONE);
                    ib_cameraFlip.setVisibility(View.GONE);
                    mFlashControl.setVisibility(View.GONE);
                }
                break;
            case R.id.video_flash:
                if(!VIDEO_FLASH_STATE){
                    VIDEO_FLASH_STATE =true;
                    FLASHOPENORCLOSE=CameraMetadata.FLASH_MODE_TORCH;
                    iv_videoFlash.setBackground(getDrawable(R.drawable.ic_flash_on));
                }else{
                    VIDEO_FLASH_STATE =false;
                    FLASHOPENORCLOSE=CameraMetadata.FLASH_MODE_OFF;
                    iv_videoFlash.setBackground(getDrawable(R.drawable.ic_flash_off));
                }
                updatePreview();
                break;
        }
    }

    public void stopRecordingUtilforButton(){
        Toast.makeText(MainActivity.this, "Stop Recording" + mFileName, Toast.LENGTH_SHORT).show();
        mChronometer.stop();
        btn_startRecord.setBackground(getDrawable(R.drawable.ic_video_record));
        stopRecord();
        mChronometer.setVisibility(View.GONE);
        isStartRecord = false;
        mEffectSpinner.setVisibility(View.VISIBLE);
        iv_videoFlash.setVisibility(View.GONE);
        iv_photos.setVisibility(View.VISIBLE);
        mEffectSpinner.setSelection(0);
        ib_takePhoto.setVisibility(View.VISIBLE);
        ib_cameraFlip.setVisibility(View.VISIBLE);
        mFlashControl.setVisibility(View.VISIBLE);
    }
    private void showPictures() {
        Intent intent = new Intent();
        IntentFilter intentFilter = new IntentFilter();
        intent.putStringArrayListExtra("pictures", this.picturesPath);
        intent.setClass(MainActivity.this, GalleryActivity.class);
        startActivity(intent);
    }
    public void saveBitmap(String BitmapPath, Bitmap mBitmap) {
        File f = new File(BitmapPath);
        try {
            f.createNewFile();
        } catch (IOException e) {
            Log.d("*******","Save Bitmap");
        }
        FileOutputStream fOut = null;
        try {
            fOut = new FileOutputStream(f);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        mBitmap.compress(Bitmap.CompressFormat.JPEG, 100, fOut);
        try {
            fOut.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            fOut.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            mCameraDevice = camera;
            myHandler.sendEmptyMessage(MSG_CAMERA_OPENED);
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            Log.d("*********","Camera Disconnected");
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            Log.d("*********","Camera Disconnected");
        }
    };
    private CameraCaptureSession.StateCallback mCameraCaptureSessionStateCallback = new CameraCaptureSession.StateCallback()  {
        @Override
        public void onConfigured(CameraCaptureSession cameraCaptureSession) {
            if (null == mCameraDevice) {
                Log.d("**********", "Camera Device null");
                return;
            }
            mCameraCaptureSession = cameraCaptureSession;
            try {
                previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, LIGHTOPENORCLOSE);
                mCameraCaptureSession.setRepeatingRequest(previewRequestBuilder.build(), null, handler1);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
            Log.d("**********","Camera Configured Failed");
        }
    };

    @SuppressLint("MissingPermission")
    @RequiresApi(api = Build.VERSION_CODES.N)
    private void initCamera() {
        Log.d("*********", "initCamera");
        HandlerThread handlerThread = new HandlerThread("Camera2");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
        handler1 = new Handler(handlerThread.getLooper());
        mainHandler = new Handler(getMainLooper());
        mImageReader = ImageReader.newInstance(1080, 1920, ImageFormat.JPEG, 30);
        mImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                Image image = reader.acquireLatestImage();
                ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
                Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                if (bitmap != null) {
                     bitmapPath = "/sdcard/" +"IMG_"+ name + ".jpg";
                    name++;
                    Matrix m = new Matrix();
                    m.setRotate((float) SENSORORIENTATION, bitmap.getWidth(), bitmap.getHeight());
                    Bitmap rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), m, true);
                        iv_photos.setVisibility(View.VISIBLE);
                        iv_photos.setImageBitmap(rotatedBitmap);
                        saveBitmap(bitmapPath, rotatedBitmap);
                    picturesPath.add(bitmapPath);
                }
            }
        }, mainHandler);

        mCameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
//            populateCameraResSpinner(SELECTED_CAM_ID);
            mCameraManager.openCamera(SELECTED_CAM_ID, stateCallback, mainHandler);
            CameraCharacteristics cameraCharacteristics = mCameraManager.getCameraCharacteristics(SELECTED_CAM_ID);
            SENSORORIENTATION = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    public void closeCamera(){
        mCameraCaptureSession.close();
        if (null != mCameraDevice) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
    }

    @SuppressLint("NewApi")
    private void takePreview() {
        Log.d("********","take preview");
        try {
            previewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewRequestBuilder.addTarget(mSurfaceHolder.getSurface());
            mCameraDevice.createCaptureSession(Arrays.asList(mSurfaceHolder.getSurface(), mImageReader.getSurface()),mCameraCaptureSessionStateCallback, handler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    private void takePicture() {
        if (mCameraDevice == null) return;
        final CaptureRequest.Builder captureRequestBuilder;
        try {
            captureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureRequestBuilder.addTarget(mImageReader.getSurface());
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, LIGHTOPENORCLOSE);
            captureRequestBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE, previewRequestBuilder.get(CaptureRequest.CONTROL_EFFECT_MODE));
            captureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(SENSORORIENTATION));
            CaptureRequest mCaptureRequest = captureRequestBuilder.build();
            mCameraCaptureSession.capture(mCaptureRequest, null, handler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        );
    }
    private void closePreviewSession() {
        if (mCameraCaptureSession != null) {
            try {
                mCameraCaptureSession.stopRepeating();
                mCameraCaptureSession.abortCaptures();
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
            mCameraCaptureSession.close();
            mCameraCaptureSession = null;
        }
    }
    private void stopRecord() {
        try{
            mediaRecorder.stop();
            mediaRecorder.release();
            mCameraCaptureSession.close();
            mCameraDevice.close();
        }catch(RuntimeException stopException) {
            // handle cleanup here
        }
        initCamera();
    }
    private void setUpMediaRecorder() throws IOException {
        final Activity activity = this;
        if (null == activity) {
            return;
        }
        mediaRecorder = new MediaRecorder();
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmssmm").format(new Date());
        mFileName = "/sdcard/" + timeStamp + ".mp4";
        mediaRecorder.setOutputFile(mFileName);
        mediaRecorder.setVideoEncodingBitRate(10000000);
        mediaRecorder.setAudioSamplingRate(16000);
        mediaRecorder.setVideoFrameRate(30);
        mediaRecorder.setVideoSize(1920, 1080);
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mediaRecorder.setOrientationHint(ORIENTATIONS.get(SENSORORIENTATION));
        mediaRecorder.prepare();
    }
    private void startRecordingVideo() {
        try {
            mRecordBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        List<Surface> surfaces = new ArrayList<>();
        surfaces.add(mSurfaceHolder.getSurface());
        mRecordBuilder.addTarget(mSurfaceHolder.getSurface());
        Surface recorderSurface = mediaRecorder.getSurface();
        if (recorderSurface == null) {
            Log.d("******", "startRecordingVideo: null");
        }
        surfaces.add(recorderSurface);
        mRecordBuilder.addTarget(recorderSurface);
        try {
            mCameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {

                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    mCameraCaptureSession = cameraCaptureSession;
                    updatePreview();
                    mediaRecorder.start();
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                }
            }, handler1);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    private void updatePreview() {
        if (null == mCameraDevice) {
            return;
        }
        try {
            setUpCaptureRequestBuilder(mRecordBuilder);
            HandlerThread thread = new HandlerThread("CameraPreview");
            thread.start();
            mCameraCaptureSession.setRepeatingRequest(mRecordBuilder.build(), null, handler1);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    private void setUpCaptureRequestBuilder(CaptureRequest.Builder builder) {
        builder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_VIDEO);
        builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        builder.set(CaptureRequest.FLASH_MODE, FLASHOPENORCLOSE);
    }
    private void startRecord() {
        closePreviewSession();
        try {
            setUpMediaRecorder();
            startRecordingVideo();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if(isStartRecord){
          stopRecordingUtilforButton();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        myHandler.removeCallbacksAndMessages(null);
        closeCamera();
        if(handler1 != null) {
            handler1.removeCallbacksAndMessages(null);
        }
        if(mainHandler != null) {
            mainHandler.removeCallbacksAndMessages(null);
        }
        mSurfaceView = null;
        mSurfaceHolder = null;
        if (mediaRecorder != null) {
            mediaRecorder.stop();
            mediaRecorder.reset();
            mediaRecorder.release();
            mediaRecorder = null;
        }
    }
}
