package com.svip.mansui.ui.component.bottombar

import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cottage
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.svip.mansui.R
import com.svip.mansui.ui.component.FloatingBottomBar
import com.svip.mansui.ui.component.FloatingBottomBarItem
import com.svip.mansui.ui.theme.LocalEnableFloatingBottomBar
import com.svip.mansui.ui.theme.LocalEnableFloatingBottomBarBlur
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.LayerBackdrop

/**
 * 底栏总入口 (Material): 普通底栏 ⇄ 液态玻璃悬浮底栏 (主题设置里切换)
 */
@Composable
fun BottomBarMain(
    blurBackdrop: LayerBackdrop?,
    backdrop: Backdrop,
    /**
     * 采样底是否**真的在录制内容**。
     * 视频背景 / 未开启玻璃时上层并没有挂 `layerBackdrop`, 此时若仍让底栏走
     * `drawBackdrop` 分支, 采到的是空底 → 底栏会渲染成**黑色**。
     * 所以必须和上层保持一致: 没录制就退回实心胶囊。
     */
    blurReady: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val mainState = LocalMainPagerState.current
    val enableFloatingBottomBar = LocalEnableFloatingBottomBar.current
    val enableFloatingBottomBarBlur = LocalEnableFloatingBottomBarBlur.current

    val items = BottomBarDestination.entries.map { destination ->
        BottomBarItemData(
            label = stringResource(destination.label),
            icon = destination.icon,
        )
    }

    // 液态玻璃底栏依赖 AGSL RuntimeShader (API 33+, InteractiveHighlight 里直接构造); 低版本自动降级为普通底栏
    val floatingSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    if (!enableFloatingBottomBar || !floatingSupported) {
        BottomBarMaterial()
    } else {
        FloatingBottomBar(
            modifier = modifier
                .padding(bottom = 12.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
            selectedIndex = { mainState.selectedPage },
            onSelected = { mainState.animateToPage(it) },
            backdrop = backdrop,
            tabsCount = items.size,
            isBlurEnabled = enableFloatingBottomBarBlur && blurReady,
        ) {
            items.forEachIndexed { index, item ->
                FloatingBottomBarItem(
                    onClick = {
                        mainState.animateToPage(index)
                    },
                    modifier = Modifier.defaultMinSize(minWidth = 76.dp)
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = item.label,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Visible
                    )
                }
            }
        }
    }
}

private data class BottomBarItemData(
    val label: String,
    val icon: ImageVector,
)

enum class BottomBarDestination(
    @get:StringRes val label: Int,
    val icon: ImageVector,
) {
    Home(R.string.home, Icons.Rounded.Cottage),
    Setting(R.string.settings, Icons.Rounded.Settings)
}
