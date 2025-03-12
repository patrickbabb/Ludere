package com.draco.ludere.gamepad

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import android.view.InputDevice
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.draco.ludere.R
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.radialgamepad.library.RadialGamePad
import com.swordfish.radialgamepad.library.config.RadialGamePadConfig
import com.swordfish.radialgamepad.library.event.Event
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable

class GamePad(
    context: Context,
    padConfig: RadialGamePadConfig,
    private val scope: CoroutineScope // CoroutineScope to launch coroutines
) {
    val pad = RadialGamePad(padConfig, 0f, context)

    companion object {
        fun shouldShowGamePads(activity: Activity): Boolean {
            if (!activity.resources.getBoolean(R.bool.config_gamepad))
                return false

            val hasTouchScreen = activity.packageManager?.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
            if (hasTouchScreen == null || hasTouchScreen == false)
                return false

            val currentDisplayId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                activity.display!!.displayId
            else {
                val wm = activity.getSystemService(AppCompatActivity.WINDOW_SERVICE) as WindowManager
                wm.defaultDisplay.displayId
            }

            val dm = activity.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            if (dm.getDisplay(currentDisplayId).flags and Display.FLAG_PRESENTATION == Display.FLAG_PRESENTATION)
                return false

            for (id in InputDevice.getDeviceIds()) {
                InputDevice.getDevice(id).apply {
                    if (sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD)
                        return false
                }
            }

            return true
        }
    }

    private fun eventHandler(event: Event, retroView: GLRetroView) {
        when (event) {
            is Event.Button -> retroView.sendKeyEvent(event.action, event.id)
            is Event.Direction -> when (event.id) {
                GLRetroView.MOTION_SOURCE_DPAD -> retroView.sendMotionEvent(GLRetroView.MOTION_SOURCE_DPAD, event.xAxis, event.yAxis)
                GLRetroView.MOTION_SOURCE_ANALOG_LEFT -> retroView.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_LEFT, event.xAxis, event.yAxis)
                GLRetroView.MOTION_SOURCE_ANALOG_RIGHT -> retroView.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_RIGHT, event.xAxis, event.yAxis)
            }
        }
    }

    // Modify the subscribe method to collect from Flow
    fun subscribe(compositeDisposable: CompositeDisposable, retroView: GLRetroView) {
        val inputDisposable = scope.launch {
            pad.events().collect { event ->
                eventHandler(event, retroView)
            }
        }

        compositeDisposable.add(object : Disposable {
            override fun dispose() {
                inputDisposable.cancel() // Cancel the coroutine when disposing
            }

            override fun isDisposed(): Boolean {
                return inputDisposable.isCancelled
            }
        })
    }
}
