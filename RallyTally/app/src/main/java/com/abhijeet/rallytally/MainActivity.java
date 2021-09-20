package com.abhijeet.rallytally;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;

import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements CvCameraViewListener2,
        OnClickListener, CompoundButton.OnCheckedChangeListener, View.OnTouchListener {
    private JavaCameraViewEx mOpenCvCameraView;
    private static final int PERMISSION_REQUEST_CODE = 1253;
    private static final int AVERAGE_COUNT = 10;

    private ExtendedSpinnerHelper mExtSpinnerMinRadius;
    private ExtendedSpinnerHelper mExtSpinnerMaxRadius;
    private ExtendedSpinnerHelper mExtSpinnerMinDistance;

    private PlusMinusCounterHelper mPlusMinusWrongCirles;
    private PlusMinusCounterHelper mPlusMinusUncountedCirles;

    private CheckBox mCheckFlash;
    private Button mBtnResetFocus;
    private Button mBtnSnapShot;

    private Mat mMatRGBASnap = null;
    private boolean mSnapShotMode = false;

    private LinkedList<Integer> mCounterCircle = new LinkedList<>();
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        CheckPermissionsOrRun();
    }

    void ShowWindowContent() {
        setContentView(R.layout.activity_main);
        mExtSpinnerMinRadius = new ExtendedSpinnerHelper(findViewById(R.id.includeMinRadius), R.string.minRaduis, 1, 50);
        mExtSpinnerMaxRadius = new ExtendedSpinnerHelper(findViewById(R.id.includeMaxRadius), R.string.maxRaduis, 30,100);
        mExtSpinnerMinDistance = new ExtendedSpinnerHelper(findViewById(R.id.includeMinDistance), R.string.minDistance, 1, 100);

        mPlusMinusWrongCirles=new PlusMinusCounterHelper(findViewById(R.id.includeWrongItems),R.string.wrongCircles,0,0,100);
        mPlusMinusUncountedCirles=new PlusMinusCounterHelper(findViewById(R.id.includeUncountedItems),R.string.uncountedItems,0,0,100);

        mCheckFlash = (CheckBox) findViewById(R.id.checkFlash);

        mBtnResetFocus= (Button) findViewById(R.id.btnResetFocus);
        mBtnSnapShot = (Button) findViewById(R.id.buttonSnapshot);

        SharedPreferences cPrefs = getSharedPreferences("common", MODE_PRIVATE);
        mExtSpinnerMinRadius.setValue(cPrefs.getInt("minRadius", mExtSpinnerMinRadius.getValue()));
        mExtSpinnerMaxRadius.setValue(cPrefs.getInt("maxRadius", mExtSpinnerMaxRadius.getValue()));
        mExtSpinnerMinDistance.setValue(cPrefs.getInt("minDistance", mExtSpinnerMinDistance.getValue()));

        showCounts.run();
        mBtnSnapShot.setOnClickListener(this);
        mBtnResetFocus.setOnClickListener(this);
        mCheckFlash.setOnCheckedChangeListener(this);
        mOpenCvCameraView = (JavaCameraViewEx) findViewById(R.id.view);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setOnTouchListener(this);
        mOpenCvCameraView.setCvCameraViewListener(this);
        mOpenCvCameraView.setPadding(0, 0, 0, 0);
        OpenCVLoader.initDebug();
        mOpenCvCameraView.setMaxFrameSize(1280, 720);
        mOpenCvCameraView.enableView();
    }

    private void CheckPermissionsOrRun() {
        ArrayList<String> strArNeedPermission = new ArrayList<String>();
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            strArNeedPermission.add(Manifest.permission.CAMERA);
        if (strArNeedPermission.size() > 0) {

            requestPermission(strArNeedPermission);
        } else {
            ShowWindowContent();
        }
    }

    public void requestPermission(ArrayList<String> strArNeedPermission) {
        String[] strArList = new String[strArNeedPermission.size()];
        strArNeedPermission.toArray(strArList);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(this, strArList, PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_CODE && grantResults.length > 0) {
            boolean bCamera = ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
            for (int n = 0; n < permissions.length; n++) {
                if (grantResults[n] == PackageManager.PERMISSION_GRANTED) {
                    if (permissions[n].equals(Manifest.permission.CAMERA))
                        bCamera = true;
                }
            }
            if (bCamera) {
                ShowWindowContent();
            } else {
                finish();
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    public void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
        SharedPreferences.Editor cPrefs = getSharedPreferences("common", MODE_PRIVATE).edit();
        cPrefs.putInt("minRadius", mExtSpinnerMinRadius.getValue());
        cPrefs.putInt("maxRadius", mExtSpinnerMaxRadius.getValue());
        cPrefs.putInt("minDistance", mExtSpinnerMinDistance.getValue());
        cPrefs.commit();
    }

    public void onCameraViewStarted(int width, int height) {
        mOpenCvCameraView.setFpsRange();
    }

    public void onCameraViewStopped() {
    }

    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
        Log.e("TubeCounter", "frame income");
        if (!mSnapShotMode || mMatRGBASnap == null) {
            mMatRGBASnap = inputFrame.rgba();
        }
        return makeCirclesOnFrame(mMatRGBASnap.clone());
    }

    private double distance(double dX1,double dY1, double dX2, double dY2){
        return Math.sqrt(Math.pow(dX1-dX2,2.0d)+Math.pow(dY1-dY2,2.0d));
    }

    private LinkedList<double[]>sortListAsSumDistanceToOthers(LinkedList<double[]>circleList){
        LinkedList<double[]>circleListDistance=new LinkedList<>();
        for(int n=0;n<circleList.size();n++) {
            double[] circle=circleList.get(n);
            double[] extCircle=new double[4];
            extCircle[0]=circle[0];
            extCircle[1]=circle[1];
            extCircle[2]=circle[2];
            extCircle[3]=0.0d;
            circleListDistance.add(extCircle);
            for(int m=0;m<circleList.size();m++){
                if(m==n)
                    continue;
                double[] otherCircle=circleList.get(m);
                extCircle[3]+=distance(circle[0],circle[1],otherCircle[0],otherCircle[1]);
            }
        }
        Collections.sort(circleListDistance, new Comparator<double[]>() {
            @Override
            public int compare(double[] o1, double[] o2) {
                return -Double.compare(o1[3],o2[3]);
            }
        });
        return circleListDistance;
    }

    private  LinkedList <double[]> makeNestingCircles(Mat matCircles){
        LinkedList <double[]>circleList=new LinkedList <double[]>();
        for (int x = 0; x < matCircles.cols(); x++) {
            circleList.add(matCircles.get(0, x));
        }
        Collections.sort(circleList, new Comparator<double[]>() {
            @Override
            public int compare(double[] o1, double[] o2) {
                return -Double.compare(o1[2],o2[2]);
            }
        });
        for(int n=0;n<circleList.size();n++){
            double[] circleBig=circleList.get(n);
            for(int m=n+1;m<circleList.size();m++){
                double[] circleSmall=circleList.get(m);
                double distanceRadius=distance(circleBig[0],circleBig[1],circleSmall[0],circleSmall[1]);
                if(distanceRadius<circleBig[2]+0.9d*circleSmall[2]) {
                    circleList.remove(m);
                    m--;
                }
            }
        }
        return circleList;
    }

    private Mat makeCirclesOnFrame(Mat rgbaFrame) {
        Mat grayFrame = new Mat();
        Imgproc.cvtColor(rgbaFrame, grayFrame, Imgproc.COLOR_RGBA2GRAY);
        Imgproc.medianBlur(grayFrame, grayFrame, 5);
        Mat circles = new Mat();
        double minDistance = mExtSpinnerMinDistance.getValue();
        int minRadius = mExtSpinnerMinRadius.getValue();
        int maxRadius = mExtSpinnerMaxRadius.getValue();
        Imgproc.HoughCircles(grayFrame, circles, Imgproc.HOUGH_GRADIENT, 1.0,
                minDistance,
                100.0, 30.0, minRadius, maxRadius);

        LinkedList <double[]>circleList=makeNestingCircles(circles);
        circleList=sortListAsSumDistanceToOthers(circleList);
        for(int n=mPlusMinusWrongCirles.getCount();n<circleList.size();n++){
            double[] c = circleList.get(n);
            Point center = new Point(Math.round(c[0]), Math.round(c[1]));

            int radius = (int) Math.round(c[2]);
            Imgproc.circle(rgbaFrame, center, radius, new Scalar(255, 0, 255), 2, 8, 0);
        }

            mCounterCircle.add(circleList.size());
            while (mCounterCircle.size() > AVERAGE_COUNT)
                mCounterCircle.remove(0);

        runOnUiThread(showCounts);
        return rgbaFrame;
    }

    private Runnable showCounts = new Runnable() {
        @Override
        public void run() {
            float fCount = 0.0f;

            for (int n = 0; n < mCounterCircle.size(); n++) {
                fCount += mCounterCircle.get(n);
                Log.d("Hi Tdqdashere", String.valueOf(fCount));

            }
            Log.d("Hi Theasasre", String.valueOf(mCounterCircle.size()));
            if (mCounterCircle.size() > 0)
                fCount /= mCounterCircle.size();
            int nCount = Math.max(0, (int) (fCount + 0.5f) + mPlusMinusUncountedCirles.getCount() - mPlusMinusWrongCirles.getCount());
            setTitle(String.format(Locale.US, "%s - %d %s", getString(R.string.app_name), nCount, getString(R.string.items)));
            Log.d("Hi There", String.valueOf(fCount));
        }
    };

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.buttonSnapshot:
                mSnapShotMode = !mSnapShotMode;
                mBtnSnapShot.setText(mSnapShotMode ? R.string.continuePreview : R.string.takeSnapShot);
                mCheckFlash.setEnabled(!mSnapShotMode);
                break;
            case R.id.btnResetFocus:
                if(!mSnapShotMode)
                    mOpenCvCameraView.resetFocus();
                break;
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        switch (buttonView.getId()) {
            case R.id.checkFlash:
                if (!mSnapShotMode)
                    mOpenCvCameraView.turnFlash(isChecked);
                break;
        }
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (v.getId()==R.id.view){
            if (event.getAction() == MotionEvent.ACTION_DOWN&&!mSnapShotMode) {
                mOpenCvCameraView.focusOnTouch(event);
            }
            return true;
        }
        return false;
    }


}
