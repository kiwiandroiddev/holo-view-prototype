package com.kiwiandroiddev.rotationvectordemo;

/**
 * Hologram effect proof-of-concept.
 * Based on RotationVectorDemo in the Android SDK samples.
 *
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
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.FloatMath;
import android.view.Menu;
import android.view.MenuItem;
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
public class HologramViewActivity extends Activity {

    // Create a constant to convert nanoseconds to seconds.
    private static final float NS2S = 1.0f / 1000000000.0f;
    private static final double DEG2RAD_FACTOR = Math.PI / 180.0f;
    private static final double RAD2DEG_FACTOR = 180.0f / Math.PI;

    private static final String TAG = "HologramViewActivity";

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

    private boolean pendingViewerPositionReset = false;
    private float mRotAxisZOffset = 0.0f;
    private float mScale = 5.0f;

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
                pendingViewerPositionReset = true;
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mRenderer.start();
        mGLSurfaceView.onResume();

        initFromPreferences();
    }

    private void initFromPreferences() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        try {
            mRotAxisZOffset = Float.parseFloat(sharedPreferences.getString(SettingsActivity.PREF_ROT_Z_OFFSET, "0.0"));
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }

        try {
            mScale = Float.parseFloat(sharedPreferences.getString(SettingsActivity.PREF_SCALE, "5.0"));
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mRenderer.stop();
        mGLSurfaceView.onPause();
    }

    class MyRenderer implements GLSurfaceView.Renderer, SensorEventListener {
        private Cube mCube;
        private Sensor mRotationVectorSensor;

        public MyRenderer() {
            // find the rotation-vector sensor
            mRotationVectorSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            mCube = new Cube();
        }

        public void start() {
            mSensorManager.registerListener(this, mRotationVectorSensor, SensorManager.SENSOR_DELAY_FASTEST);
        }

        public void stop() {
            mSensorManager.unregisterListener(this);
        }

        public void onSensorChanged(SensorEvent event) {
            // we received a sensor event. it is a good practice to check
            // that we received the proper event
            if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {

                if (pendingViewerPositionReset) {
                    currentXRotRads = 0.0f;
                    currentYRotRads = 0.0f;
                    frustumXOffset = 0.0f;
                    frustumYOffset = 0.0f;
                    frustumZNear = 1.0f;
                    pendingViewerPositionReset = false;
                }

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
                }

                timestamp = event.timestamp;
            }
        }

        public void onDrawFrame(GL10 gl) {
            gl.glMatrixMode(GL10.GL_PROJECTION);
            gl.glLoadIdentity();
            gl.glFrustumf(-screenWidthRatio + frustumXOffset,
                           screenWidthRatio + frustumXOffset,
                           -1 - frustumYOffset, 1 - frustumYOffset,
                           frustumZNear, frustumZNear + 100);

            // clear screen
            gl.glClear(GL10.GL_COLOR_BUFFER_BIT);

            // set-up modelview matrix
            gl.glMatrixMode(GL10.GL_MODELVIEW);
            gl.glLoadIdentity();

            gl.glPushMatrix();

            // apply user-defined Z offset for rotation axis
            gl.glTranslatef(0.0f, 0, mRotAxisZOffset);

            gl.glRotatef((float) (currentXRotRads * RAD2DEG_FACTOR * -1.0f), 1.0f, 0.0f, 0.0f);
            gl.glRotatef((float) (currentYRotRads * RAD2DEG_FACTOR * -1.0f), 0.0f, 1.0f, 0.0f);

            gl.glTranslatef(0.0f, 0, -mRotAxisZOffset);

            // default Z offset of 3D object from viewport
            gl.glTranslatef(0.0f, 0, -40.0f);

            gl.glScalef(mScale, mScale, mScale);

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
