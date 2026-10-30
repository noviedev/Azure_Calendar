package com.example.azurecalendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import com.example.azurecalendar.ui.theme.AzureCalendarTheme

private val BorderLavender = Color(0xFFE1BEE7)
private val TextDark = Color(0xFF2C1A36)

@Composable
fun SocialLoginButtons(
    onGoogleClick: () -> Unit,
    onFacebookClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(
            onClick = onGoogleClick,
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, BorderLavender),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = R.drawable.ic_google),
                    contentDescription = "Google Logo",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Google", fontSize = 13.sp, color = TextDark)
            }
        }

        OutlinedButton(
            onClick = onFacebookClick,
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, BorderLavender),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "f", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1877F2))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Facebook", fontSize = 13.sp, color = TextDark)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SocialLoginButtonsPreview() {
    AzureCalendarTheme {
        SocialLoginButtons(
            onGoogleClick = {},
            onFacebookClick = {},
            modifier = Modifier.padding(16.dp)
        )
    }
}