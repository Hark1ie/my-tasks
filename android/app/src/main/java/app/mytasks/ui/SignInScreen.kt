package app.mytasks.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** [onSignIn] returns an error message, or null on success. */
@Composable
fun SignInScreen(onSignIn: suspend () -> String?) {
    val colors = appColors()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().background(colors.background).systemBarsPadding().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("My Tasks", color = colors.text, fontSize = 28.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(12.dp))
        Text(
            "Sign in to keep your tasks in sync between your phone and your computer.",
            color = colors.textSecondary,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        if (busy) {
            CircularProgressIndicator(color = colors.accent)
        } else {
            Button(
                onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        error = onSignIn()
                        busy = false
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = Color.Black),
            ) {
                Text("Sign in with Google")
            }
        }
        error?.let {
            Spacer(Modifier.height(16.dp))
            Text(it, color = Color(0xFFEF5350), fontSize = 14.sp, textAlign = TextAlign.Center)
        }
    }
}
