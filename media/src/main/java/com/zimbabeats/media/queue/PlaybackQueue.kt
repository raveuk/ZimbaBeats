package com.zimbabeats.media.queue

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.zimbabeats.core.domain.model.Video
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages the playback queue
 */
class PlaybackQueue {

    private val _queue = MutableStateFlow<List<QueueItem>>(emptyList())
    val queue: StateFlow<List<QueueItem>> = _queue.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _shuffleEnabled = MutableStateFlow(false)
    val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(RepeatMode.OFF)
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    /**
     * Set the entire queue with new items
     */
    fun setQueue(videos: List<Video>, startIndex: Int = 0) {
        _queue.value = videos.mapIndexed { index, video ->
            QueueItem(
                id = video.id,
                video = video,
                originalIndex = index
            )
        }
        _currentIndex.value = startIndex
    }

    /**
     * Add video to end of queue
     */
    fun addToQueue(video: Video) {
        val currentQueue = _queue.value.toMutableList()
        currentQueue.add(
            QueueItem(
                id = video.id,
                video = video,
                originalIndex = currentQueue.size
            )
        )
        _queue.value = currentQueue
    }

    /**
     * Play video next (insert after current)
     */
    fun playNext(video: Video) {
        val currentQueue = _queue.value.toMutableList()
        val insertIndex = _currentIndex.value + 1
        currentQueue.add(
            insertIndex,
            QueueItem(
                id = video.id,
                video = video,
                originalIndex = insertIndex
            )
        )
        _queue.value = currentQueue
    }

    /**
     * Remove item from queue
     */
    fun removeFromQueue(index: Int) {
        val currentQueue = _queue.value.toMutableList()
        if (index in currentQueue.indices) {
            currentQueue.removeAt(index)
            _queue.value = currentQueue

            // Adjust current index if needed
            if (index < _currentIndex.value) {
                _currentIndex.value = _currentIndex.value - 1
            }
        }
    }

    /**
     * Move item in queue
     */
    fun moveItem(fromIndex: Int, toIndex: Int) {
        val currentQueue = _queue.value.toMutableList()
        if (fromIndex in currentQueue.indices && toIndex in currentQueue.indices) {
            val item = currentQueue.removeAt(fromIndex)
            currentQueue.add(toIndex, item)
            _queue.value = currentQueue

            // Adjust current index
            when {
                fromIndex == _currentIndex.value -> _currentIndex.value = toIndex
                fromIndex < _currentIndex.value && toIndex >= _currentIndex.value -> {
                    _currentIndex.value = _currentIndex.value - 1
                }
                fromIndex > _currentIndex.value && toIndex <= _currentIndex.value -> {
                    _currentIndex.value = _currentIndex.value + 1
                }
            }
        }
    }

    /**
     * Clear the queue
     */
    fun clearQueue() {
        _queue.value = emptyList()
        _currentIndex.value = 0
    }

    /**
     * Skip to next item
     */
    fun skipToNext(): Boolean {
        val hasNext = when (_repeatMode.value) {
            RepeatMode.OFF -> _currentIndex.value < _queue.value.size - 1
            RepeatMode.ALL -> true
            RepeatMode.ONE -> false // Don't skip in repeat one mode
        }

        if (hasNext) {
            _currentIndex.value = if (_currentIndex.value >= _queue.value.size - 1) {
                0 // Loop back to start in repeat all mode
            } else {
                _currentIndex.value + 1
            }
            return true
        }
        return false
    }

    /**
     * Skip to previous item
     */
    fun skipToPrevious(): Boolean {
        if (_currentIndex.value > 0) {
            _currentIndex.value = _currentIndex.value - 1
            return true
        }
        return false
    }

    /**
     * Skip to specific index
     * @return true if the index was valid and updated
     */
    fun skipToIndex(index: Int): Boolean {
        if (index in _queue.value.indices) {
            _currentIndex.value = index
            return true
        }
        return false
    }

    /**
     * Get current item
     */
    fun getCurrentItem(): QueueItem? {
        return _queue.value.getOrNull(_currentIndex.value)
    }

    /**
     * Toggle shuffle
     */
    fun toggleShuffle() {
        _shuffleEnabled.value = !_shuffleEnabled.value
        if (_shuffleEnabled.value) {
            shuffleQueue()
        } else {
            unshuffleQueue()
        }
    }

    /**
     * Set repeat mode
     */
    fun setRepeatMode(mode: RepeatMode) {
        _repeatMode.value = mode
    }

    /**
     * Cycle through repeat modes
     */
    fun cycleRepeatMode() {
        _repeatMode.value = when (_repeatMode.value) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
    }

    private fun shuffleQueue() {
        val currentItem = getCurrentItem()
        val shuffled = _queue.value.shuffled()

        // Keep current item at current position
        if (currentItem != null) {
            val mutableList = shuffled.toMutableList()
            mutableList.remove(currentItem)
            mutableList.add(_currentIndex.value, currentItem)
            _queue.value = mutableList
        } else {
            _queue.value = shuffled
        }
    }

    private fun unshuffleQueue() {
        _queue.value = _queue.value.sortedBy { it.originalIndex }
    }

    /**
     * Convert to Media3 MediaItems
     */
    fun toMediaItems(): List<MediaItem> {
        return _queue.value.map { queueItem ->
            MediaItem.Builder()
                .setMediaId(queueItem.id)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(queueItem.video.title)
                        .setArtist(queueItem.video.channelName)
                        .setArtworkUri(android.net.Uri.parse(queueItem.video.thumbnailUrl))
                        .build()
                )
                .build()
        }
    }
}

data class QueueItem(
    val id: String,
    val video: Video,
    val originalIndex: Int
)

enum class RepeatMode {
    OFF,    // No repeat
    ALL,    // Repeat entire queue
    ONE     // Repeat current item
}
