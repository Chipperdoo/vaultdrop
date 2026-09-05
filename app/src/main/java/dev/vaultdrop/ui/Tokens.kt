package dev.vaultdrop.ui

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.vaultdrop.ui.theme.Mono
import dev.vaultdrop.ui.theme.Ui

/** The design's type scale, transcribed. Colour is applied at the call site. */
object T {
    val date = TextStyle(fontFamily = Mono, fontSize = 16.sp, letterSpacing = (-0.01).em)
    val newTag = TextStyle(fontFamily = Mono, fontSize = 10.sp)
    val chipText = TextStyle(fontFamily = Mono, fontSize = 11.sp, letterSpacing = 0.02.em)
    val fmBody = TextStyle(fontFamily = Mono, fontSize = 12.5.sp, lineHeight = 20.6.sp)
    val note = TextStyle(
        fontFamily = Mono, fontSize = 15.sp, lineHeight = 22.5.sp, letterSpacing = (-0.003).em,
    )

    val stripBrackets = TextStyle(fontFamily = Mono, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
    val stripQuery = TextStyle(fontFamily = Mono, fontSize = 12.5.sp)
    val stripCount = TextStyle(fontFamily = Mono, fontSize = 10.5.sp)
    val stripTitle = TextStyle(fontFamily = Ui, fontSize = 14.sp)
    val stripPath = TextStyle(fontFamily = Mono, fontSize = 10.5.sp)
    val indexing = TextStyle(fontFamily = Mono, fontSize = 12.5.sp)

    val notice = TextStyle(fontFamily = Mono, fontSize = 12.sp, lineHeight = 18.6.sp)
    val noticeAction = TextStyle(
        fontFamily = Ui, fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.06.em,
    )
    val swipeHint = TextStyle(fontFamily = Mono, fontSize = 17.sp)

    val sheetLabel = TextStyle(fontFamily = Mono, fontSize = 15.sp)
    val sheetSub = TextStyle(fontFamily = Mono, fontSize = 10.5.sp)

    val eyebrow = TextStyle(
        fontFamily = Mono, fontSize = 10.5.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.24.em,
    )
    val eyebrowTight = TextStyle(
        fontFamily = Mono, fontSize = 10.5.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.em,
    )
    val headline = TextStyle(
        fontFamily = Ui, fontSize = 28.sp, lineHeight = 34.7.sp, letterSpacing = (-0.01).em,
    )
    val lede = TextStyle(fontFamily = Ui, fontSize = 14.sp, lineHeight = 22.4.sp)
    val capability = TextStyle(fontFamily = Mono, fontSize = 11.5.sp, lineHeight = 24.2.sp)
    val button = TextStyle(fontFamily = Ui, fontSize = 15.sp, fontWeight = FontWeight.Medium)

    val verifyLabel = TextStyle(fontFamily = Mono, fontSize = 12.5.sp)
    val verifyValue = TextStyle(fontFamily = Mono, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
    val verifyFoot = TextStyle(fontFamily = Mono, fontSize = 11.5.sp, lineHeight = 19.6.sp)

    val cardTitle = TextStyle(fontFamily = Ui, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    val cardBody = TextStyle(fontFamily = Mono, fontSize = 11.5.sp, lineHeight = 18.4.sp)
    val cardRow = TextStyle(fontFamily = Mono, fontSize = 11.5.sp)

    val searchInput = TextStyle(fontFamily = Mono, fontSize = 15.sp)
    val searchStats = TextStyle(fontFamily = Mono, fontSize = 11.sp, letterSpacing = 0.04.em)
    val resultTitle = TextStyle(fontFamily = Mono, fontSize = 15.sp)
    val resultSnippet = TextStyle(fontFamily = Mono, fontSize = 12.sp, lineHeight = 18.sp)
    val resultPath = TextStyle(fontFamily = Mono, fontSize = 10.sp)
    val openTitle = TextStyle(fontFamily = Mono, fontSize = 16.sp)
    val openPath = TextStyle(fontFamily = Mono, fontSize = 10.sp)
    val empty = TextStyle(fontFamily = Mono, fontSize = 12.5.sp, lineHeight = 20.6.sp)

    /** Every tap target in the chrome is the same 44dp circle. */
    val touch = 44.dp
    val barHeight = 56.dp
}
