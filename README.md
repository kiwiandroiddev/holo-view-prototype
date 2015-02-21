# Hologram effect prototype for Android

![Nexus 5 demo video](/../screenshots/holo2.gif?raw=true "Nexus 5 demo video")

This is a proof-of-concept for a hologram effect. The idea is to give the illusion of a 3D cube that appears to pop-out from the screen as the user rotates their device.

This is achieved by both distorting the projection and rotating the 3D model proportionately to the rotation of the device as reported by the gyroscope sensor.

It has some shortcomings:
* The effect of a real 3D object isn't convincing with both eyes open since both eyes are still seeing the same 2D image at close range, regardless of their different perspective. I.e. The illusion is more convinving with one eye
* The rotation and distortion of the scene isn't quite in sync with the actual device's rotation, even with the gryoscope sensor's reporting frequency set to `SENSOR_DELAY_FASTEST`. There is still a small yet perceivable lag. This could possibly be addressed by some sort of compensatory algorithm. E.g. when rotation is first reported, assume the device has already been rotating in that direction for X milliseconds and adjust accordingly.
 
Based on RotationVectorDemo in the Android SDK samples.

# Todo
* Automatically reset the observer's position after a few seconds of inactivity, animating the transition
* Add a rotation factor to user settings
* Make into Android live wallpaper
 
# Building

Import the top-level build.gradle with Android Studio

# Contributions
...are more than welcome, send me a pull request :)

