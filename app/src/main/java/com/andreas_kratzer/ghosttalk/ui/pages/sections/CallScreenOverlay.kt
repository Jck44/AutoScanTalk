package com.andreas_kratzer.ghosttalk.ui.pages.sections

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.ui.theme.CallAcceptContainerDark
import com.andreas_kratzer.ghosttalk.core.ui.theme.CallAcceptContainerLight
import com.andreas_kratzer.ghosttalk.core.ui.theme.CallAcceptDark
import com.andreas_kratzer.ghosttalk.core.ui.theme.CallAcceptLight
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import java.util.Locale

// Colors for call buttons
private val AnswerGreenLight = CallAcceptLight
private val AnswerGreenDark = CallAcceptDark
private val AnswerGreenContainerLight = CallAcceptContainerLight
private val AnswerGreenContainerDark = CallAcceptContainerDark


/**
 * Full-screen overlay displayed when an incoming call is ringing.
 * Shows caller info and two large scannable buttons: Answer and Reject.
 */
@Composable
fun IncomingCallOverlay(
    callerName: String?,
    callerPhone: String?,
    focusedButton: String, // "ANNEHMEN" or "ABLEHNEN"
    onAnswer: () -> Unit,
    onReject: () -> Unit,
    modifier: Modifier = Modifier,
    isSimulated: Boolean = false
) {
    val dimensions = LocalDimensions.current
    val isDark = isSystemInDarkTheme()
    val displayName = callerName?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.call_unknown_caller)
    val displayPhone = callerPhone?.takeIf { it.isNotBlank() && it != displayName }

    // Pulsing animation for the call icon
    val infiniteTransition = rememberInfiniteTransition(label = "call_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(dimensions.paddingLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // --- Caller Info Section ---
        Spacer(modifier = Modifier.weight(0.15f))

        if (isSimulated) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                modifier = Modifier.padding(bottom = dimensions.paddingMedium)
            ) {
                Text(
                    text = "SIMULIERTER ANRUF",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(horizontal = dimensions.paddingMedium, vertical = dimensions.paddingSmall)
                )
            }
        }

        Icon(
            imageVector = Icons.Default.Call,
            contentDescription = null,
            modifier = Modifier
                .size(56.dp)
                .scale(pulseScale),
            tint = if (isDark) AnswerGreenDark else AnswerGreenLight
        )

        Spacer(modifier = Modifier.height(dimensions.paddingLarge))

        Text(
            text = displayName,
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )

        if (displayPhone != null) {
            Spacer(modifier = Modifier.height(dimensions.paddingSmall))
            Text(
                text = displayPhone,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(dimensions.paddingMedium))

        Text(
            text = stringResource(R.string.call_incoming_title),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.weight(0.2f))

        // --- Action Buttons ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dimensions.paddingLarge),
            horizontalArrangement = Arrangement.spacedBy(dimensions.paddingLarge)
        ) {
            // Answer Button
            CallActionButton(
                label = stringResource(R.string.call_answer),
                icon = Icons.Default.Phone,
                containerColor = if (isDark) AnswerGreenContainerDark else AnswerGreenContainerLight,
                contentColor = if (isDark) AnswerGreenDark else AnswerGreenLight,
                isFocused = focusedButton == "ANNEHMEN",
                onClick = onAnswer,
                testTag = "call_answer_button",
                modifier = Modifier.weight(1f).height(140.dp)
            )

            // Reject Button
            CallActionButton(
                label = stringResource(R.string.call_reject),
                icon = Icons.Default.Close,
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.error,
                isFocused = focusedButton == "ABLEHNEN",
                onClick = onReject,
                testTag = "call_reject_button",
                modifier = Modifier.weight(1f).height(140.dp)
            )
        }

        Spacer(modifier = Modifier.weight(0.15f))
    }
}

/**
 * Full-screen overlay displayed when a call is active or dialing.
 * Shows caller info, duration timer, and a single Hang Up button.
 */
@Composable
fun ActiveCallOverlay(
    callerName: String?,
    callerPhone: String?,
    durationSeconds: Int,
    isDialing: Boolean,
    isOutgoing: Boolean,
    isHangUpFocused: Boolean,
    onHangUp: () -> Unit,
    modifier: Modifier = Modifier,
    isSimulated: Boolean = false
) {
    val dimensions = LocalDimensions.current
    val displayName = callerName?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.call_unknown_caller)
    val displayPhone = callerPhone?.takeIf { it.isNotBlank() && it != displayName }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(dimensions.paddingLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.weight(0.15f))

        if (isSimulated) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                modifier = Modifier.padding(bottom = dimensions.paddingMedium)
            ) {
                Text(
                    text = "SIMULIERTER ANRUF",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(horizontal = dimensions.paddingMedium, vertical = dimensions.paddingSmall)
                )
            }
        }

        // Status icon
        Icon(
            imageVector = Icons.Default.Phone,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(dimensions.paddingLarge))

        // Caller name
        Text(
            text = displayName,
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )

        if (displayPhone != null) {
            Spacer(modifier = Modifier.height(dimensions.paddingSmall))
            Text(
                text = displayPhone,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(dimensions.paddingMedium))

        // Status subtitle
        Text(
            text = if (isDialing) {
                if (isOutgoing) stringResource(R.string.call_outgoing_title)
                else stringResource(R.string.call_incoming_title)
            } else {
                stringResource(R.string.call_active_title)
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(dimensions.paddingExtraLarge))

        // Duration or connecting text
        if (isDialing) {
            // Pulsing dots animation for connecting state
            val infiniteTransition = rememberInfiniteTransition(label = "connecting_pulse")
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.4f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "connecting_alpha"
            )
            Text(
                text = stringResource(R.string.call_connecting),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(alpha)
            )
        } else {
            val minutes = durationSeconds / 60
            val seconds = durationSeconds % 60
            Text(
                text = String.format(Locale.US, "%02d:%02d", minutes, seconds),
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.Light,
                    letterSpacing = 4.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.weight(0.25f))

        // Hang Up Button
        CallActionButton(
            label = stringResource(R.string.call_hang_up),
            icon = Icons.Default.Close,
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.error,
            isFocused = isHangUpFocused,
            onClick = onHangUp,
            testTag = "call_hang_up_button",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dimensions.paddingExtraLarge)
                .height(140.dp)
        )

        Spacer(modifier = Modifier.weight(0.15f))
    }
}

/**
 * Reusable call action button matching the app's GridButton design language.
 * Uses Card with elevation, press scale animation, and focus ring border.
 */
@Composable
private fun CallActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    containerColor: Color,
    contentColor: Color,
    isFocused: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val dimensions = LocalDimensions.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "call_button_scale"
    )

    Card(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = modifier
            .scale(scale)
            .testTag(testTag),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isFocused) 8.dp else dimensions.cardElevation
        ),
        border = if (isFocused) {
            BorderStroke(4.dp, MaterialTheme.colorScheme.primary)
        } else null,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(dimensions.paddingMedium),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(40.dp),
                tint = contentColor
            )
            Spacer(modifier = Modifier.height(dimensions.paddingMedium))
            Text(
                text = label,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = dimensions.buttonFontSize * 1.2f
                ),
                color = contentColor,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
