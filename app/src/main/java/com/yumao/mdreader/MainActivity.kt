package com.yumao.mdreader

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yumao.mdreader.data.AppSettings
import com.yumao.mdreader.editor.EditorScreen
import com.yumao.mdreader.home.HomeScreen
import com.yumao.mdreader.reader.ReaderScreen
import com.yumao.mdreader.settings.SettingsScreen
import com.yumao.mdreader.ui.MdReaderTheme
import com.yumao.mdreader.ui.isAppDark
import com.yumao.mdreader.ui.rememberMarkwon

class MainActivity : ComponentActivity() {

    private val vm: MdViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        resolveIntent(intent, savedInstanceState == null)
        setContent { AppRoot(vm) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        resolveIntent(intent, true)
    }

    private fun resolveIntent(intent: Intent?, focusedEntry: Boolean) {
        if (intent == null || !focusedEntry) return
        vm.handleIncoming(intent)
    }
}

@Composable
private fun AppRoot(vm: MdViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val screen by vm.screen.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val message by vm.message.collectAsStateWithLifecycle()
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it, duration = SnackbarDuration.Short)
            vm.consumeMessage()
        }
    }
    MdReaderTheme(settings) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            val dark = isAppDark(settings)
            val markwon = rememberMarkwon(settings, dark)
            Box(Modifier.fillMaxSize()) {
                AnimatedContent(
                    targetState = screen,
                    transitionSpec = {
                        val dir = if (targetState == Screen.HOME) -1 else 1
                        (slideInHorizontally { d -> dir * d / 5 } + fadeIn()) togetherWith
                            (slideOutHorizontally { d -> -dir * d / 5 } + fadeOut())
                    },
                    label = "nav",
                ) { target ->
                    when (target) {
                        Screen.HOME -> HomeScreen(vm)
                        Screen.READER -> ReaderScreen(vm, markwon)
                        Screen.EDITOR -> EditorScreen(vm)
                        Screen.SETTINGS -> SettingsScreen(vm)
                    }
                }
                SnackbarHost(
                    hostState = snackbar,
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
                )
            }
        }
    }
}
