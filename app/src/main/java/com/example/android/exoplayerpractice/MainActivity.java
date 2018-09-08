package com.example.android.exoplayerpractice;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.FileDataSource;
import com.google.android.exoplayer2.upstream.cache.CacheDataSink;
import com.google.android.exoplayer2.upstream.cache.CacheDataSource;
import com.google.android.exoplayer2.upstream.cache.LeastRecentlyUsedCacheEvictor;
import com.google.android.exoplayer2.upstream.cache.SimpleCache;
import com.google.android.exoplayer2.util.Util;

import java.io.File;

public class MainActivity extends AppCompatActivity {
    public static final String LOG = MainActivity.class.getSimpleName();
    PlayerView mPlayerView;
    ExoPlayer mExoPlayer;
    AudioManager mAudioManager;
    AudioFocusRequest mAudioFocusRequest;
    AudioManager.OnAudioFocusChangeListener audioFocusChangeListener = new AudioManager.OnAudioFocusChangeListener() {
        @Override
        public void onAudioFocusChange(int focusChange) {
            switch (focusChange){
                case AudioManager.AUDIOFOCUS_GAIN:
                    Log.d(LOG,"Audio focus gain");
                    if(mExoPlayer!=null && !playWhenReady){
                        Log.d(LOG,"Exoplayer is not null, playwhen ready is false");
                        playWhenReady = true;
                        mExoPlayer.setPlayWhenReady(playWhenReady);
                    }else if(mExoPlayer==null){
                        Log.d(LOG,"exoplayer is null and initialize it");
                        initializePlayer();
                    }
                    break;
                case AudioManager.AUDIOFOCUS_LOSS:
                    Log.d(LOG,"audio focus loss, release player");
                    releasePlayer();
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                    Log.d(LOG,"audio focus can duck, will temporarily pause exoplayer");
                    playWhenReady = false;
                    mExoPlayer.setPlayWhenReady(playWhenReady);
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                    Log.d(LOG,"Audio focus transient, will temporarily pause exoplayer");
                    playWhenReady = false;
                    mExoPlayer.setPlayWhenReady(playWhenReady);
                    break;
            }
        }
    };
    private boolean playWhenReady = true;
    private int currentWindow;
    private long currentPosition;
    private ComponentListener componentListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mPlayerView = (PlayerView) findViewById(R.id.player_view);
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if(Util.SDK_INT >= 24){
            Log.d(LOG,"initialize player when app starts above version 24");
            initializePlayer();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(Util.SDK_INT <24){
            Log.d(LOG,"initialize player when app resumes below version 24");
            initializePlayer();
        }
    }

    private void initializePlayer(){
        Log.d(LOG,"initializePlayer CALLED");
        BandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();
        TrackSelection.Factory videoTrackSelectionFactory = new AdaptiveTrackSelection.Factory(bandwidthMeter);
        TrackSelector trackSelector = new DefaultTrackSelector(videoTrackSelectionFactory);
        mExoPlayer = ExoPlayerFactory.newSimpleInstance(new DefaultRenderersFactory(this),
                trackSelector,new DefaultLoadControl());
        componentListener = new ComponentListener();
        mExoPlayer.addListener(componentListener);
        mPlayerView.setPlayer(mExoPlayer);
        Log.d(LOG,"playwhenready: "+playWhenReady+"currentwindow: "+currentWindow+"currentposition: "+currentPosition);
        mExoPlayer.setPlayWhenReady(playWhenReady);
        mExoPlayer.seekTo(currentWindow,currentPosition);
        MediaSource videoSource = buildMediaSource();
        mExoPlayer.prepare(videoSource,false,false);

    }

    private int initializeAudioSet(){
        Log.d(LOG,"initializeAudioSet CALLED");
        int focusRequestResult;
        //Audio focus pre_Android 8.0
        if(Util.SDK_INT < 26){
            Log.d(LOG,"request audio focus when phone sdk version is under 26");
            focusRequestResult = mAudioManager.requestAudioFocus(audioFocusChangeListener,
                    AudioManager.STREAM_MUSIC,AudioManager.AUDIOFOCUS_GAIN);

        }else{//after Android 8.0
            Log.d(LOG,"request audio focus when phone sdk version is above 26");
            AudioAttributes mAudioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();
            mAudioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setOnAudioFocusChangeListener(audioFocusChangeListener)
                    .setAcceptsDelayedFocusGain(true)
                    .setAudioAttributes(mAudioAttributes)
                    .build();
            focusRequestResult = mAudioManager.requestAudioFocus(mAudioFocusRequest);
        }
        return focusRequestResult;
    }

    private MediaSource buildMediaSource(){
        Log.d(LOG,"buildMediaSource CALLED");
        Handler handler = new Handler();
        DataSource.Factory dataSourceFactory =
                new CacheDataSourceFactory(this,100 * 1024 * 1024, 5 * 1024 * 1024);
        return new ExtractorMediaSource.Factory(dataSourceFactory)
                .createMediaSource(Uri.parse("https://d17h27t6h515a5.cloudfront.net/topher/2017/April/58ffd974_-intro-creampie/-intro-creampie.mp4"),
                        handler,null);
    }

    private void releasePlayer(){
        Log.d(LOG,"releasePlayer CALLED");
        if(mExoPlayer!=null){
            mExoPlayer.removeListener(componentListener);
            playWhenReady = mExoPlayer.getPlayWhenReady();
            currentWindow = mExoPlayer.getCurrentWindowIndex();
            currentPosition = mExoPlayer.getCurrentPosition();
            if(Util.SDK_INT < 26){
                mAudioManager.abandonAudioFocus(audioFocusChangeListener);
            }else{
                mAudioManager.abandonAudioFocusRequest(mAudioFocusRequest);
            }
            mExoPlayer.release();
            mExoPlayer = null;
        }
    }

    @Override
    protected void onPause() {
        Log.d(LOG,"ONPAUSE CALLED");
        super.onPause();
        if(Util.SDK_INT < 24){
            releasePlayer();
        }
    }

    @Override
    protected void onStop() {
        Log.d(LOG,"ONSTOP CALLED");
        super.onStop();
        if (Util.SDK_INT >= 24){
            releasePlayer();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        playWhenReady = true;
        currentWindow = 0;
        currentPosition = 0;
    }

    private class ComponentListener extends Player.DefaultEventListener {

        @Override
        public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
            int result = AudioManager.AUDIOFOCUS_REQUEST_FAILED;
            Log.d(LOG,"onPlayerStateChanged CALLED");
            super.onPlayerStateChanged(playWhenReady, playbackState);
            switch (playbackState){
                case Player.STATE_IDLE:
                    Log.d(LOG,"when player state is idle, request audio focus");
                    result = initializeAudioSet();
                    break;
                case Player.STATE_BUFFERING:
                    Log.d(LOG,"the player is buffering");
                    break;
                case Player.STATE_READY:
                    if(result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED){
                        Log.d(LOG,"AUDIO FOCUS GRANTED");
                        //start playback
                        if(playWhenReady){
                            Log.d(LOG,"AUDIO FOCUS GRANTED. playwhenready is true, so start playing");
                            mExoPlayer.setPlayWhenReady(playWhenReady);}
                    }
                    break;
                case Player.STATE_ENDED:
                    Log.d(LOG,"the media has finished playing");
                    break;
            }
        }
    }
    private class CacheDataSourceFactory implements DataSource.Factory{
        private final Context context;
        private final DefaultDataSourceFactory defaultDataSourceFactory;
        private final long maxFileSize, maxCacheSize;

        public CacheDataSourceFactory(Context context, long maxFileSize, long maxCacheSize) {
            this.context = context;
            this.maxFileSize = maxFileSize;
            this.maxCacheSize = maxCacheSize;
            String userAgent = Util.getUserAgent(context,context.getApplicationInfo().name);
            defaultDataSourceFactory = new DefaultDataSourceFactory(context,userAgent);
        }

        @Override
        public DataSource createDataSource() {
            LeastRecentlyUsedCacheEvictor evictor = new LeastRecentlyUsedCacheEvictor(maxCacheSize);
            SimpleCache simpleCache = new SimpleCache(new File(context.getCacheDir(),"media"),evictor);
            return new CacheDataSource(simpleCache,defaultDataSourceFactory.createDataSource(),
                    new FileDataSource(),new CacheDataSink(simpleCache,maxFileSize),
                    CacheDataSource.FLAG_BLOCK_ON_CACHE|CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR,null);
        }
    }
}



























