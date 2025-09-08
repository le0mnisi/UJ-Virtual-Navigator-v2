package com.example.ujvirtualnavigatorv2

import android.Manifest
import android.graphics.Color
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.updatePadding
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import com.mapbox.common.location.*
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import com.mapbox.maps.plugin.animation.camera
import com.mapbox.maps.plugin.locationcomponent.createDefault2DPuck
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.maps.plugin.PuckBearing
import com.mapbox.maps.plugin.viewport.viewport

class MainActivity : ComponentActivity() {

    private lateinit var mapView: MapView
    private lateinit var requestPermissionsLauncher: ActivityResultLauncher<Array<String>>

    private lateinit var locationService: LocationService
    private var locationProvider: DeviceLocationProvider? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Draw behind system bars
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Dark status & navigation bars
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        // Root layout
        val rootLayout = ConstraintLayout(this).apply { id = View.generateViewId() }

        // Initialize MapView
        mapView = MapView(this).apply { id = View.generateViewId() }

        // Apply system bar padding
        ViewCompat.setOnApplyWindowInsetsListener(mapView) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = systemBars.top, bottom = systemBars.bottom)
            insets
        }

        rootLayout.addView(
            mapView,
            ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                ConstraintLayout.LayoutParams.MATCH_PARENT
            )
        )

        // Circular Locate Me button
        val locateButton = ImageButton(this).apply {
            id = View.generateViewId()
            setImageResource(android.R.drawable.ic_menu_mylocation)
            background = MaterialShapeDrawable(
                ShapeAppearanceModel.builder()
                    .setAllCornerSizes(100f)
                    .build()
            ).apply {
                setTint(Color.parseColor("#80000000"))
            }
            setOnClickListener { moveCameraToUser() }
            elevation = 10f
        }

        val buttonSize = 150
        rootLayout.addView(
            locateButton,
            ConstraintLayout.LayoutParams(buttonSize, buttonSize)
        )

        // Constraint: bottom-right above navigation bar
        ConstraintSet().apply {
            clone(rootLayout)
            connect(locateButton.id, ConstraintSet.END, rootLayout.id, ConstraintSet.END, 32)
            connect(locateButton.id, ConstraintSet.BOTTOM, rootLayout.id, ConstraintSet.BOTTOM, 200)
            applyTo(rootLayout)
        }

        setContentView(rootLayout)

        // Request location permissions
        requestPermissionsLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val granted = permissions.values.all { it }
            if (granted) {
                Toast.makeText(this, "Location permissions granted!", Toast.LENGTH_SHORT).show()
                setupLocationService()
            } else {
                Toast.makeText(this, "Location permissions denied!", Toast.LENGTH_SHORT).show()
            }
        }

        val requiredPermissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        requestPermissionsLauncher.launch(requiredPermissions)

        // Load custom style properly
        mapView.getMapboxMap().loadStyleUri(
            "mapbox://styles/slick16/cmf9xecrz003201sdgschbnwy"
        ) { style ->
            Log.d("UJNavigator", "Custom style loaded successfully")
            // Safe to enable location component and set camera after style loaded
            enableLocationComponent()
            mapView.getMapboxMap().setCamera(
                CameraOptions.Builder()
                    .center(Point.fromLngLat(-98.0, 39.5))
                    .zoom(2.0)
                    .build()
            )
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
        mapView.viewport.transitionTo(
            mapView.viewport.makeFollowPuckViewportState()
        )
    }

    private fun setupLocationService() {
        locationService = LocationServiceFactory.getOrCreate()
        val request = LocationProviderRequest.Builder()
            .interval(
                IntervalSettings.Builder()
                    .interval(1000L)
                    .minimumInterval(500L)
                    .maximumInterval(2000L)
                    .build()
            )
            .displacement(0f)
            .accuracy(AccuracyLevel.HIGHEST)
            .build()

        val result = locationService.getDeviceLocationProvider(request)
        if (result.isValue) {
            locationProvider = result.value!!

            val locationObserver = LocationObserver { locations ->
                if (locations.isNotEmpty()) {
                    val loc = locations.last()
                    Log.d("UJNavigator", "Location update: ${loc.latitude}, ${loc.longitude}")
                }
            }

            locationProvider?.addLocationObserver(locationObserver, Looper.getMainLooper())
        } else {
            Log.e("UJNavigator", "Failed to get device location provider")
        }
    }

    private fun moveCameraToUser() {
        locationProvider?.getLastLocation { loc ->
            loc?.let {
                mapView.camera.flyTo(
                    CameraOptions.Builder()
                        .center(Point.fromLngLat(it.longitude, it.latitude))
                        .zoom(16.0)
                        .build(),
                    MapAnimationOptions.Builder()
                        .duration(1500)
                        .build()
                )
            } ?: Toast.makeText(this, "User location not available", Toast.LENGTH_SHORT).show()
        }
    }
}
