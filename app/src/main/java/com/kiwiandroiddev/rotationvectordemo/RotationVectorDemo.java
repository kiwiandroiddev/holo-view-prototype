package com.kiwiandroiddev.rotationvectordemo;

/**
 * Created by matt on 20/12/14.
 */
/*
* Copyright (C) 2007 The Android Open Source Project
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;
import android.app.Activity;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Bundle;
import android.util.FloatMath;
import android.util.Log;
import android.view.View;
import android.widget.Button;

/**
 * Wrapper activity demonstrating the use of the new
 * {@link SensorEvent#values rotation vector sensor}
 * ({@link Sensor#TYPE_ROTATION_VECTOR TYPE_ROTATION_VECTOR}).
 *
 * @see Sensor
 * @see SensorEvent
 * @see SensorManager
 *
 */
public class RotationVectorDemo extends Activity {

    // Create a constant to convert nanoseconds to seconds.
    private static final float NS2S = 1.0f / 1000000000.0f;
    private static final double DEG2RAD_FACTOR = Math.PI / 180.0f;
    private static final double RAD2DEG_FACTOR = 180.0f / Math.PI;

    private static final String TAG = "RotationVectorDemo";

    private GLSurfaceView mGLSurfaceView;
    private SensorManager mSensorManager;
    private MyRenderer mRenderer;

    private float timestamp;
    private float currentXRotRads = 0.0f;
    private float currentYRotRads = 0.0f;

    private float frustumXOffset = 0.0f;
    private float frustumYOffset = 0.0f;
    private float frustumZNear = 1.0f;

    private float screenWidthRatio = 1.0f;

    private boolean pendingViewerReset = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        mSensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
        mRenderer = new MyRenderer();

        mGLSurfaceView = (GLSurfaceView) findViewById(R.id.glsurfaceview);
        mGLSurfaceView.setRenderer(mRenderer);

        Button resetViewButton = (Button) findViewById(R.id.reset_viewer_position_button);
        resetViewButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pendingViewerReset = true;
            }
        });
    }

    @Override
    protected void onResume() {
        // Ideally a game should implement onResume() and onPause()
        // to take appropriate action when the activity loses focus
        super.onResume();
        mRenderer.start();
        mGLSurfaceView.onResume();
    }

    @Override
    protected void onPause() {
    // Ideally a game should implement onResume() and onPause()
    // to take appropriate action when the activity loses focus
        super.onPause();
        mRenderer.stop();
        mGLSurfaceView.onPause();
    }

    class MyRenderer implements GLSurfaceView.Renderer, SensorEventListener {
        private Cube mCube;
        private Sensor mRotationVectorSensor;
        private final float[] mRotationMatrix = new float[16];

        public MyRenderer() {
            // find the rotation-vector sensor
            mRotationVectorSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            mCube = new Cube();

            // initialize the rotation matrix to identity
            mRotationMatrix[ 0] = 1;
            mRotationMatrix[ 4] = 1;
            mRotationMatrix[ 8] = 1;
            mRotationMatrix[12] = 1;

//            // rotation test
//            float[] normalVector = new float[4];
//            normalVector[0] = 0;
//            normalVector[1] = 0;
//            normalVector[2] = 1;
//            normalVector[3] = 0;
//
//            Log.d(TAG, String.format("normalVector before = (%.2f, %.2f, %.2f, %.2f)",
//                            normalVector[0],
//                            normalVector[1],
//                            normalVector[2],
//                            normalVector[3]
//                            ));
//
//            Matrix.rotateM(normalVector, 0, 45.0f, 0, 1, 0);
        }

        public void start() {
            mSensorManager.registerListener(this, mRotationVectorSensor, SensorManager.SENSOR_DELAY_GAME);
//            mSensorManager.registerListener(this, mRotationVectorSensor, SensorManager.SENSOR_DELAY_UI);
        }

        public void stop() {
            mSensorManager.unregisterListener(this);
        }

        public void onSensorChanged(SensorEvent event) {
            // we received a sensor event. it is a good practice to check
            // that we received the proper event
            if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {

                if (pendingViewerReset) {
                    currentXRotRads = 0.0f;
                    currentYRotRads = 0.0f;
                    frustumXOffset = 0.0f;
                    frustumYOffset = 0.0f;
                    frustumZNear = 1.0f;
                    pendingViewerReset = false;
                }

//                Log.d(TAG, String.format("event.values = [%.2f, %.2f, %.2f]",
//                        event.values[0], event.values[1], event.values[2]));

                if (timestamp != 0) {
                    final float dT = (event.timestamp - timestamp) * NS2S;

                    float dXRads = event.values[0] * dT;
                    float dYRads = event.values[1] * dT;

                    currentXRotRads += dXRads;
                    currentYRotRads += dYRads;

                    // shorten distance from observer to projection place as it is rotated.
                    // distance from observer to center of viewport should remain constant
                    // as a consequence of translating it
                    float cosTheta = FloatMath.cos(currentYRotRads);
                    frustumZNear = cosTheta;

                    // TODO include x rotation in this calculation

                    // translate the viewport along its plane. This has the effect of distorting
                    // the view more as the device screen is rotated.
                    frustumYOffset = FloatMath.sin(currentXRotRads);
                    frustumXOffset = FloatMath.sin(currentYRotRads);

//                    Log.d(TAG, String.format("frustumZNear = %.2f, frustumXOffset = %.2f, frustumYOffset = %.2f",
//                            frustumZNear, frustumXOffset, frustumYOffset));
//
//                    Log.d(TAG, String.format("dYRads = %.2f, currentYRotRads = %.2f",
//                            dYRads, currentYRotRads));
                }

                timestamp = event.timestamp;

                // convert the rotation-vector to a 4x4 matrix. the matrix
                // is interpreted by Open GL as the inverse of the
                // rotation-vector, which is what we want.

//                SensorManager.getRotationMatrixFromVector(
//                        mRotationMatrix, event.values);
            }
        }

        public void onDrawFrame(GL10 gl) {
            gl.glMatrixMode(GL10.GL_PROJECTION);
            gl.glLoadIdentity();
            gl.glFrustumf(-screenWidthRatio + frustumXOffset,
                           screenWidthRatio + frustumXOffset,
                           -1 - frustumYOffset, 1 - frustumYOffset,
//                           -1, 1,
                           frustumZNear, frustumZNear + 100);

            // clear screen
            gl.glClear(GL10.GL_COLOR_BUFFER_BIT);
            // set-up modelview matrix
            gl.glMatrixMode(GL10.GL_MODELVIEW);
            gl.glLoadIdentity();

            gl.glPushMatrix();

//            gl.glTranslatef(0, 0, -5.0f);
//            gl.glTranslatef(0, 0, -5.0f);
//            gl.glTranslatef(0.0f, 0, -10.0f);
//            gl.glTranslatef(4.0f, 0, 0);
//            gl.glScalef(currentXScale, 1.0f, 1.0f);
//            gl.glMultMatrixf(mRotationMatrix, 0);
//            gl.glRotatef(60.0f, 0.0f, 1.0f, 0.0f);

            float rotAxisZOffset = 5.0f;
            gl.glTranslatef(0.0f, 0, rotAxisZOffset);

            gl.glRotatef((float) (currentXRotRads * RAD2DEG_FACTOR * -1.0f), 1.0f, 0.0f, 0.0f);
            gl.glRotatef((float) (currentYRotRads * RAD2DEG_FACTOR * -1.0f), 0.0f, 1.0f, 0.0f);

            gl.glTranslatef(0.0f, 0, -rotAxisZOffset);

            gl.glTranslatef(0.0f, 0, -40.0f);

            float scale = 5.0f;
            gl.glScalef(scale, scale, scale);

            gl.glPushMatrix();



            gl.glRotatef((float) (currentXRotRads * RAD2DEG_FACTOR * 1.0f), 1.0f, 0.0f, 0.0f);
            gl.glRotatef((float) (currentYRotRads * RAD2DEG_FACTOR * 1.0f), 0.0f, 1.0f, 0.0f);

            // draw our object
            gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);
            gl.glEnableClientState(GL10.GL_COLOR_ARRAY);
            mCube.draw(gl);
            gl.glPopMatrix();

            gl.glPopMatrix();
        }

        public void onSurfaceChanged(GL10 gl, int width, int height) {
            // set view-port
            gl.glViewport(0, 0, width, height);
            // set projection matrix
            screenWidthRatio = (float) width / height;
//            gl.glMatrixMode(GL10.GL_PROJECTION);
//            gl.glLoadIdentity();
//            gl.glFrustumf(-ratio, ratio, -1, 1, 1, 10);
//            Log.d(TAG, "ratio = " + ratio);
////            gl.glFrustumf(0, ratio*2, -1, 1, 1, 10);
        }

        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            // dither is enabled by default, we don't need it
            gl.glDisable(GL10.GL_DITHER);
            gl.glClearColor(0,0,0,1);
        }
        class Cube {
            // initialize our cube
            private FloatBuffer mVertexBuffer;
            private FloatBuffer mColorBuffer;
            private ByteBuffer mIndexBuffer;
            public Cube() {
                final float vertices[] = {
                        -1, -1, -1,	1, -1, -1,
                        1, 1, -1,	-1, 1, -1,
                        -1, -1, 1, 1, -1, 1,
                        1, 1, 1, -1, 1, 1,
                };
                final float colors[] = {
                        0, 0, 0, 1, 1, 0, 0, 1,
                        1, 1, 0, 1, 0, 1, 0, 1,
                        0, 0, 1, 1, 1, 0, 1, 1,
                        1, 1, 1, 1, 0, 1, 1, 1,
                };
//                final float colors[] = {
//                        0, 0, 1, 1, 0, 0, 1, 1,
//                        0, 0, 1, 1, 0, 0, 1, 1,
//                        0, 0, 1, 1, 0, 0, 1, 1,
//                        0, 0, 1, 1, 0, 0, 1, 1,
//                };
                final byte indices[] = {
                        0, 4, 5, 0, 5, 1,
                        1, 5, 6, 1, 6, 2,
                        2, 6, 7, 2, 7, 3,
                        3, 7, 4, 3, 4, 0,
                        4, 7, 6, 4, 6, 5,
                        3, 0, 1, 3, 1, 2
                };
                ByteBuffer vbb = ByteBuffer.allocateDirect(vertices.length*4);
                vbb.order(ByteOrder.nativeOrder());
                mVertexBuffer = vbb.asFloatBuffer();
                mVertexBuffer.put(vertices);
                mVertexBuffer.position(0);
                ByteBuffer cbb = ByteBuffer.allocateDirect(colors.length*4);
                cbb.order(ByteOrder.nativeOrder());
                mColorBuffer = cbb.asFloatBuffer();
                mColorBuffer.put(colors);
                mColorBuffer.position(0);
                mIndexBuffer = ByteBuffer.allocateDirect(indices.length);
                mIndexBuffer.put(indices);
                mIndexBuffer.position(0);
            }
            public void draw(GL10 gl) {
                gl.glEnable(GL10.GL_CULL_FACE);
                gl.glFrontFace(GL10.GL_CW);
                gl.glShadeModel(GL10.GL_SMOOTH);
                gl.glVertexPointer(3, GL10.GL_FLOAT, 0, mVertexBuffer);
                gl.glColorPointer(4, GL10.GL_FLOAT, 0, mColorBuffer);
                gl.glDrawElements(GL10.GL_TRIANGLES, 36, GL10.GL_UNSIGNED_BYTE, mIndexBuffer);
            }
        }
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    }
}
