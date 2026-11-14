package com.baba.callvault.ui.viewmodels

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.baba.callvault.data.AppPreferences
import com.baba.callvault.data.recordings.AudioImport
import com.baba.callvault.data.recordings.SharedAudio
import com.baba.callvault.onboarding.OnboardingStatus
import com.baba.callvault.utils.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Imports one audio file shared from another app. */
class ShareImportViewModel(application: Application) : AndroidViewModel(application) {

    sealed interface State {
        data object Reading : State
        data class Deciding(val displayName: String?, val sizeBytes: Long) : State
        data object Importing : State
        data class Imported(val displayName: String) : State
        data class ShareRefused(val reason: SharedAudio.Reason) : State
        data class ImportRefused(val reason: AudioImport.Reason) : State
    }

    private val _state = MutableStateFlow<State>(State.Reading)
    val state: StateFlow<State> = _state.asStateFlow()

    private var hasBegun = false
    private var hasChosen = false
    private var source: Uri? = null

    val isBusy: Boolean get() = _state.value is State.Importing

    fun begin(action: String?, source: Uri?) {
        if (hasBegun) return
        hasBegun = true
        viewModelScope.launch {
            val isAppSetUp = withContext(Dispatchers.IO) { isAppSetUp(getApplication()) }
            when (val verdict = SharedAudio.verdictFor(action, source != null, isAppSetUp)) {
                is SharedAudio.Verdict.Refuse -> {
                    AppLogger.w(TAG, "Share refused (${verdict.reason}): action=$action, stream=${source != null}")
                    _state.value = State.ShareRefused(verdict.reason)
                }
                SharedAudio.Verdict.Import -> {
                    val incoming = requireNotNull(source)
                    this@ShareImportViewModel.source = incoming
                    val described = withContext(Dispatchers.IO) {
                        AudioImport.describe(getApplication(), incoming)
                    }
                    _state.value = State.Deciding(described.displayName, described.sizeBytes)
                }
            }
        }
    }

    fun choose() {
        val incoming = source ?: return
        if (hasChosen) return
        hasChosen = true
        _state.value = State.Importing
        viewModelScope.launch {
            when (val result = AudioImport.import(getApplication(), incoming)) {
                is AudioImport.Outcome.Refused -> _state.value = State.ImportRefused(result.reason)
                is AudioImport.Outcome.Imported -> {
                    AppLogger.i(TAG, "Imported '${result.displayName}'")
                    _state.value = State.Imported(result.displayName)
                }
            }
        }
    }

    private fun isAppSetUp(context: Context): Boolean {
        val status = OnboardingStatus.getStatus(context, AppPreferences(context))
        return status.isComplete() && status.wizardCompleted
    }

    private companion object {
        const val TAG = "CV:ShareImport"
    }
}
