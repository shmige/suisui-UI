package com.svip.mansui.ui.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.svip.mansui.R
import com.svip.mansui.ui.component.material.SegmentedColumn
import com.svip.mansui.ui.component.material.SegmentedListItem
import com.svip.mansui.ui.component.material.SegmentedTextField
import com.svip.mansui.ui.component.material.SmallOutlineButton
import com.svip.mansui.ui.component.material.TonalCard
import com.svip.mansui.ui.theme.LocalControlAlpha
import com.svip.mansui.ui.theme.LocalEnableBlur
import com.svip.mansui.ui.theme.LocalGlassMode
import com.svip.mansui.ui.util.BlurredBar
import com.svip.mansui.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop

/**
 * 主页 (Material)。
 * 2026-10-01: 三个主按钮改为"图标在上 / 标题+副标题在下"的方形卡片, 排成一行可横向滑动;
 * 顶栏模糊只在页面滚动后才生效, 修掉"开启模糊后手机顶部一大块白色区域"。
 */
@Composable
fun HomePagerMaterial(
    state: HomeUiState,
    actions: HomeActions,
    bottomInnerPadding: Dp,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val glass = LocalGlassMode.current
    val enableBlur = LocalEnableBlur.current
    val blurBackdrop = rememberBlurBackdrop(enableBlur)
    val scrollState = rememberScrollState()
    // 滚动到顶时不铺底色/不加模糊 → 大标题栏干净, 不会出现整条白色磨砂块
    val scrolled = scrollState.value > 0
    val barColor = if (glass || (blurBackdrop != null && scrolled)) {
        Color.Transparent
    } else {
        MaterialTheme.colorScheme.surface
    }

    Scaffold(
        topBar = {
            BlurredBar(blurBackdrop, blurActive = scrolled) {
                TopBar(scrollBehavior = scrollBehavior, barColor = barColor)
            }
        },
        containerColor = if (glass) Color.Transparent else MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        Box(modifier = if (blurBackdrop != null) Modifier.layerBackdrop(blurBackdrop) else Modifier) {
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatusCard(modeName = state.modeName)
                VersionAndChannelCard(
                    state = state,
                    onChannelClick = actions.onChannelClick,
                    onUpdate = actions.onUpdate,
                )
                KamiCard(
                    state = state,
                    onKamiChange = actions.onKamiChange,
                    onToggleVisible = actions.onToggleKamiVisible,
                )
                QuickActionRow(state = state, actions = actions)
                Spacer(Modifier.height(bottomInnerPadding))
            }
        }
    }
}

@Composable
private fun TopBar(
    scrollBehavior: TopAppBarScrollBehavior? = null,
    barColor: Color = MaterialTheme.colorScheme.surface,
) {
    LargeFlexibleTopAppBar(
        title = { Text(stringResource(R.string.app_name)) },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = barColor,
            scrolledContainerColor = barColor
        ),
        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        scrollBehavior = scrollBehavior
    )
}

/**
 * 运行状态卡 —— 照搬 KernelSU-Style-UI-Kit 的卡片写法 (HomeMaterial.PermissionCard):
 * Card(secondaryContainer) + 左列 titleMedium SemiBold 标题 / bodySmall 副标题 + 右侧 AssistChip(CheckCircle)。
 */
@Composable
private fun StatusCard(modeName: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
                .copy(alpha = LocalControlAlpha.current),
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.home_running_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "当前模式：$modeName",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                AssistChip(
                    onClick = { },
                    colors = AssistChipDefaults.assistChipColors(
                        labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        leadingIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                    label = { Text("运行中") },
                    leadingIcon = {
                        Icon(Icons.Default.CheckCircle, contentDescription = null)
                    },
                )
            }
        }
    }
}

/** 应用版本 (与版本号同一行) + 渠道版本 */
@Composable
private fun VersionAndChannelCard(
    state: HomeUiState,
    onChannelClick: () -> Unit,
    onUpdate: () -> Unit,
) {
    // 有新版本 → 「当前版本：v1.8 · 最新版：v1.9」; 已是最新 → 只写「当前已是最新版」(版本号看右侧)
    // 不管有没有开"检查更新"都显示
    val versionText = when {
        state.latestVersion.isEmpty() -> "当前版本：v${state.appVersion.versionName}"
        state.hasUpdate -> "当前版本：v${state.appVersion.versionName} · 最新版：v${state.latestVersion}"
        else -> "当前已是最新版"
    }
    SegmentedColumn(
        content = listOf(
            {
                SegmentedListItem(
                    headlineContent = { Text(stringResource(R.string.home_app_version)) },
                    supportingContent = { Text(versionText) },
                    trailingContent = {
                        if (state.hasUpdate) {
                            // 不是最新版 → 这里给一个「更新」按钮 (与设置页「重置」同一套描边小按钮)
                            SmallOutlineButton(text = "更新", onClick = onUpdate)
                        } else {
                            Text(
                                text = "v${state.appVersion.versionName}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    },
                )
            },
            {
                SegmentedListItem(
                    onClick = onChannelClick,
                    headlineContent = { Text(stringResource(R.string.home_channel)) },
                    supportingContent = { Text(state.channelName) },
                    leadingContent = { Icon(Icons.Filled.Gamepad, stringResource(R.string.home_channel)) },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                    },
                )
            }
        )
    )
}

/** 卡密输入行 (KSU 分段输入行: 眼睛切换明文) */
@Composable
private fun KamiCard(
    state: HomeUiState,
    onKamiChange: (String) -> Unit,
    onToggleVisible: () -> Unit,
) {
    SegmentedColumn(
        content = listOf(
            {
                SegmentedTextField(
                    label = stringResource(R.string.home_kami),
                    value = state.kami,
                    onValueChange = onKamiChange,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = if (state.kamiVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    placeholder = { Text(stringResource(R.string.home_kami_hint)) },
                    colors = ListItemDefaults.segmentedColors().copy(
                        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                            .copy(alpha = LocalControlAlpha.current),
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                            .copy(alpha = LocalControlAlpha.current),
                        supportingContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    trailingContent = {
                        IconButton(onClick = onToggleVisible) {
                            Icon(
                                imageVector = if (state.kamiVisible) {
                                    Icons.Filled.VisibilityOff
                                } else {
                                    Icons.Filled.Visibility
                                },
                                contentDescription = null,
                            )
                        }
                    },
                )
            }
        )
    )
}

/**
 * 三个功能入口: 启动数据 / 解绑密钥 / 购买卡密
 * 布局参考用户给的效果图 —— 方形卡片(图标在上, 标题+副标题在下), 排成一行横向可滑动。
 */
@Composable
private fun QuickActionRow(
    state: HomeUiState,
    actions: HomeActions,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        QuickActionCard(
            icon = Icons.Filled.PlayArrow,
            title = stringResource(R.string.home_start),
            subtitle = "校验密钥进行初始化",
            enabled = state.kami.isNotBlank(),
            onClick = actions.onStart,
        )
        QuickActionCard(
            icon = Icons.Filled.LinkOff,
            title = stringResource(R.string.home_unbind),
            subtitle = "解绑当前设备",
            enabled = state.kami.isNotBlank(),
            onClick = actions.onUnbind,
        )
        QuickActionCard(
            icon = Icons.Filled.ShoppingCart,
            title = stringResource(R.string.home_buy),
            subtitle = "前往卡密商城",
            enabled = true,
            onClick = actions.onBuy,
        )
    }
}

@Composable
private fun QuickActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .width(148.dp)
            .height(132.dp)
            .alpha(if (enabled) 1f else 0.5f),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
            .copy(alpha = LocalControlAlpha.current),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(30.dp),
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 2,
                )
            }
        }
    }
}
