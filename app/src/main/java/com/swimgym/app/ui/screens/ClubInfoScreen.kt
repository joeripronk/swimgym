package com.swimgym.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.swimgym.app.domain.model.ClubNews
import com.swimgym.app.domain.model.CreditItem
import com.swimgym.app.ui.viewmodel.ClubInfoViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClubInfoScreen(
    viewModel: ClubInfoViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    var fullscreenImage by remember { mutableStateOf<String?>(null) }
    val newsListState = rememberLazyListState()

    LaunchedEffect(Unit) {
        if (uiState.news.isEmpty() && uiState.credits.isEmpty()) {
            viewModel.load()
        }
    }

    val shouldLoadMoreNews by remember {
        derivedStateOf {
            val lastVisible = newsListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= uiState.news.size - 3
        }
    }
    LaunchedEffect(selectedTab, shouldLoadMoreNews, uiState.newsPage, uiState.news.size) {
        if (selectedTab == 0 && shouldLoadMoreNews && uiState.news.isNotEmpty()) {
            viewModel.loadMoreNews()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SwimGym Info") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.load() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("News (${uiState.news.size})") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Credits (${uiState.credits.size})") }
                )
            }

            when {
                uiState.isLoading && uiState.news.isEmpty() && uiState.credits.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                uiState.error != null && uiState.news.isEmpty() && uiState.credits.isEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = uiState.error ?: "Error",
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { viewModel.load() }) { Text("Retry") }
                    }
                }
                selectedTab == 0 -> {
                    if (uiState.news.isEmpty()) {
                        EmptyMessage("No news available")
                    } else {
                        LazyColumn(
                            state = newsListState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(uiState.news, key = { it.id }) { item ->
                                NewsCard(
                                    item = item,
                                    onClick = { viewModel.openNews(item) },
                                    onImageClick = { url -> fullscreenImage = url }
                                )
                            }
                            item(key = "news-footer") {
                                when {
                                    uiState.isLoadingMoreNews -> Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Loading more news...")
                                    }
                                    uiState.newsLoadMoreError != null -> Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = uiState.newsLoadMoreError ?: "Error",
                                            color = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.weight(1f)
                                        )
                                        TextButton(onClick = { viewModel.loadMoreNews() }) {
                                            Text("Retry")
                                        }
                                    }
                                    uiState.newsEndReached -> Text(
                                        text = "No more news",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
                else -> {
                    if (uiState.credits.isEmpty()) {
                        EmptyMessage("No credits information available")
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(uiState.credits, key = { it.name }) { item ->
                                CreditRow(item = item)
                            }
                        }
                    }
                }
            }
        }
    }

    uiState.selectedNews?.let { news ->
        NewsDetailView(
            news = news,
            isLoading = uiState.isLoadingNewsDetail,
            onClose = { viewModel.closeNews() },
            onImageClick = { url -> fullscreenImage = url }
        )
    }

    fullscreenImage?.let { url ->
        NewsImageViewer(imageUrl = url, onClose = { fullscreenImage = null })
    }
}

@Composable
private fun EmptyMessage(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun NewsCard(
    item: ClubNews,
    onClick: () -> Unit,
    onImageClick: (String) -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("d MMM yyyy", Locale.getDefault()) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors()
    ) {
        Row(modifier = Modifier.padding(16.dp)) {
            if (item.authorImageUrl.isNotBlank()) {
                AsyncImage(
                    model = item.authorImageUrl,
                    contentDescription = "Author image",
                    modifier = Modifier.size(40.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.clickable(onClick = onClick)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.author.ifBlank { "SwimGym" },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        val dateLabel = if (item.timestamp > 0) {
                            dateFormat.format(Date(item.timestamp))
                        } else {
                            item.dateLabel
                        }
                        if (dateLabel.isNotBlank()) {
                            Text(
                                text = dateLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = item.message,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (item.imageUrl.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    AsyncImage(
                        model = item.imageUrl,
                        contentDescription = "News image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .clickable { onImageClick(item.imageUrl) },
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }
    }
}

@Composable
private fun CreditRow(item: CreditItem) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = item.name, style = MaterialTheme.typography.bodyLarge)
            val tint = when {
                item.isUnlimited || (item.count ?: 0) > 0 -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Text(
                text = item.amount,
                style = MaterialTheme.typography.titleSmall,
                color = tint
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewsDetailView(
    news: ClubNews,
    isLoading: Boolean,
    onClose: () -> Unit,
    onImageClick: (String) -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("d MMMM yyyy HH:mm", Locale.getDefault()) }
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("News") },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .padding(12.dp)
                                    .size(24.dp)
                            )
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (news.authorImageUrl.isNotBlank()) {
                        AsyncImage(
                            model = news.authorImageUrl,
                            contentDescription = "Author image",
                            modifier = Modifier.size(44.dp).clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.width(12.dp))
                    }
                    Column {
                        Text(
                            text = news.author.ifBlank { "SwimGym" },
                            style = MaterialTheme.typography.titleMedium
                        )
                        val date = if (news.timestamp > 0) {
                            dateFormat.format(Date(news.timestamp))
                        } else {
                            news.dateLabel
                        }
                        if (date.isNotBlank()) {
                            Text(
                                text = date,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(text = news.message, style = MaterialTheme.typography.bodyLarge)
                if (news.imageUrl.isNotBlank()) {
                    Spacer(Modifier.height(16.dp))
                    AsyncImage(
                        model = news.imageUrl,
                        contentDescription = "News image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .clickable { onImageClick(news.imageUrl) },
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }
    }
}

@Composable
private fun NewsImageViewer(imageUrl: String, onClose: () -> Unit) {
    var scale by remember { mutableStateOf(1f) }
    var rotation by remember { mutableStateOf(0f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, rotationChange ->
                        scale = (scale * zoom).coerceIn(1f, 8f)
                        rotation += rotationChange
                        offset += pan
                    }
                }
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = "News image",
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        rotationZ = rotation
                        translationX = offset.x
                        translationY = offset.y
                    },
                contentScale = ContentScale.Fit
            )
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
            IconButton(
                onClick = { rotation = (rotation + 90f) % 360f },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Rotate", tint = Color.White)
            }
        }
    }
}
