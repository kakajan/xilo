package com.example.xilo.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.xilo.data.repository.PostRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CreatePostViewModel @Inject constructor(
    private val postRepository: PostRepository
) : ViewModel() {

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _success = MutableStateFlow(false)
    val success: StateFlow<Boolean> = _success.asStateFlow()

    fun createPost(title: String, content: String) {
        if (title.isBlank() || content.isBlank()) {
            _error.value = "عنوان و متن پست نمی‌تواند خالی باشد"
            return
        }
        viewModelScope.launch {
            _isSubmitting.value = true
            _error.value = null
            
            postRepository.createPost(title, content)
                .onSuccess {
                    _success.value = true
                    // Invalidate feed after successful creation
                    postRepository.refreshFeed()
                }
                .onFailure { e ->
                    _error.value = e.message ?: "Failed to create post"
                }
                
            _isSubmitting.value = false
        }
    }
}
