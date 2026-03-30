package com.example.graphicaltimeplanner

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class CourseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val data = remoteMessage.data
        Log.d(TAG, "FCM data received: $data")

        val type = data["type"]
        if (type == "course_change") {
            val courseCode = data["course_code"] ?: "Course Update"
            val changeSummary = data["change_summary"] ?: "Your course schedule has been updated"
            NotificationHelper.showCourseChangeNotification(
                this,
                courseCode,
                changeSummary
            )
        }
    }

    override fun onNewToken(token: String) {
        Log.d(TAG, "FCM token refreshed: $token")
        // Topic-based architecture — no need to store token server-side
    }

    companion object {
        private const val TAG = "CourseMessagingService"
    }
}
