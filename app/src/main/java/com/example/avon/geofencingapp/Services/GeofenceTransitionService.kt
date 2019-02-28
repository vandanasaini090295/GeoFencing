package com.example.avon.geofencingapp.Services

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.support.v4.app.NotificationCompat
import android.text.TextUtils
import android.util.Log
import com.example.avon.geofencingapp.Interface.NotificationInterface
import com.example.avon.geofencingapp.UI.GeofenceActivity
import com.example.avon.geofencingapp.R
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent

/**
 * This Service class handles the GeofenceEvent
 */
class GeofenceTransitionService : IntentService(GeofenceTransitionService::class.simpleName), NotificationInterface {

    private val TAG = GeofenceTransitionService::class.java.simpleName
    private val GEOFENCE_NOTIFICATION_ID = 0

    override fun onHandleIntent(intent: Intent?) {
        // retrieve the geofencing event
        var geofencingEvent = GeofencingEvent.fromIntent(intent)

        // handling errors
        if(geofencingEvent.hasError()){
            var errorString = getErrorString(geofencingEvent.errorCode)
            Log.d(TAG, errorString.toString())
            return
        }

        // retrieve geofence transition
        var geofenceTransition = geofencingEvent.geofenceTransition
        // check the transition type
        if (Geofence.GEOFENCE_TRANSITION_ENTER == geofenceTransition || Geofence.GEOFENCE_TRANSITION_EXIT == geofenceTransition ){
            // get the geofence that were triggered
            var geofencesList = geofencingEvent.triggeringGeofences

            //create a detailed message with geofences recieved and sending notification details as a string
            (this as NotificationInterface).sendNotification(getGeofenceTransitionDetails(geofencesList, geofenceTransition))
        }
    }


    // create a detail message with geofences received
    private fun getGeofenceTransitionDetails(geofencesList : List<Geofence>, geofenceTransition : Int) : String{
        //get the id of each geofence triggered
        var triggerinGeofencesList = ArrayList<String>()
        for (geofence : Geofence in geofencesList){
            triggerinGeofencesList.add(geofence.requestId)
        }

        var status = ""
        if(Geofence.GEOFENCE_TRANSITION_ENTER == geofenceTransition){
            status = "entering "
        } else if(Geofence.GEOFENCE_TRANSITION_EXIT == geofenceTransition){
            status = "exiting "
        } else if(Geofence.GEOFENCE_TRANSITION_DWELL == geofenceTransition){
            status = "dwelling "
        }
        return status + TextUtils.join(" , ", triggerinGeofencesList)
    }

    private fun getErrorString(errorCode : Int){
        when(errorCode){
            GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE -> getString(R.string.geofence_not_available_error_code)
            GeofenceStatusCodes.GEOFENCE_TOO_MANY_GEOFENCES -> getString(R.string.too_many_geofences_error_code)
            GeofenceStatusCodes.GEOFENCE_TOO_MANY_PENDING_INTENTS -> getString(R.string.too_many_pending_intents_error_code)
            else -> {
                getString(R.string.unknown_error_code)
            }
        }
    }

    override fun sendNotification(message: String) {
        Log.d(TAG, "sendNotification()" + message)

        //Intent to start the main activity
        val notificationIntent = Intent(this, GeofenceActivity::class.java)

        val taskStackBuilder = TaskStackBuilder.create(this)
        taskStackBuilder.addParentStack(GeofenceActivity::class.java)
        taskStackBuilder.addNextIntent(notificationIntent)
        val notificationPendingIntent = taskStackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT)

        // creating and sending notification
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationBuilder = NotificationCompat.Builder(this, "NOTIF_CHANNEL_ID")
                .setSmallIcon(R.drawable.abc_ic_go_search_api_material)
                .setColor(Color.RED)
                .setContentTitle(message)
                .setContentText("Geofence Notification")
                .setDefaults(NotificationCompat.DEFAULT_LIGHTS)
                .setContentIntent(notificationPendingIntent)
                .setAutoCancel(true)

        notificationManager.notify(GEOFENCE_NOTIFICATION_ID, notificationBuilder.build())
    }


}