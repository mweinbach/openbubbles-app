package app.openbubbles.nativeapp.ui.common

import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * The product's "pill field" color decision, made once: underline indicators
 * removed (the container shape already reads as a field), containers left to
 * the caller or the library default. Used by the chat-list search field and
 * the login form fields.
 */
@Composable
fun pillTextFieldColors(
    focusedContainerColor: Color = Color.Unspecified,
    unfocusedContainerColor: Color = Color.Unspecified,
): TextFieldColors = TextFieldDefaults.colors(
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    focusedContainerColor = focusedContainerColor,
    unfocusedContainerColor = unfocusedContainerColor,
)
