package com.nyantv.ui.auth

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.nyantv.data.PairSession
import com.nyantv.data.PairingClient
import com.nyantv.data.PollResult
import com.nyantv.ui.utils.focusBorder
import com.nyantv.viewmodel.AppViewModel
import kotlinx.coroutines.delay

private fun qrBitmap(text: String, size: Int = 512): ImageBitmap {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
    val w = matrix.width
    val h = matrix.height
    val pixels = IntArray(w * h)
    for (y in 0 until h) {
        val off = y * w
        for (x in 0 until w) {
            pixels[off + x] = if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        }
    }
    return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        .apply { setPixels(pixels, 0, w, 0, 0, w, h) }
        .asImageBitmap()
}

/**
 * Device-pairing login: shows a QR/code, the user signs in on their phone/PC (a real browser,
 * past Cloudflare), and the TV polls the relay until the token arrives.
 */
@Composable
fun PairLoginScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    onSuccess: () -> Unit,
    provider: String = "anilist",
) {
    val serviceName = if (provider == "mal") "MyAnimeList" else "AniList"
    val client  = remember { PairingClient() }
    var session by remember { mutableStateOf<PairSession?>(null) }
    var status  by remember { mutableStateOf("Requesting a code…") }
    var error   by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }

    BackHandler { onBack() }

    LaunchedEffect(attempt) {
        error = null
        session = null
        status = "Requesting a code…"
        val s = client.newSession(provider)
        if (s == null) {
            val detail = client.lastError
            // A TLS/certificate failure on this flow is almost always a wrong device clock (the
            // cert looks out-of-date), so point the user there instead of "check your connection".
            val looksLikeTls = detail != null && listOf(
                "chain validation", "certificate", "cert path", "certpath",
                "trust anchor", "ssl", "handshake", "validation failed",
            ).any { detail.contains(it, ignoreCase = true) }
            error = buildString {
                append("Couldn't reach the pairing server")
                if (detail != null) append(" ($detail)")
                append(".\n")
                if (looksLikeTls) {
                    append("This usually means the device's date & time is wrong. ")
                    append("Fix the clock (Settings → Date & time) and try again.")
                } else {
                    append("Check your connection and try again.")
                }
            }
            return@LaunchedEffect
        }
        session = s
        status = "Waiting for you to sign in…"
        val deadline = System.currentTimeMillis() + s.expiresIn * 1000L
        while (System.currentTimeMillis() < deadline) {
            delay(2500)
            when (val r = client.poll(s.code)) {
                is PollResult.Code -> {
                    status = "Finishing sign-in…"
                    val ok = when (provider) {
                        "mal"  -> vm.exchangePairedMalCode(r.authCode, r.codeVerifier.orEmpty(), r.redirectUri)
                        else   -> vm.exchangePairedAnilistCode(r.authCode, r.redirectUri)
                    }
                    if (ok) {
                        status = "Signed in!"
                        onSuccess()
                    } else {
                        error = "Signed in, but finishing on the device failed. Tap retry."
                    }
                    return@LaunchedEffect
                }
                is PollResult.Done -> {
                    status = "Signed in!"
                    vm.applyPairedAnilistToken(r.accessToken)
                    onSuccess()
                    return@LaunchedEffect
                }
                PollResult.Expired -> { error = "This code expired. Tap retry for a new one."; return@LaunchedEffect }
                PollResult.Pending -> {}
            }
        }
        error = "This code expired. Tap retry for a new one."
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                // Scrollable + top-aligned so the QR, captions and the Cancel button are never
                // clipped on shorter resolutions (vertical-centering used to cut off the bottom,
                // making the Cancel label invisible while the button still worked).
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Sign in with $serviceName",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                "Scan the code with your phone — or open the link on any device — log in to $serviceName there, and this screen continues automatically.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 480.dp),
            )

            val s = session
            when {
                error != null -> {
                    Text(error!!, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    Button(onClick = { attempt++ }, modifier = Modifier.focusBorder(RoundedCornerShape(50))) {
                        Text("Retry")
                    }
                }
                s == null -> {
                    CircularProgressIndicator()
                    Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
                else -> {
                    val qr = remember(s.verifyUrl) { qrBitmap(s.verifyUrl) }
                    Image(
                        bitmap = qr,
                        contentDescription = "Pairing QR code",
                        modifier = Modifier
                            .size(240.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White)
                            .padding(12.dp),
                    )
                    Text(s.verifyUrl, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
                    Text(
                        "Code: ${s.code}",
                        style = MaterialTheme.typography.titleMedium.copy(letterSpacing = 2.sp),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                }
            }

            OutlinedButton(
                onClick  = onBack,
                modifier = Modifier.focusBorder(RoundedCornerShape(50)),
                colors   = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onBackground),
            ) {
                Text("Cancel", color = MaterialTheme.colorScheme.onBackground)
            }
        }
    }
}
