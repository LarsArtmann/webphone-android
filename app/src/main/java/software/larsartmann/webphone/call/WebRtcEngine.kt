package software.larsartmann.webphone.call

import android.content.Context
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription

/**
 * The native WebRTC audio layer (stream-webrtc-android's libwebrtc):
 * the same AudioProcessing (AEC/NS/AGC) desktop Chrome ships — the
 * platform's best-in-class call audio the platform decision bought
 * this shell for.
 */
object WebRtcEngine {
    @Volatile private var factory: PeerConnectionFactory? = null

    fun initialize(context: Context) {
        if (factory != null) return
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions
                .builder(context)
                .setEnableInternalTracer(false)
                .createInitializationOptions(),
        )
        factory =
            PeerConnectionFactory
                .builder()
                .createPeerConnectionFactory()
    }

    /** One call's media leg: mic track up, remote audio out. */
    class CallMedia(
        private val rtcConfig: PeerConnection.RTCConfiguration,
    ) {
        private val peer: PeerConnection?
        private var audioSource: AudioSource? = null
        private var micTrack: AudioTrack? = null

        init {
            val f = factory ?: error("WebRtcEngine not initialized")
            peer =
                f.createPeerConnection(
                    rtcConfig,
                    object : PeerConnection.Observer {
                        override fun onSignalingChange(state: PeerConnection.SignalingState) = Unit

                        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) = Unit

                        override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit

                        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) = Unit

                        override fun onIceCandidate(candidate: org.webrtc.IceCandidate) = Unit

                        override fun onIceCandidatesRemoved(candidates: Array<org.webrtc.IceCandidate>) = Unit

                        override fun onAddStream(stream: MediaStream) = Unit

                        override fun onRemoveStream(stream: MediaStream) = Unit

                        override fun onDataChannel(channel: org.webrtc.DataChannel) = Unit

                        override fun onRenegotiationNeeded() = Unit
                    },
                )
            audioSource = f.createAudioSource(MediaConstraints())
            micTrack = f.createAudioTrack("webphone-mic", audioSource)
            peer?.addTrack(micTrack, listOf("webphone"))
        }

        fun createOffer(callback: (sdp: SessionDescription?) -> Unit) {
            peer?.createOffer(
                object : SdpObserver by NoopSdpObserver {
                    override fun onCreateSuccess(sdp: SessionDescription) {
                        peer.setLocalDescription(NoopSdpObserver, sdp)
                        callback(sdp)
                    }

                    override fun onCreateFailure(error: String?) = callback(null)
                },
                MediaConstraints(),
            )
        }

        fun acceptAnswer(remoteSdp: SessionDescription) {
            peer?.setRemoteDescription(NoopSdpObserver, remoteSdp)
        }

        fun acceptOfferThenAnswer(
            remoteSdp: SessionDescription,
            callback: (sdp: SessionDescription?) -> Unit,
        ) {
            peer?.setRemoteDescription(NoopSdpObserver, remoteSdp)
            peer?.createAnswer(
                object : SdpObserver by NoopSdpObserver {
                    override fun onCreateSuccess(sdp: SessionDescription) {
                        peer.setLocalDescription(NoopSdpObserver, sdp)
                        callback(sdp)
                    }

                    override fun onCreateFailure(error: String?) = callback(null)
                },
                MediaConstraints(),
            )
        }

        fun setMicrophoneMuted(muted: Boolean) {
            micTrack?.setEnabled(!muted)
        }

        fun close() {
            try {
                peer?.dispose()
            } finally {
                audioSource?.dispose()
                micTrack?.dispose()
            }
        }
    }

    private object NoopSdpObserver : SdpObserver {
        override fun onCreateSuccess(sdp: SessionDescription) = Unit

        override fun onSetSuccess() = Unit

        override fun onCreateFailure(error: String?) = Unit

        override fun onSetFailure(error: String?) = Unit
    }
}
