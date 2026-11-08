package com.aljohanei.rafiq

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.view.KeyEvent

/**
 * عرض اسم المقطع والتحكم بالتشغيل.
 * يحتاج إذن «الوصول إلى الإشعارات» لعرض الاسم فقط؛ أزرار التحكم تعمل بدونه.
 */
class MediaWatcher : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        Media.attach(this)
    }
}

object Media {
    @Volatile var title = ""
    @Volatile var artist = ""
    @Volatile var app = ""
    @Volatile var playing = false
    private var ctrl: MediaController? = null
    private var msm: MediaSessionManager? = null

    private val cb = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) { read() }
        override fun onPlaybackStateChanged(state: PlaybackState?) { read() }
    }
    private val sessions = MediaSessionManager.OnActiveSessionsChangedListener { list -> pick(list) }

    fun granted(c: Context): Boolean {
        val flat = Settings.Secure.getString(c.contentResolver, "enabled_notification_listeners") ?: return false
        return flat.contains(c.packageName)
    }

    fun attach(c: Context) {
        if (!granted(c)) return
        try {
            val m = c.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val cn = ComponentName(c, MediaWatcher::class.java)
            if (msm == null) { m.addOnActiveSessionsChangedListener(sessions, cn); msm = m }
            pick(m.getActiveSessions(cn))
        } catch (e: Exception) {}
    }

    private fun pick(list: List<MediaController>?) {
        try { ctrl?.unregisterCallback(cb) } catch (e: Exception) {}
        ctrl = list?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list?.firstOrNull()
        try { ctrl?.registerCallback(cb) } catch (e: Exception) {}
        read()
    }

    private fun read() {
        val c = ctrl
        if (c == null) { title = ""; artist = ""; app = ""; playing = false; return }
        val md = c.metadata
        title = md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
        artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
        app = c.packageName
        playing = c.playbackState?.state == PlaybackState.STATE_PLAYING
    }

    /** أزرار التشغيل: عبر الجلسة إن وُجدت، وإلا بمفاتيح الوسائط العامة */
    fun key(c: Context, code: Int) {
        val t = ctrl?.transportControls
        if (t != null) {
            when (code) {
                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> t.skipToPrevious()
                KeyEvent.KEYCODE_MEDIA_NEXT -> t.skipToNext()
                else -> if (playing) t.pause() else t.play()
            }
            return
        }
        val am = c.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        if (code == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) playing = !playing
    }
}
