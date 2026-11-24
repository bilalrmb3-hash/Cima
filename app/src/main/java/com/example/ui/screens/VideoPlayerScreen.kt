package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.data.addons.ActivePlaybackSession
import com.example.data.addons.PlayableStream
import com.example.data.addons.SubtitleTrackItem
import com.example.data.player.PlaybackBookmark
import com.example.data.player.formatDurationMs
import com.example.ui.i18n.LocalStrings
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

private enum class ActivePlayerDialog {
    NONE, QUALITY, SUBTITLES, AUDIO, SPEED, SOURCES
}

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    session: ActivePlaybackSession,
    initialBookmark: PlaybackBookmark?,
    onBack: () -> Unit,
    onSavePosition: (positionMs: Long, durationMs: Long) -> Unit,
    onSwitchStream: (PlayableStream, Long) -> Unit,
    onOpenAllSources: () -> Unit,
    onNextEpisode: () -> Unit,
    onMarkCompleted: () -> Unit
) {
    val strings = LocalStrings.current
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isLandscape by remember { mutableStateOf(true) }
    var isControlsVisible by remember { mutableStateOf(true) }
    var isLocked by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var playbackError by remember { mutableStateOf<String?>(null) }

    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isSeeking by remember { mutableStateOf(false) }
    var seekSliderMs by remember { mutableFloatStateOf(0f) }

    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var activeDialog by remember { mutableStateOf(ActivePlayerDialog.NONE) }

    var qualityOptions by remember { mutableStateOf<List<VideoQualityOption>>(emptyList()) }
    var subtitleOptions by remember { mutableStateOf<List<MediaTrackOption>>(emptyList()) }
    var audioOptions by remember { mutableStateOf<List<MediaTrackOption>>(emptyList()) }
    var isAutoQuality by remember { mutableStateOf(true) }
    var isSubtitlesOff by remember { mutableStateOf(false) }

    var customLocalSubtitleUri by remember(session) { mutableStateOf(session.localSubtitleUri) }
    var showStartOverBanner by remember(session) {
        mutableStateOf(initialBookmark != null && initialBookmark.isMeaningfulResume)
    }
    var hasRestoredBookmark by remember(session) { mutableStateOf(false) }
    var hasMarkedCompletedThisSession by remember(session) { mutableStateOf(false) }

    val trackSelector = remember {
        DefaultTrackSelector(context).apply {
            parameters = buildUponParameters()
                .setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
                .setMaxVideoBitrate(Int.MAX_VALUE)
                .build()
        }
    }

    val exoPlayer = remember(session) {
        val okHttp = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
        val okHttpFactory = OkHttpDataSource.Factory(okHttp).apply {
            if (session.stream.headers.isNotEmpty()) {
                setDefaultRequestProperties(session.stream.headers)
            }
        }
        val dataSourceFactory = DefaultDataSource.Factory(context, okHttpFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setTrackSelector(trackSelector)
            .build()
            .apply {
                playWhenReady = true
            }
    }

    fun buildMediaItem(localSub: Uri?): MediaItem {
        val subtitleConfigs = buildList {
            session.extraSubtitles.forEach { sub ->
                add(buildRemoteSubtitleConfig(sub))
            }
            if (localSub != null) {
                add(buildLocalSubtitleConfig(context, localSub))
            }
        }
        return MediaItem.Builder()
            .setUri(Uri.parse(session.stream.url))
            .setSubtitleConfigurations(subtitleConfigs)
            .build()
    }

    LaunchedEffect(session, customLocalSubtitleUri) {
        val resumePos = session.initialSeekOverrideMs
            ?: if (!hasRestoredBookmark && initialBookmark != null && initialBookmark.isMeaningfulResume) {
                initialBookmark.positionMs
            } else {
                exoPlayer.currentPosition.coerceAtLeast(0L)
            }
        hasRestoredBookmark = true
        playbackError = null
        exoPlayer.setMediaItem(buildMediaItem(customLocalSubtitleUri), resumePos)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    val localSubPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            customLocalSubtitleUri = uri
            isSubtitlesOff = false
        }
    }

    // Orientation & immersive system bars management
    DisposableEffect(isLandscape) {
        val activity = context.findActivity()
        val window = activity?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, view)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
        activity?.requestedOrientation = if (isLandscape) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        onDispose { }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            val pos = exoPlayer.currentPosition
            val dur = exoPlayer.duration
            if (pos >= 3_000L) {
                onSavePosition(pos, dur.coerceAtLeast(0L))
            }
            exoPlayer.release()
        }
    }

    DisposableEffect(Unit) {
        val activity = context.findActivity()
        onDispose {
            activity?.window?.let { window ->
                WindowCompat.getInsetsController(window, view)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Lifecycle pause handling
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                exoPlayer.pause()
                val pos = exoPlayer.currentPosition
                val dur = exoPlayer.duration
                if (pos >= 3_000L) {
                    onSavePosition(pos, dur.coerceAtLeast(0L))
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Player listener for state, tracks, and errors
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    durationMs = exoPlayer.duration.coerceAtLeast(0L)
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                val parsed = parseTracks(tracks)
                qualityOptions = parsed.videoOptions
                audioOptions = parsed.audioOptions
                subtitleOptions = parsed.subtitleOptions
            }

            override fun onPlayerError(error: PlaybackException) {
                isBuffering = false
                playbackError = error.localizedMessage ?: error.errorCodeName
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    // Periodic UI progress ticker & local position persistence every 5s
    LaunchedEffect(exoPlayer) {
        var ticks = 0
        while (true) {
            if (!isSeeking) {
                currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                durationMs = exoPlayer.duration.coerceAtLeast(0L)
            }
            if (ticks % 10 == 0 && exoPlayer.currentPosition >= 3_000L) {
                onSavePosition(
                    exoPlayer.currentPosition,
                    exoPlayer.duration.coerceAtLeast(0L)
                )
            }
            ticks++
            delay(500L)
        }
    }

    // Auto-hide controls & start-over prompt
    LaunchedEffect(isControlsVisible, isPlaying, activeDialog) {
        if (isControlsVisible && isPlaying && activeDialog == ActivePlayerDialog.NONE) {
            delay(4000L)
            isControlsVisible = false
        }
    }

    LaunchedEffect(showStartOverBanner) {
        if (showStartOverBanner) {
            delay(6500L)
            showStartOverBanner = false
        }
    }

    BackHandler {
        if (isLocked) {
            isLocked = false
            isControlsVisible = true
        } else {
            onBack()
        }
    }

    val nearEnd = durationMs > 30_000L && currentPositionMs >= (durationMs * 0.90f).toLong()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                isControlsVisible = !isControlsVisible
            }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    keepScreenOn = true
                }
            },
            update = { playerView ->
                if (playerView.player !== exoPlayer) {
                    playerView.player = exoPlayer
                }
                playerView.resizeMode = resizeMode
                playerView.keepScreenOn = isPlaying
            },
            modifier = Modifier.fillMaxSize()
        )

        if (isBuffering && playbackError == null) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        playbackError?.let { err ->
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = strings.playerErrorTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TextButton(onClick = onBack) { Text(strings.back) }
                        Button(onClick = {
                            playbackError = null
                            exoPlayer.prepare()
                            exoPlayer.play()
                        }) {
                            Text(strings.retry)
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = isControlsVisible && playbackError == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            if (isLocked) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .safeDrawingPadding()
                        .padding(16.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.65f),
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        IconButton(
                            onClick = { isLocked = false },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("player_unlock_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.LockOpen,
                                contentDescription = strings.unlockControls,
                                tint = Color.White
                            )
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.72f),
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.80f)
                                )
                            )
                        )
                        .safeDrawingPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    // Top Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("player_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = strings.back,
                                tint = Color.White
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = session.target.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val subLine = listOfNotNull(
                                session.target.subtitle,
                                session.stream.addonName
                            ).joinToString(" • ")
                            if (subLine.isNotBlank()) {
                                Text(
                                    text = subLine,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.78f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        IconButton(
                            onClick = { isLandscape = !isLandscape },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("player_rotate_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ScreenRotation,
                                contentDescription = strings.rotateScreen,
                                tint = Color.White
                            )
                        }

                        IconButton(
                            onClick = { isLocked = true },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("player_lock_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = strings.lockControls,
                                tint = Color.White
                            )
                        }
                    }

                    // Center Playback Controls (kept LTR for intuitive rewind/forward arrows)
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Row(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalArrangement = Arrangement.spacedBy(28.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.55f)
                            ) {
                                IconButton(
                                    onClick = {
                                        exoPlayer.seekTo((exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L))
                                    },
                                    modifier = Modifier
                                        .size(56.dp)
                                        .testTag("player_rewind_10")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Replay10,
                                        contentDescription = "-10s",
                                        tint = Color.White,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }
                            }

                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                IconButton(
                                    onClick = {
                                        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                                    },
                                    modifier = Modifier
                                        .size(68.dp)
                                        .testTag("player_play_pause")
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(38.dp)
                                    )
                                }
                            }

                            Surface(
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.55f)
                            ) {
                                IconButton(
                                    onClick = {
                                        val target = exoPlayer.currentPosition + 10_000L
                                        val maxDur = exoPlayer.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
                                        exoPlayer.seekTo(target.coerceAtMost(maxDur))
                                    },
                                    modifier = Modifier
                                        .size(56.dp)
                                        .testTag("player_forward_10")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Forward10,
                                        contentDescription = "+10s",
                                        tint = Color.White,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Bottom Seekbar & Feature Chips
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (showStartOverBanner) {
                                FilledTonalButton(
                                    onClick = {
                                        exoPlayer.seekTo(0L)
                                        showStartOverBanner = false
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.testTag("player_start_over_button")
                                ) {
                                    Text(strings.startFromBeginning, style = MaterialTheme.typography.labelMedium)
                                }
                            }

                            if (nearEnd && !hasMarkedCompletedThisSession) {
                                FilledTonalButton(
                                    onClick = {
                                        hasMarkedCompletedThisSession = true
                                        onMarkCompleted()
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.testTag("player_mark_watched_button")
                                ) {
                                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(strings.markWatchedAction, style = MaterialTheme.typography.labelMedium)
                                }
                            }

                            Spacer(Modifier.weight(1f))

                            if (nearEnd && session.target.nextEpisodeStreamId != null) {
                                Button(
                                    onClick = onNextEpisode,
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    modifier = Modifier.testTag("player_next_episode_button")
                                ) {
                                    Text(strings.nextEpisodeLabel, style = MaterialTheme.typography.labelMedium)
                                    Spacer(Modifier.width(4.dp))
                                    Icon(Icons.Rounded.SkipNext, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val displayedPos = if (isSeeking) seekSliderMs.toLong() else currentPositionMs
                                Text(
                                    text = formatDurationMs(displayedPos),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White
                                )
                                Slider(
                                    value = if (durationMs > 0L) {
                                        (displayedPos.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                                    } else 0f,
                                    onValueChange = { fraction ->
                                        isSeeking = true
                                        seekSliderMs = fraction * durationMs.toFloat()
                                    },
                                    onValueChangeFinished = {
                                        exoPlayer.seekTo(seekSliderMs.toLong())
                                        currentPositionMs = seekSliderMs.toLong()
                                        isSeeking = false
                                    },
                                    colors = SliderDefaults.colors(
                                        thumbColor = MaterialTheme.colorScheme.primary,
                                        activeTrackColor = MaterialTheme.colorScheme.primary,
                                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 8.dp)
                                        .testTag("player_seek_slider")
                                )
                                Text(
                                    text = formatDurationMs(durationMs),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PlayerControlPill(
                                icon = Icons.Rounded.VideoLibrary,
                                label = strings.sourcesLabel,
                                onClick = { activeDialog = ActivePlayerDialog.SOURCES },
                                testTag = "player_sources_button"
                            )
                            PlayerControlPill(
                                icon = Icons.Rounded.HighQuality,
                                label = if (isAutoQuality) strings.qualityLabel else {
                                    qualityOptions.firstOrNull { it.isSelected }?.let { "${it.height}p" } ?: strings.qualityLabel
                                },
                                onClick = { activeDialog = ActivePlayerDialog.QUALITY },
                                testTag = "player_quality_button"
                            )
                            PlayerControlPill(
                                icon = Icons.Rounded.Subtitles,
                                label = strings.subtitlesLabel,
                                onClick = { activeDialog = ActivePlayerDialog.SUBTITLES },
                                testTag = "player_subtitles_button"
                            )
                            if (audioOptions.size > 1) {
                                PlayerControlPill(
                                    icon = Icons.Rounded.Audiotrack,
                                    label = strings.audioLabel,
                                    onClick = { activeDialog = ActivePlayerDialog.AUDIO },
                                    testTag = "player_audio_button"
                                )
                            }
                            PlayerControlPill(
                                icon = Icons.Rounded.Speed,
                                label = "${playbackSpeed}x",
                                onClick = { activeDialog = ActivePlayerDialog.SPEED },
                                testTag = "player_speed_button"
                            )
                            val aspectLabel = when (resizeMode) {
                                AspectRatioFrameLayout.RESIZE_MODE_FILL -> strings.aspectFill
                                AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> strings.aspectZoom
                                else -> strings.aspectFit
                            }
                            PlayerControlPill(
                                icon = Icons.Rounded.AspectRatio,
                                label = aspectLabel,
                                onClick = {
                                    resizeMode = when (resizeMode) {
                                        AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                        AspectRatioFrameLayout.RESIZE_MODE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    }
                                },
                                testTag = "player_aspect_button"
                            )
                        }
                    }
                }
            }
        }
    }

    when (activeDialog) {
        ActivePlayerDialog.QUALITY -> {
            QualityPickerDialog(
                options = qualityOptions,
                isAuto = isAutoQuality,
                onSelectAuto = {
                    isAutoQuality = true
                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
                        .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                        .setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
                        .setMaxVideoBitrate(Int.MAX_VALUE)
                        .build()
                    activeDialog = ActivePlayerDialog.NONE
                },
                onSelectOption = { option ->
                    isAutoQuality = false
                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
                        .setOverrideForType(TrackSelectionOverride(option.trackGroup, listOf(option.trackIndex)))
                        .build()
                    activeDialog = ActivePlayerDialog.NONE
                },
                onDismiss = { activeDialog = ActivePlayerDialog.NONE }
            )
        }

        ActivePlayerDialog.SUBTITLES -> {
            SubtitlePickerDialog(
                options = subtitleOptions,
                isOff = isSubtitlesOff,
                onSelectOff = {
                    isSubtitlesOff = true
                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                        .build()
                    activeDialog = ActivePlayerDialog.NONE
                },
                onSelectOption = { option ->
                    isSubtitlesOff = false
                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        .setOverrideForType(TrackSelectionOverride(option.trackGroup, listOf(option.trackIndex)))
                        .build()
                    activeDialog = ActivePlayerDialog.NONE
                },
                onPickLocalSubtitle = {
                    activeDialog = ActivePlayerDialog.NONE
                    localSubPicker.launch(arrayOf("application/x-subrip", "text/vtt", "text/plain", "*/*"))
                },
                onDismiss = { activeDialog = ActivePlayerDialog.NONE }
            )
        }

        ActivePlayerDialog.AUDIO -> {
            AudioTrackPickerDialog(
                options = audioOptions,
                onSelectOption = { option ->
                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
                        .setOverrideForType(TrackSelectionOverride(option.trackGroup, listOf(option.trackIndex)))
                        .build()
                    activeDialog = ActivePlayerDialog.NONE
                },
                onDismiss = { activeDialog = ActivePlayerDialog.NONE }
            )
        }

        ActivePlayerDialog.SPEED -> {
            SpeedPickerDialog(
                currentSpeed = playbackSpeed,
                onSelectSpeed = { newSpeed ->
                    playbackSpeed = newSpeed
                    exoPlayer.playbackParameters = PlaybackParameters(newSpeed)
                    activeDialog = ActivePlayerDialog.NONE
                },
                onDismiss = { activeDialog = ActivePlayerDialog.NONE }
            )
        }

        ActivePlayerDialog.SOURCES -> {
            StreamSourcePickerDialog(
                streams = session.availableStreams,
                activeStreamId = session.stream.id,
                onSelectStream = { selected ->
                    val pos = exoPlayer.currentPosition
                    val dur = exoPlayer.duration.coerceAtLeast(0L)
                    if (pos >= 3_000L) onSavePosition(pos, dur)
                    activeDialog = ActivePlayerDialog.NONE
                    onSwitchStream(selected, pos)
                },
                onOpenAllSources = {
                    activeDialog = ActivePlayerDialog.NONE
                    onOpenAllSources()
                },
                onDismiss = { activeDialog = ActivePlayerDialog.NONE }
            )
        }

        ActivePlayerDialog.NONE -> Unit
    }
}

@Composable
private fun PlayerControlPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    testTag: String
) {
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag(testTag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color.White,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private data class ParsedTrackGroups(
    val videoOptions: List<VideoQualityOption>,
    val audioOptions: List<MediaTrackOption>,
    val subtitleOptions: List<MediaTrackOption>
)

@OptIn(UnstableApi::class)
private fun parseTracks(tracks: Tracks): ParsedTrackGroups {
    val videoList = mutableListOf<VideoQualityOption>()
    val audioList = mutableListOf<MediaTrackOption>()
    val subList = mutableListOf<MediaTrackOption>()

    tracks.groups.forEachIndexed { groupIdx, group ->
        val trackGroup = group.mediaTrackGroup
        for (i in 0 until group.length) {
            if (!group.isTrackSupported(i)) continue
            val format = group.getTrackFormat(i)
            val isSelected = group.isTrackSelected(i)

            when (group.type) {
                C.TRACK_TYPE_VIDEO -> {
                    if (format.height > 0) {
                        val kbps = if (format.bitrate > 0) " (${format.bitrate / 1000} kbps)" else ""
                        videoList.add(
                            VideoQualityOption(
                                id = "v_${groupIdx}_$i",
                                label = "${format.height}p$kbps",
                                height = format.height,
                                bitrate = format.bitrate,
                                trackGroup = trackGroup,
                                trackIndex = i,
                                isSelected = isSelected
                            )
                        )
                    }
                }
                C.TRACK_TYPE_AUDIO -> {
                    val label = listOfNotNull(
                        format.label?.takeIf { it.isNotBlank() },
                        format.language?.takeIf { it.isNotBlank() && it != "und" }
                    ).distinct().joinToString(" • ").ifBlank { "Audio ${audioList.size + 1}" }
                    audioList.add(
                        MediaTrackOption(
                            id = "a_${groupIdx}_$i",
                            label = label,
                            trackGroup = trackGroup,
                            trackIndex = i,
                            isSelected = isSelected
                        )
                    )
                }
                C.TRACK_TYPE_TEXT -> {
                    val label = listOfNotNull(
                        format.label?.takeIf { it.isNotBlank() },
                        format.language?.takeIf { it.isNotBlank() && it != "und" }
                    ).distinct().joinToString(" • ").ifBlank { "Subtitle ${subList.size + 1}" }
                    subList.add(
                        MediaTrackOption(
                            id = "s_${groupIdx}_$i",
                            label = label,
                            trackGroup = trackGroup,
                            trackIndex = i,
                            isSelected = isSelected
                        )
                    )
                }
            }
        }
    }

    val sortedVideo = videoList
        .sortedWith(compareByDescending<VideoQualityOption> { it.height }.thenByDescending { it.bitrate })
        .distinctBy { "${it.height}_${it.bitrate}" }

    return ParsedTrackGroups(
        videoOptions = sortedVideo,
        audioOptions = audioList,
        subtitleOptions = subList
    )
}

private fun buildRemoteSubtitleConfig(sub: SubtitleTrackItem): MediaItem.SubtitleConfiguration {
    val mimeType = detectSubtitleMimeType(sub.url, null)
    return MediaItem.SubtitleConfiguration.Builder(Uri.parse(sub.url))
        .setMimeType(mimeType)
        .setLanguage(sub.lang)
        .setLabel(sub.label)
        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
        .build()
}

private fun buildLocalSubtitleConfig(context: Context, uri: Uri): MediaItem.SubtitleConfiguration {
    val mimeType = detectSubtitleMimeType(uri.toString(), context.contentResolver.getType(uri))
    return MediaItem.SubtitleConfiguration.Builder(uri)
        .setMimeType(mimeType)
        .setLabel("Local Subtitle")
        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
        .build()
}

private fun detectSubtitleMimeType(urlOrPath: String, contentType: String?): String {
    val lower = urlOrPath.lowercase()
    return when {
        contentType?.contains("vtt") == true || ".vtt" in lower -> MimeTypes.TEXT_VTT
        contentType?.contains("subrip") == true || ".srt" in lower -> MimeTypes.APPLICATION_SUBRIP
        ".ass" in lower || ".ssa" in lower -> MimeTypes.TEXT_SSA
        else -> MimeTypes.APPLICATION_SUBRIP
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
