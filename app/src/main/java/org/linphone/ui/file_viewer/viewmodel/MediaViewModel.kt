/*
 * Copyright (c) 2010-2023 Belledonne Communications SARL.
 *
 * This file is part of linphone-android
 * (see https://www.linphone.org).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.linphone.ui.file_viewer.viewmodel

import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.annotation.UiThread
import androidx.lifecycle.MutableLiveData
import org.linphone.core.tools.Log
import org.linphone.ui.GenericViewModel
import org.linphone.utils.Event
import org.linphone.utils.FileUtils

class MediaViewModel @UiThread constructor() : GenericViewModel() {
    companion object {
        private const val TAG = "[Media ViewModel]"
    }

    val path = MutableLiveData<String>()

    val fileName = MutableLiveData<String>()

    val fullScreenMode = MutableLiveData<Boolean>()

    val isImage = MutableLiveData<Boolean>()

    val isVideo = MutableLiveData<Boolean>()

    val isVideoPlaying = MutableLiveData<Boolean>()

    val isAudio = MutableLiveData<Boolean>()

    val isAudioPlaying = MutableLiveData<Boolean>()

    val toggleVideoPlayPauseEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    private lateinit var filePath: String

    private lateinit var mediaPlayer: MediaPlayer

    override fun onCleared() {
        if (::mediaPlayer.isInitialized) {
            mediaPlayer.release()
        }

        super.onCleared()
    }

    @UiThread
    fun loadFile(file: String) {
        filePath = file
        val name = FileUtils.getNameFromFilePath(file)
        fileName.value = name

        val extension = FileUtils.getExtensionFromFileName(name)
        val mime = FileUtils.getMimeTypeFromExtension(extension)
        when (FileUtils.getMimeType(mime)) {
            FileUtils.MimeType.Image -> {
                Log.d("$TAG File [$file] seems to be an image")
                isImage.value = true
                path.value = file
            }
            FileUtils.MimeType.Video -> {
                Log.d("$TAG File [$file] seems to be a video")
                isVideo.value = true
                isVideoPlaying.value = false
            }
            FileUtils.MimeType.Audio -> {
                Log.d("$TAG File [$file] seems to be an audio file")
                isAudio.value = true

                initMediaPlayer()
            }
            else -> {
                Log.e("$TAG Unexpected MIME type [$mime] for file at [$file]")
            }
        }
    }

    @UiThread
    fun toggleFullScreen() {
        fullScreenMode.value = fullScreenMode.value != true
    }

    @UiThread
    fun playPauseVideo() {
        val playVideo = isVideoPlaying.value == false
        isVideoPlaying.value = playVideo
        toggleVideoPlayPauseEvent.value = Event(playVideo)
    }

    @UiThread
    fun playPauseAudio() {
        if (::mediaPlayer.isInitialized) {
            if (mediaPlayer.isPlaying) {
                mediaPlayer.pause()
                isAudioPlaying.value = false
            } else {
                mediaPlayer.start()
                isAudioPlaying.value = true
            }
        }
    }

    @UiThread
    fun pauseAudio() {
        if (::mediaPlayer.isInitialized) {
            mediaPlayer.pause()
        }
    }

    @UiThread
    private fun initMediaPlayer() {
        isAudioPlaying.value = false
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).setUsage(
                    AudioAttributes.USAGE_MEDIA
                ).build()
            )
            setDataSource(filePath)
            setOnCompletionListener {
                Log.i("$TAG Media player reached the end of file")
                isAudioPlaying.postValue(false)
            }
            prepare()
            start()
            isAudioPlaying.value = true
        }
        Log.i("$TAG Media player for file [$filePath] created")
    }
}