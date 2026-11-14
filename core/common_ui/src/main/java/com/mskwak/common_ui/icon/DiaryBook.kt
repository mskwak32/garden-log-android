package com.mskwak.common_ui.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.mskwak.common_ui.IconPack

val IconPack.DiaryBook: ImageVector
    get() {
        if (_DiaryBook != null) {
            return _DiaryBook!!
        }
        _DiaryBook = ImageVector.Builder(
            name = "DiaryBook",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            // 뒷장 종이
            path(fill = SolidColor(Color(0xFFEDE3C8))) {
                roundRect(x = 5f, y = 2.5f, w = 14.5f, h = 19f, r = 1.8f)
            }
            // 표지
            path(fill = SolidColor(Color(0xFF6AA524))) {
                roundRect(x = 4f, y = 2f, w = 14.5f, h = 19.5f, r = 1.8f)
            }
            // 책등
            path(fill = SolidColor(Color(0xFF557A1E))) {
                roundRect(x = 4f, y = 2f, w = 3f, h = 19.5f, r = 1f)
            }
            // 새싹 줄기
            path(
                stroke = SolidColor(Color(0xFFF7FBF1)),
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(12.8f, 17.6f)
                lineTo(12.8f, 12.6f)
            }
            // 왼쪽 잎
            path(fill = SolidColor(Color(0xFFDDF5C9))) {
                moveTo(12.8f, 14.2f)
                quadTo(10f, 14.3f, 9.6f, 11.4f)
                quadTo(12.5f, 11.2f, 12.8f, 14.2f)
                close()
            }
            // 오른쪽 잎
            path(fill = SolidColor(Color(0xFFF7FBF1))) {
                moveTo(12.8f, 13.3f)
                quadTo(13.1f, 10.1f, 16.2f, 9.9f)
                quadTo(16f, 13f, 12.8f, 13.3f)
                close()
            }
            // 책갈피 끈
            path(fill = SolidColor(Color(0xFFD85A30))) {
                moveTo(14.8f, 21.3f)
                lineTo(14.8f, 23.6f)
                lineTo(15.9f, 22.7f)
                lineTo(17f, 23.6f)
                lineTo(17f, 21.3f)
                close()
            }
        }.build()

        return _DiaryBook!!
    }

/**
 * 모서리 제어점을 꼭짓점으로 잡은 둥근 사각형 path
 */
private fun PathBuilder.roundRect(x: Float, y: Float, w: Float, h: Float, r: Float) {
    moveTo(x + r, y)
    lineTo(x + w - r, y)
    quadTo(x + w, y, x + w, y + r)
    lineTo(x + w, y + h - r)
    quadTo(x + w, y + h, x + w - r, y + h)
    lineTo(x + r, y + h)
    quadTo(x, y + h, x, y + h - r)
    lineTo(x, y + r)
    quadTo(x, y, x + r, y)
    close()
}

@Suppress("ObjectPropertyName")
private var _DiaryBook: ImageVector? = null
