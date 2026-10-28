package com.example.carlauncher

import android.service.notification.NotificationListenerService

/**
 * لا نقرأ محتوى الإشعارات. هذه الخدمة مطلوبة فقط لأن أندرويد لا يسمح
 * بقراءة «ما يُشغَّل الآن» والتحكم به إلا لتطبيق مُفعَّل له الوصول إلى الإشعارات.
 */
class MediaListener : NotificationListenerService()
