package com.svip.mansui.ui.component.material

import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.svip.mansui.ui.theme.LocalControlAlpha

/** 默认底衬: 跟随"主页控件透明度"(背景模式下半透明, 让背景透出) */
@Composable
private fun defaultTonalContainer(): Color =
    MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp).copy(alpha = LocalControlAlpha.current)

@Composable
fun TonalCard(
    modifier: Modifier = Modifier,
    containerColor: Color = defaultTonalContainer(),
    shape: Shape = MaterialTheme.shapes.large,
    enabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = shape
    ) {
        content()
    }
}

@Composable
fun TonalCard(
    modifier: Modifier = Modifier,
    containerColor: Color = defaultTonalContainer(),
    shape: Shape = MaterialTheme.shapes.large,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = shape
    ) {
        content()
    }
}
