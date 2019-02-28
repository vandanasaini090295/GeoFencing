package com.example.avon.geofencingapp.UI

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import com.example.avon.geofencingapp.R
import com.example.avon.geofencingapp.Services.GeofenceTransitionService
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.location.*
import com.google.android.gms.maps.*
import com.google.android.gms.maps.MapFragment
import com.google.android.gms.maps.model.*
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.tasks.OnSuccessListener

/**
 * activity class for all the geofence creation purposes
 */
class GeofenceActivity : AppCompatActivity(),
        OnMapReadyCallback,
        GoogleMap.OnMapClickListener,
        GoogleMap.OnMarkerClickListener,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener{

    private val TAG = GeofenceActivity::class.java.simpleName
    private lateinit var mLatText: TextView
    private lateinit var mLongText: TextView
    private lateinit var mapFragment: MapFragment
    private var map : GoogleMap? = null
    private var googleApiClient : GoogleApiClient? = null
    private var lastLocation : Location? = null
    private val REQUEST_PERMISSION = 49
    private lateinit var locationRequest: LocationRequest
    private val UPDATE_INTERVAL = 1000L
    private val FASTEST_INTERVAL = 900L
    private var geofenceMarker : Marker? = null
    private var locationMarker :Marker? = null
    private val GEOFENCE_REQ_ID = "My Geofence"
    private val GEO_DURATION = 60 * 60 * 1000L
    private val GEOFENCE_REQ_CODE = 0
    private var geofenceLimits : Circle? = null
    private val GEOFENCE_RADIUS_DOUBLE = 500.0        //in metres
    private val GEOFENCE_RADIUS_FLOAT = 500.0f        //in metres
    private val KEY_GEOFENCE_LAT = "GEOFENCE LATITUDE"
    private val KEY_GEOFENCE_LONG = "GEOFENCE LONGITUDE"
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_geofence)

        mLatText = findViewById(R.id.lat) as TextView
        mLongText = findViewById(R.id.longitude) as TextView

        //initialize googlemaps
        initGMaps()
        // create GoogleApiClient
        createGoogleApi()
    }

    // creating GoogleApiClient instance
    private fun createGoogleApi() {
        Log.d(TAG, getString(R.string.createGoogleApi))
        if(null == googleApiClient){
            googleApiClient = GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build()
        }
    }

    // initialize google maps
    private fun initGMaps() {
        mapFragment = fragmentManager.findFragmentById(R.id.map) as MapFragment

        // sets a callback object which is triggered when the GoogleMap instance is ready to be used
        mapFragment.getMapAsync(this)
    }

    // callback called when map is ready
    override fun onMapReady(googleMap: GoogleMap?) {
        Log.d(TAG, getString(R.string.onMapReady))
        map = googleMap
        map?.setOnMapClickListener(this)
        map?.setOnMarkerClickListener(this)

    }

    // callback called when map is touched
    override fun onMapClick(latLng: LatLng?) {
        Log.d(TAG, getString(R.string.onMapClick))
        markerForGeofence(latLng)
    }

    private fun markerForGeofence(latLng: LatLng?) {
        Log.d(TAG, getString(R.string.markerForGeofence) + "(" + latLng + ")")
        val title = latLng?.latitude.toString() + ", " + latLng?.longitude.toString()
        var markerOptions : MarkerOptions? = null
        // define marker options
        if(null != latLng) {
            markerOptions = MarkerOptions()
                    .position(latLng)
                    .title(title)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE))
        }

        if(null != map){
            // remove last geofence marker
            if(null != geofenceMarker)
                geofenceMarker?.remove()

            geofenceMarker = map?.addMarker(markerOptions)
        }
    }

    // callback called when marker is touched
    override fun onMarkerClick(marker: Marker?): Boolean {
        Log.d(TAG, getString(R.string.onMarkerClickListener) + " : " + marker?.getPosition())
        return false
    }

    //GoogleApiClient.ConnectionCallbacks connected
    override fun onConnected(bundle: Bundle?) {
        Log.i(TAG, getString(R.string.onConnected))
        getLastKnownLocation()
        recoverGeofenceMarker()
    }

    // recovering last geofence marker
    private fun recoverGeofenceMarker() {
        Log.d(TAG, getString(R.string.recoverGeofenceMarker))
        val prefs = getPreferences(Context.MODE_PRIVATE)
        if(prefs.contains(KEY_GEOFENCE_LAT) && prefs.contains(KEY_GEOFENCE_LONG)){
            val latitude = prefs.getLong(KEY_GEOFENCE_LAT, -1).toDouble()
            val longitude = prefs.getLong(KEY_GEOFENCE_LONG, -1).toDouble()
            val latLng = LatLng(latitude, longitude)
            markerForGeofence(latLng)
            drawGeofence()
        }
    }

    // Get last known location
    private fun getLastKnownLocation() {
        Log.d(TAG, getString(R.string.getLastKnownLocation))
        if (checkPermission()) {
            fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
            fusedLocationProviderClient.lastLocation
                    .addOnSuccessListener(this, OnSuccessListener { location: Location? ->
                        if (null != location) {
                            lastLocation = location
                            Log.d(TAG, "LastKnown Location : " + " Long : " + lastLocation?.longitude
                                    + " Lat : " + lastLocation?.latitude)
                            writeLastLocation()
                            startLocationUpdates()
                        } else {
                            Log.d(TAG, getString(R.string.no_location_received_yet))
                            startLocationUpdates()
                        }
                    })
        } else {
            askPermission()
        }
    }

    // start location updates
    private fun startLocationUpdates() {
        Log.d(TAG, getString(R.string.start_location_updates))
        locationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(UPDATE_INTERVAL)
                .setFastestInterval(FASTEST_INTERVAL)

        if(checkPermission()) {
            fusedLocationProviderClient.requestLocationUpdates(locationRequest, null)
        }
    }

    private fun writeLastLocation() {
        writeActualLocation(lastLocation)
    }

    //GoogleApiClient.ConnectionCallbacks suspended
    override fun onConnectionSuspended(i: Int) {
        Log.w(TAG, getString(R.string.onConnectionSuspended))
    }

    //GoogleApiClient.OnConnectionFailedListener failed
    override fun onConnectionFailed(connectionResult: ConnectionResult) {
        Log.w(TAG, getString(R.string.on_connection_failed))
    }

    override fun onStart() {
        super.onStart()

        // calling googleApiClient connection on starting the activity
        googleApiClient?.connect()
    }

    override fun onStop() {
        super.onStop()

        //Disconnect GoogleApiClient connection when stopping activity
        googleApiClient?.disconnect()
    }


    override fun onLocationChanged(location: Location?) {
        Log.d(TAG, "onLocationChanged [" + location + "]")
        lastLocation = location
        writeActualLocation(lastLocation)

    }

    // write location coordinates on UI
    private fun writeActualLocation(lastLocation: Location?) {
        mLatText.setText("Lat : " + lastLocation?.latitude.toString())
        mLongText.setText("Long : " + lastLocation?.longitude.toString())

        if(null != lastLocation?.latitude && null != lastLocation?.longitude)
            markerLocation(LatLng(lastLocation.latitude, lastLocation.longitude))
    }

    private fun markerLocation(latLng: LatLng?) {
        Log.d(TAG, "markerLocation(" + latLng + ")")
        val title = latLng?.latitude.toString() + ", " + latLng?.longitude.toString()
        var markerOptions : MarkerOptions? = null
        if(null != latLng) {
            markerOptions = MarkerOptions()
                    .title(title)
                    .position(latLng)
        }

        if(null != map){
            //remove the anterior marker
            if(null != locationMarker)
                locationMarker?.remove()

            locationMarker = map?.addMarker(markerOptions)
            val zoom = 14f
            val cameraUpdate = CameraUpdateFactory.newLatLngZoom(latLng, zoom)
            map?.animateCamera(cameraUpdate)

            val cameraPosition = CameraPosition.Builder()
                .target(latLng)      // Sets the center of the map to location user
                .zoom(14f)                   // Sets the zoom
                .bearing(90f)                // Sets the orientation of the camera to east
                .tilt(40f)                   // Sets the tilt of the camera to 30 degrees
                .build();                   // Creates a CameraPosition from the builder
            map?.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
        }

    }

    // check for permission to access location
    private fun checkPermission() : Boolean{
        Log.d(TAG, getString(R.string.check_permission))
        return ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    }

    // Ask for permission
    private fun askPermission() {
        Log.d(TAG, getString(R.string.ask_permission))
        ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION), REQUEST_PERMISSION)
    }

    // Verify user's response of the permission requested
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        Log.d(TAG, "onRequestPermissionsResult()")
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when(requestCode){
            REQUEST_PERMISSION -> {
                if(grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                    // permission granted
                    getLastKnownLocation()
                } else{
                    // permission denied
                    permissionDenied()
                }
            }
            else -> {
                Log.d(TAG, "request permission code doesn't match")
            }
        }
    }

    // App cannot work without permissions
    private fun permissionDenied() {
        Log.d(TAG, "permission denied")
        Toast.makeText(applicationContext, getString(R.string.permissions_denied), Toast.LENGTH_LONG).show();
    }

    /**
     * creating a geofence
     * transition types: GEOFENCE_TRANSITION_ENTER :: triggering event when a user enters geofence
     * or GEOFENCE_TRANSITION_EXIT :: triggering event when a user exits geofence
     * or GEOFENCE_TRANSITION_DWELL :: triggering event when a user dwells in the geofence for quite an interval;
     * set the setLoiteringDelay property for the dwell period
     */
    private fun createGeofence(latLng : LatLng?, radius : Float) :  Geofence?{
        Log.d(TAG, "createGeofence()")
        var geofence : Geofence? = null
                if(null != latLng) {
            geofence = Geofence.Builder()
                    .setCircularRegion(latLng.latitude, latLng.longitude, radius)
                    .setRequestId(GEOFENCE_REQ_ID)
                    .setExpirationDuration(GEO_DURATION)
                    .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
                    .build()
        }
        return geofence
    }

    // create a geofencing request object
    private fun createGeofenceRequest(geofence : Geofence?) : GeofencingRequest{
        Log.d(TAG, "createGeofenceRequest")

        return GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                .addGeofence(geofence)
                .build()

    }

    //create geofence pending intent
    private fun createGeofencePendingIntent() : PendingIntent{
        Log.d(TAG, "createGeofencePendingIntent")
        val intent = Intent(this, GeofenceTransitionService::class.java)
        return  PendingIntent.getService(this, GEOFENCE_REQ_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT)
    }

    //add the created geofencerequest to the device's monitoring list
    private fun addGeofence(geofencingRequest : GeofencingRequest){
        Log.d(TAG, "addGeofence()")
        if(checkPermission()){
            Log.d(TAG, "checkPermission in addGeofence()")
            var geofencingClient = LocationServices.getGeofencingClient(this)
            geofencingClient.addGeofences(geofencingRequest, createGeofencePendingIntent())
                    .addOnSuccessListener{
                        saveGeofence()
                        drawGeofence()
                    }
                    .addOnFailureListener {

                    }
        }
    }

    // saving geofence marker with shared prefs
    private fun saveGeofence() {
        Log.d(TAG, "saveGeofence()")
        val sharedPrefs = getPreferences(Context.MODE_PRIVATE)
        val editor = sharedPrefs.edit()
        val latitude = geofenceMarker?.position?.latitude?.toLong() ?: -1L
        val longitude = geofenceMarker?.position?.longitude?.toLong() ?: -1L
        editor.putLong(KEY_GEOFENCE_LAT, latitude)
        editor.putLong(KEY_GEOFENCE_LONG, longitude)
        editor.apply();
    }

    // draw geofence circle on google map
    private fun drawGeofence() {
        Log.d(TAG, "drawGeofence()")
        if(null != geofenceLimits){
            geofenceLimits?.remove()
        }

        var circleOptions : CircleOptions = CircleOptions()
                .center(geofenceMarker?.position)
                .strokeColor(Color.argb(50, 70, 70, 70))
                .fillColor(Color.argb(100, 150, 150, 150))
                .radius(GEOFENCE_RADIUS_DOUBLE)
        if(null != map)
            geofenceLimits = map?.addCircle(circleOptions)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val inflator = menuInflater
        inflator.inflate(R.menu.geofence_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when(item?.itemId){
            R.id.geofence -> {
                startGeofence()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    /**
     * start geofence creation process
     * Radius : radius of the geofencing area
     * Latitude and Longitude : center coordinates of the geofencing area
     */
    private fun startGeofence() {
        Log.d(TAG, "startGeofence()")
        if (null != geofenceMarker) {
            var geofence = createGeofence(geofenceMarker?.position, GEOFENCE_RADIUS_FLOAT)
            val geofencingRequest = createGeofenceRequest(geofence)
            addGeofence(geofencingRequest)
        } else {
            Log.e(TAG, "geofence marker is null")
        }
    }
}
