package com.kurly.loupe.ui

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kurly.loupe.DesignInspector

/**
 * Debug 설정 화면에 넣을 수 있는 인스펙터 토글 Composable.
 *
 * 플로팅 토글 버튼의 표시/숨김을 제어합니다.
 * 실제 인스펙터 on/off는 플로팅 버튼으로 합니다.
 *
 * 사용법:
 * ```kotlin
 * @Composable
 * fun DebugSettingsScreen() {
 *     Column {
 *         Text("Debug Settings")
 *         DesignInspectorToggle()
 *     }
 * }
 * ```
 */
@Composable
fun DesignInspectorToggle(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var isEnabled by remember { mutableStateOf(DesignInspector.isToggleShown) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isEnabled) Color(0xFF5F0080).copy(alpha = 0.08f)
            else Color(0xFFF5F5F5)
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Design Inspector",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1C1C1C),
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (isEnabled) "플로팅 버튼으로 인스펙터를 켜고 끌 수 있습니다"
                    else "플로팅 토글 버튼을 화면에 표시합니다",
                    fontSize = 13.sp,
                    color = Color(0xFF6B6B6B),
                )
            }

            Switch(
                checked = isEnabled,
                onCheckedChange = { checked ->
                    isEnabled = checked
                    val activity = context as? Activity ?: return@Switch
                    if (checked) {
                        DesignInspector.showToggle(activity)
                    } else {
                        DesignInspector.hideToggle()
                    }
                },
                colors = SwitchDefaults.colors(
                    checkedTrackColor = Color(0xFF5F0080),
                    checkedThumbColor = Color.White,
                ),
            )
        }
    }

    // 하위 안내
    if (isEnabled) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF5F0080))
            )
            Spacer(Modifier.width(8.dp))
            Text("바운딩 박스", fontSize = 11.sp, color = Color(0xFF757575))

            Spacer(Modifier.width(16.dp))
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF00C853).copy(alpha = 0.5f))
            )
            Spacer(Modifier.width(8.dp))
            Text("패딩", fontSize = 11.sp, color = Color(0xFF757575))

            Spacer(Modifier.width(16.dp))
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFFFF6D00).copy(alpha = 0.5f))
            )
            Spacer(Modifier.width(8.dp))
            Text("마진", fontSize = 11.sp, color = Color(0xFF757575))
        }
    }
}

/**
 * 전체 디버그 설정 화면 예시.
 * 필요에 따라 커스터마이즈하세요.
 */
@Composable
fun DebugSettingsScreen(
    onBack: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
    ) {
        // 간단한 앱바
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Debug Settings",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        HorizontalDivider(color = Color(0xFFEEEEEE))

        Spacer(modifier = Modifier.height(8.dp))

        // 인스펙터 토글
        DesignInspectorToggle()

        // 여기에 다른 디버그 옵션 추가 가능
        // DebugOption("Network Logger", ...)
        // DebugOption("Performance Monitor", ...)
    }
}