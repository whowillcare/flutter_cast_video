package it.aesys.flutter_cast_video

import android.content.Context
import android.net.Uri
import android.view.ContextThemeWrapper
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadOptions
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.MediaError
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.Session
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.api.PendingResult
import com.google.android.gms.common.api.ResultCallback
import com.google.android.gms.common.api.Status
import com.google.android.gms.common.images.WebImage
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.platform.PlatformView


class ChromeCastController(
        messenger: BinaryMessenger,
        viewId: Int,
        context: Context?
) : PlatformView, MethodChannel.MethodCallHandler, SessionManagerListener<Session>, PendingResult.StatusListener {
    private val channel = MethodChannel(messenger, "flutter_cast_video/chromeCast_$viewId")
    private val chromeCastButton = MediaRouteButton(ContextThemeWrapper(context, R.style.Theme_AppCompat_DayNight_NoActionBar))
    private val sessionManager = CastContext.getSharedInstance()?.sessionManager

    init {
        CastButtonFactory.setUpMediaRouteButton(context as Context, chromeCastButton)
        channel.setMethodCallHandler(this)
    }

    private fun loadMedia(args: Any?) {
        if (args is Map<*, *>) {
            val url = args["url"] as? String ?: ""
            val title = args["title"] as? String ?: ""
            val subtitle = args["subtitle"] as? String ?: ""
            val imageUrl = args["image"] as? String ?: ""
            val contentType = args["contentType"] as? String ?: "videos/mp4"
            val liveStream = args["live"] as? Boolean ?: false

            val movieMetadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE)

            val streamType = if (liveStream) MediaInfo.STREAM_TYPE_LIVE else MediaInfo.STREAM_TYPE_BUFFERED

            movieMetadata.putString(MediaMetadata.KEY_TITLE, title)
            movieMetadata.putString(MediaMetadata.KEY_SUBTITLE, subtitle)
            movieMetadata.addImage(WebImage(Uri.parse(imageUrl)))
            //movieMetadata.addImage(WebImage(Uri.parse(imageUrl)))

            val media = MediaInfo
                            .Builder(url)
                            .setStreamType(streamType)
                            .setContentType(contentType)
                            .setMetadata(movieMetadata)
                       .build()
            val options = MediaLoadOptions.Builder().build()
            val request = sessionManager?.currentCastSession?.remoteMediaClient?.load(media, options)

            request?.addStatusListener(this)
        }
    }

    private fun play() {
        val request = sessionManager?.currentCastSession?.remoteMediaClient?.play()
        request?.addStatusListener(this)
    }

    private fun pause() {
        val request = sessionManager?.currentCastSession?.remoteMediaClient?.pause()
        request?.addStatusListener(this)
    }

    /*private fun mediaQueue(args: Any?) : List<HashMap<String,String>>{
       val items : List<HashMap<String,String>> = mutableListOf()
        val client = sessionManager?.currentCastSession?.remoteMediaClient ?: return items
       val queue = client.getMediaQueue()
       val status = client.mediaStatus
       val qlen = queue.getItemCount()

       var start = 0
       var batch = 5
       var page = 1;
       if (args is Map<*, *>) {
           batch = (args["batch"] as? Int) ?: batch
           page = (args["page"] as? Int) ?: page
       }
        start = page * batch
        var end = (page+1) * batch
        if (start >= qlen){
            return items
        }
        if (end > qlen){
            end = qlen
        }
        batch = end - start

        var ret = queue.fetchMoreItemsRelativeToIndex(start, batch, 0)
        ret.setResultCallback {

        }
    }
     */
    private fun seek(args: Any?) {
        if (args is Map<*, *>) {
            val relative = (args["relative"] as? Boolean) ?: false
            var interval = args["interval"] as? Double
            interval = interval?.times(1000)
            if (relative) {
                interval = interval?.plus(sessionManager?.currentCastSession?.remoteMediaClient?.mediaStatus?.streamPosition ?: 0)
            }
            val request = sessionManager?.currentCastSession?.remoteMediaClient?.seek(interval?.toLong() ?: 0)
            request?.addStatusListener(this)
        }
    }

    private fun mediaInfoToMap(mediaInfo: MediaInfo?) : HashMap<String,String>? {
        val info = HashMap<String, String>()
        mediaInfo?.let {
            val id = mediaInfo.contentId ?: ""
            info["id"] = id
                info["url"] = mediaInfo.contentUrl ?: id
            info["contentType"] = mediaInfo.contentType ?: ""

            mediaInfo.metadata?.let {
                info["title"] = it.getString(MediaMetadata.KEY_TITLE) ?: ""
                info["subtitle"] = it.getString(MediaMetadata.KEY_SUBTITLE) ?: ""
                val imgs = it.images
                if (imgs.size > 0){
                    info["image"] = imgs[0].url.toString();
                }
            }
        }
        return info;
    }
    private fun getMediaInfo() : HashMap<String,String>? =  mediaInfoToMap(sessionManager?.currentCastSession?.remoteMediaClient?.getMediaInfo())


    private fun setVolume(args: Any?) {
        if (args is Map<*, *>) {
            val volume = args["volume"] as? Double
            val request = sessionManager?.currentCastSession?.remoteMediaClient?.setStreamVolume(volume ?: 0.0)
            request?.addStatusListener(this)
        }
    }

    private fun getVolume() = sessionManager?.currentCastSession?.volume ?: 0.0

    private fun stop() {
        val request = sessionManager?.currentCastSession?.remoteMediaClient?.stop()
        request?.addStatusListener(this)
    }

    private fun isPlaying() = sessionManager?.currentCastSession?.remoteMediaClient?.isPlaying ?: false

    private fun isConnected() = sessionManager?.currentCastSession?.isConnected ?: false

    private fun endSession() = sessionManager?.endCurrentSession(true)

    private fun position() = sessionManager?.currentCastSession?.remoteMediaClient?.approximateStreamPosition ?: 0

    private fun duration() = sessionManager?.currentCastSession?.remoteMediaClient?.mediaInfo?.streamDuration ?: 0

    private fun addSessionListener() {
        sessionManager?.addSessionManagerListener(this)
    }

    private fun removeSessionListener() {
        sessionManager?.removeSessionManagerListener(this)
    }

    private val mRemoteMediaClientListener: RemoteMediaClient.Callback = object : RemoteMediaClient.Callback() {
        override fun onStatusUpdated() {
            val mediaStatus: MediaStatus? = sessionManager?.currentCastSession?.remoteMediaClient?.mediaStatus
            val playerStatus: Int = mediaStatus?.playerState ?: MediaStatus.PLAYER_STATE_UNKNOWN
            var retCode: Int = playerStatus
            if (playerStatus == MediaStatus.PLAYER_STATE_PLAYING) {
                retCode = 1
            } else if (playerStatus == MediaStatus.PLAYER_STATE_BUFFERING) {
                retCode = 0
            } else if (playerStatus == MediaStatus.PLAYER_STATE_IDLE && mediaStatus?.getIdleReason() === MediaStatus.IDLE_REASON_FINISHED) {
                retCode = 2
            }else if (playerStatus == MediaStatus.PLAYER_STATE_PAUSED){
                retCode = 3
            }else {
                retCode = 4 //error or unkonwn
            }
            channel.invokeMethod("chromeCast#didPlayerStatusUpdated", retCode)
        }
        override fun onMediaError(mediaError: MediaError){
            val errorCode: Int = mediaError.detailedErrorCode ?: 100
            channel.invokeMethod("chromeCast#didPlayerStatusUpdated", errorCode)
        }
    }

    override fun getView() = chromeCastButton

    override fun dispose() {

    }

    // Flutter methods handling

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when(call.method) {
            "chromeCast#wait" -> result.success(null)
            "chromeCast#loadMedia" -> {
                loadMedia(call.arguments)
                result.success(null)
            }
            "chromeCast#play" -> {
                play()
                result.success(null)
            }
            "chromeCast#pause" -> {
                pause()
                result.success(null)
            }
            "chromeCast#seek" -> {
                seek(call.arguments)
                result.success(null)
            }
            "chromeCast#setVolume" -> {
                setVolume(call.arguments)
                result.success(null)
            }
            "chromeCast#getMediaInfo" -> result.success(getMediaInfo())
            "chromeCast#getVolume" -> result.success(getVolume())
            "chromeCast#stop" -> {
                stop()
                result.success(null)
            }
            "chromeCast#isPlaying" -> result.success(isPlaying())
            "chromeCast#isConnected" -> result.success(isConnected())
            "chromeCast#endSession" -> {
                endSession()
                result.success(null)
            }
            "chromeCast#position" -> result.success(position())
            "chromeCast#duration" -> result.success(duration())
            "chromeCast#addSessionListener" -> {
                addSessionListener()
                result.success(null)
            }
            "chromeCast#removeSessionListener" -> {
                removeSessionListener()
                result.success(null)
            }
        }
    }

    // SessionManagerListener

    override fun onSessionStarted(p0: Session, p1: String) {
        if(p0 is CastSession) {
            p0.remoteMediaClient?.registerCallback(mRemoteMediaClientListener);
        }
        channel.invokeMethod("chromeCast#didStartSession", null)
    }

    override fun onSessionEnded(p0: Session, p1: Int) {
        channel.invokeMethod("chromeCast#didEndSession", null)
    }

    override fun onSessionResuming(p0: Session, p1: String) {

    }

    override fun onSessionResumed(p0: Session, p1: Boolean) {

    }

    override fun onSessionResumeFailed(p0: Session, p1: Int) {

    }

    override fun onSessionSuspended(p0: Session, p1: Int) {

    }

    override fun onSessionStarting(p0: Session) {

    }

    override fun onSessionEnding(p0: Session) {

    }

    override fun onSessionStartFailed(p0: Session, p1: Int) {

    }

    // PendingResult.StatusListener

    override fun onComplete(status: Status) {
        if (status.isSuccess) {
            channel.invokeMethod("chromeCast#requestDidComplete", null)
        }
    }
}
