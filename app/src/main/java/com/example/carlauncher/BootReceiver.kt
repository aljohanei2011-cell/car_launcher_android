package com.example.carlauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** بعد تشغيل الشاشة: نبدأ خدمة الرحلة والتنبيهات إن كان إذن الموقع ممنوحاً. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            TripService.start(context)
        }
    }
}
