package com.example.ujvirtualnavigatorv2

import android.Manifest
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.plugin.PuckBearing
import com.mapbox.maps.plugin.locationcomponent.createDefault2DPuck
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.maps.plugin.viewport.viewport

class MainActivity : ComponentActivity() {

    private lateinit var mapView: MapView
    private lateinit var switchStyleButton: AppCompatImageButton
    private lateinit var recenterButton: AppCompatImageButton

    private val styleUris = listOf(
        "mapbox://styles/slick16/cmf9xecrz003201sdgschbnwy",
        "mapbox://styles/slick16/cmf9xhlb7002m01s39w451ckj",
        "mapbox://styles/slick16/cmf9xz8kh002t01sddxebh40f"
    )
    private var currentStyleIndex = 0

    private val fallbackLocation = Point.fromLngLat(28.0805, -26.1462) // Bunting Rd
    private val fallbackZoom = 15.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        mapView = MapView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        loadStyle(styleUris[currentStyleIndex])

        // Create FABs
        switchStyleButton = createFab(R.drawable.map_styles, Color.DKGRAY)
        switchStyleButton.setOnClickListener {
            currentStyleIndex = (currentStyleIndex + 1) % styleUris.size
            loadStyle(styleUris[currentStyleIndex])
        }

        recenterButton = createFab(R.drawable.recenter_location, Color.DKGRAY)
        recenterButton.setOnClickListener { recenterToUser() }

        val container = FrameLayout(this).apply {
            addView(mapView)
            addView(switchStyleButton)
            addView(recenterButton)
        }

        setContentView(container)

        // Stack buttons properly above navigation bar
        ViewCompat.setOnApplyWindowInsetsListener(container) { _, insets ->
            val navBarHeight = insets.systemWindowInsetBottom
            val marginEnd = 24
            val fabSize = (56 * resources.displayMetrics.density).toInt() // 56dp
            val spacing = 16 // space between buttons

            // Recenter button: bottom-most button
            (recenterButton.layoutParams as FrameLayout.LayoutParams).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                bottomMargin = navBarHeight + spacing
                this.marginEnd = marginEnd
            }

            // Switch style button: stacked above recenter button
            (switchStyleButton.layoutParams as FrameLayout.LayoutParams).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                bottomMargin = navBarHeight + spacing + fabSize + spacing
                this.marginEnd = marginEnd
            }

            insets
        }

        // Request location permission
        requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    /** Create a circular FAB 56dp with shadow and icon */
    private fun createFab(iconRes: Int, bgColor: Int): AppCompatImageButton {
        val size = (56 * resources.displayMetrics.density).toInt() // 56dp in px
        return AppCompatImageButton(this).apply {
            setImageResource(iconRes)
            imageTintList = ContextCompat.getColorStateList(context, android.R.color.white)
            background = createFabBackground(bgColor)
            layoutParams = FrameLayout.LayoutParams(size, size).apply {
                gravity = Gravity.BOTTOM or Gravity.END
            }
            elevation = 8f
        }
    }

    private fun createFabBackground(color: Int): MaterialShapeDrawable {
        val shapeAppearanceModel = ShapeAppearanceModel().withCornerSize(50f)
        return MaterialShapeDrawable(shapeAppearanceModel).apply {
            setTint(color)
            paintStyle = android.graphics.Paint.Style.FILL
            shadowCompatibilityMode = MaterialShapeDrawable.SHADOW_COMPAT_MODE_ALWAYS
        }
    }

    private fun loadStyle(uri: String) {
        // Save the current camera state before loading a new style
        val currentCamera = mapView.mapboxMap.cameraState

        mapView.mapboxMap.loadStyleUri(uri) {
            // Restore the saved camera position after style loads
            mapView.mapboxMap.setCamera(
                CameraOptions.Builder()
                    .center(currentCamera.center)
                    .zoom(currentCamera.zoom)
                    .bearing(currentCamera.bearing)
                    .pitch(currentCamera.pitch)
                    .build()
            )

            enableLocationComponent()
        }
    }

    private fun enableLocationComponent() {
        val locationComponent = mapView.location
        locationComponent.updateSettings {
            enabled = true
            pulsingEnabled = true
            locationPuck = createDefault2DPuck(withBearing = true)
            puckBearing = PuckBearing.COURSE
            puckBearingEnabled = true
        }

        mapView.viewport.transitionTo(mapView.viewport.makeFollowPuckViewportState())
    }

    private fun recenterToUser() {
        val currentZoom = mapView.mapboxMap.cameraState.zoom
        mapView.viewport.transitionTo(mapView.viewport.makeFollowPuckViewportState())
        mapView.mapboxMap.setCamera(CameraOptions.Builder().zoom(currentZoom).build())
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                enableLocationComponent()
                recenterToUser()
            }
        }
}
