package dev.vaultdrop

import android.Manifest
import android.content.ActivityNotFoundException
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import dev.vaultdrop.ui.EditorScreen
import dev.vaultdrop.ui.FirstRunScreen
import dev.vaultdrop.ui.SearchScreen
import dev.vaultdrop.ui.VerifyScreen
import dev.vaultdrop.ui.theme.VaultDropTheme
import dev.vaultdrop.voice.RecognizeSpeech

class MainActivity : ComponentActivity() {

    private val vm: EditorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Phase 1, ahead of the first frame: read the cached config out of
        // SharedPreferences and open today's note. That single read is the only
        // filesystem operation on the critical path.
        vm.phase1()

        setContent {
            VaultDropTheme {
                VaultDropApp(vm)
            }
        }
    }

    /** Nothing in flight is lost on the way out. */
    override fun onPause() {
        super.onPause()
        vm.saveNow()
    }

    /**
     * The two halves of "how long have we been away", for the resume reindex.
     *
     * Leaving also closes the mic on a dictation. An app in the background
     * cannot keep listening without a microphone foreground service, and this
     * one has none, so the dictation stops the way the Stop button stops it:
     * what was heard is still transcribed, and lands when it arrives. Turning
     * the phone is not leaving, and the dictation carries on through it.
     */
    override fun onStop() {
        super.onStop()
        vm.onBackgrounded()
        if (!isChangingConfigurations) vm.stopDictation()
    }

    override fun onResume() {
        super.onResume()
        vm.onResumed()
    }
}

@Composable
fun VaultDropApp(vm: EditorViewModel) {
    val state = vm.state

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> vm.onVaultPicked(uri) }

    val recognizer = rememberLauncherForActivityResult(RecognizeSpeech()) { vm.onRecognized(it) }
    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> vm.onMicPermission(granted) }
    val context = LocalContext.current

    // Voice input asks for what only an activity can start. Collected for as
    // long as the activity lives; a request sent between two of them waits.
    LaunchedEffect(Unit) {
        for (launch in vm.voiceLaunches) {
            when (launch) {
                VoiceLaunch.Recognizer -> try {
                    recognizer.launch(Unit)
                } catch (_: ActivityNotFoundException) {
                    vm.onRecognizerMissing()
                }
                VoiceLaunch.MicPermission -> micPermission.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    when {
        state.screen == Screen.FirstRun -> FirstRunScreen(
            errorTitle = state.vaultErrorTitle,
            errorDetail = state.vaultErrorDetail,
            onChooseVault = { picker.launch(null) },
        )

        state.screen == Screen.Verifying -> VerifyScreen(
            lines = state.verifyLines,
            step = state.verifyStep,
            shaLine = state.shaLine,
            ready = state.verifyReady,
            onContinue = vm::onVerifyContinue,
        )

        state.searchOpen -> SearchScreen(
            state = state,
            onQueryChange = vm::onSearchQuery,
            onSubmit = vm::onSearchSubmit,
            onClear = vm::clearSearch,
            onClose = vm::closeSearch,
            onOpenResult = vm::openResult,
            onBack = vm::backToResults,
            onReadFull = vm::readFull,
        )

        else -> EditorScreen(
            state = state,
            body = vm.body,
            onToggleFrontmatter = vm::toggleFrontmatter,
            onOpenDays = vm::openDays,
            onCloseDays = vm::closeDays,
            onPickDay = { vm.goToDate(it.date) },
            onStepDay = vm::stepDay,
            onOpenSearch = vm::openSearch,
            onPickLink = { vm.insertLink(it.name) },
            onDismissNotice = vm::dismissNotice,
            onChangeVault = vm::changeVault,
            onReloadConfig = vm::reloadConfig,
            onAskReindex = vm::askReindex,
            onConfirmReindex = vm::confirmReindex,
            onCancelReindex = vm::cancelReindex,
            onAskDelete = vm::askDelete,
            onConfirmDelete = vm::confirmDelete,
            onCancelDelete = vm::cancelDelete,
            onMic = vm::onMic,
            onMicStop = vm::stopDictation,
            onMicCancel = vm::cancelDictation,
            onMicPick = vm::pickVoice,
            onMicPickerClose = vm::closeVoicePicker,
            onMicSettings = vm::openVoicePicker,
            onMicOpenApp = { packageName ->
                vm.dismissNotice()
                context.packageManager.getLaunchIntentForPackage(packageName)?.let {
                    runCatching { context.startActivity(it) }
                }
            },
        )
    }

    // Phase 2 begins only once the editor is up and the caret is live.
    LaunchedEffect(state.screen) {
        if (state.screen == Screen.Editor) vm.startPhase2()
    }

    BackHandler(
        enabled = state.daysOpen || state.searchOpen || state.deleteOpen || state.reindexOpen ||
            state.voicePicker != null,
    ) {
        when {
            state.voicePicker != null -> vm.closeVoicePicker()
            state.deleteOpen -> vm.cancelDelete()
            state.reindexOpen -> vm.cancelReindex()
            state.daysOpen -> vm.closeDays()
            state.openedNote != null -> vm.backToResults()
            state.searchOpen -> vm.closeSearch()
        }
    }
}
