package app.openbubbles.nativeapp.ui.attachmentviewer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import app.openbubbles.nativeapp.ui.common.rememberPdfPreview
import java.io.File

/** In-app PDF page viewer. Swipe between rendered pages. */
@Composable
fun AttachmentPdfViewer(
    file: File,
    name: String?,
    pageCount: Int,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(pageCount = { pageCount.coerceAtLeast(1) })
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) { page ->
            val decoded = rememberPdfPreview(file = file, maxDimensionPx = 2048, pageIndex = page)
            if (decoded == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Could not render page ${page + 1}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                }
            } else {
                Image(
                    bitmap = decoded.image,
                    contentDescription = name ?: "PDF page ${page + 1}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        if (pageCount > 1) {
            Surface(
                color = Color.Black.copy(alpha = 0.45f),
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 20.dp),
            ) {
                Text(
                    text = "${pagerState.currentPage + 1} / $pageCount",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}
