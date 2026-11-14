package com.safeguard.app.service

import com.safeguard.app.core.JamState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * One-process bridge between the foreground service and the ViewModel.
 * Replaces polling with a hot [StateFlow]; null means "service not running",
 * which the UI renders as a locally probed idle state.
 */
object ServiceBridge {

    private val _state = MutableStateFlow<JamState?>(null)
    val state: StateFlow<JamState?> = _state.asStateFlow()

    private var binding: Job? = null

    fun bind(scope: CoroutineScope, engine: com.safeguard.app.core.JamEngine) {
        binding?.cancel()
        binding = scope.launch {
            engine.state.collect { _state.value = it }
        }
    }

    fun unbind() {
        binding?.cancel()
        binding = null
        _state.value = null
    }
}
