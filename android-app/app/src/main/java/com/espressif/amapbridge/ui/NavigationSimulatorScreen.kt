package com.espressif.amapbridge.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.espressif.amapbridge.navigation.Maneuver
import com.espressif.amapbridge.navigation.NavigationInfo
import com.espressif.amapbridge.navigation.NavigationSimulationForm
import kotlinx.coroutines.delay

@Composable
fun NavigationSimulatorScreen(onPublish: (NavigationInfo) -> Unit) {
    var form by remember { mutableStateOf(NavigationSimulationForm.preset(Maneuver.RIGHT)) }
    var menuExpanded by remember { mutableStateOf(false) }
    var autoPlay by rememberSaveable { mutableStateOf(false) }
    val validation = form.validate()

    LaunchedEffect(autoPlay) {
        if (!autoPlay) return@LaunchedEffect
        while (true) {
            for (maneuver in Maneuver.entries) {
                if (!autoPlay) return@LaunchedEffect
                form = NavigationSimulationForm.preset(maneuver)
                form.validate().navigation?.let(onPublish)
                delay(2_000)
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("完整导航模拟器", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("修改字段后发送，数据会同步到 ESP 预览和已连接设备。")
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("导航动作", fontWeight = FontWeight.SemiBold)
                Box {
                    OutlinedButton(onClick = { menuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Image(
                            painter = painterResource(form.maneuver.iconResource()),
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                        )
                        Text("  ${form.maneuver.displayName} (${form.maneuver.wireValue})")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        Maneuver.entries.forEach { maneuver ->
                            DropdownMenuItem(
                                text = { Text("${maneuver.displayName} · ${maneuver.wireValue}") },
                                leadingIcon = {
                                    Image(
                                        painter = painterResource(maneuver.iconResource()),
                                        contentDescription = null,
                                        modifier = Modifier.size(30.dp),
                                    )
                                },
                                onClick = {
                                    form = NavigationSimulationForm.preset(maneuver)
                                    menuExpanded = false
                                },
                            )
                        }
                    }
                }
            }
        }
        item {
            OutlinedTextField(
                value = form.road,
                onValueChange = { form = form.copy(road = it) },
                label = { Text("道路名") },
                modifier = Modifier.fillMaxWidth(),
                isError = validation.errors.containsKey("road"),
                supportingText = validation.errors["road"]?.let { { Text(it) } },
                singleLine = true,
            )
        }
        item {
            NumberFields(
                leftLabel = "下一动作距离 (m)",
                leftValue = form.distanceMeters,
                leftError = validation.errors["distance"],
                onLeftChange = { form = form.copy(distanceMeters = it) },
                rightLabel = "剩余距离 (m)",
                rightValue = form.remainingDistanceMeters,
                rightError = validation.errors["remaining"],
                onRightChange = { form = form.copy(remainingDistanceMeters = it) },
            )
        }
        item {
            NumberFields(
                leftLabel = "剩余时间 (min)",
                leftValue = form.remainingMinutes,
                leftError = validation.errors["duration"],
                onLeftChange = { form = form.copy(remainingMinutes = it) },
                rightLabel = "ETA (HH:mm)",
                rightValue = form.eta,
                rightError = validation.errors["eta"],
                onRightChange = { form = form.copy(eta = it) },
                rightNumeric = false,
            )
        }
        item {
            NumberFields(
                leftLabel = "当前速度 (km/h)",
                leftValue = form.currentSpeedKph,
                leftError = validation.errors["speed"],
                onLeftChange = { form = form.copy(currentSpeedKph = it) },
                rightLabel = "限速 (km/h)",
                rightValue = form.speedLimitKph,
                rightError = validation.errors["limit"],
                onRightChange = { form = form.copy(speedLimitKph = it) },
            )
        }
        item {
            OutlinedTextField(
                value = form.raw,
                onValueChange = { form = form.copy(raw = it) },
                label = { Text("原始通知（可选）") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { validation.navigation?.let(onPublish) },
                    enabled = validation.isValid && !autoPlay,
                    modifier = Modifier.weight(1f),
                ) { Text("发送一次") }
                OutlinedButton(
                    onClick = { autoPlay = !autoPlay },
                    modifier = Modifier.weight(1f),
                ) { Text(if (autoPlay) "停止轮播" else "自动轮播") }
                OutlinedButton(
                    onClick = {
                        autoPlay = false
                        form = NavigationSimulationForm.preset(Maneuver.RIGHT)
                    },
                ) { Text("重置") }
            }
        }
        item {
            Text(
                if (autoPlay) "正在每 2 秒轮播全部动作" else "共 ${Maneuver.entries.size} 种协议动作",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun NumberFields(
    leftLabel: String,
    leftValue: String,
    leftError: String?,
    onLeftChange: (String) -> Unit,
    rightLabel: String,
    rightValue: String,
    rightError: String?,
    onRightChange: (String) -> Unit,
    rightNumeric: Boolean = true,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CompactField(leftLabel, leftValue, leftError, onLeftChange, true, Modifier.weight(1f))
        CompactField(rightLabel, rightValue, rightError, onRightChange, rightNumeric, Modifier.weight(1f))
    }
}

@Composable
private fun CompactField(
    label: String,
    value: String,
    error: String?,
    onValueChange: (String) -> Unit,
    numeric: Boolean,
    modifier: Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text),
        singleLine = true,
    )
}
