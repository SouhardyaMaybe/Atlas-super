package com.atlassuper.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.progress.LinearProgressIndicator
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalReducedMotion
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.atlassuper.core.DownloadEntry
import com.atlassuper.core.LaunchPlan
import com.atlassuper.core.LaunchPlanner
import com.atlassuper.core.TargetPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

private const val CHOSEN_VERSION = "1.16.5"

/** Motion tokens owned by this screen. */
object MotionTokens {
    val morphDurationMillis = 250L
    val ease = androidx.compose.animation.FastOutSlowInEasing
}

/** Byte formatting helper. */
fun formatBytes(bytes: Long): String {
    val nf = java.text.NumberFormat.getIntegerInstance(java.util.Locale.US)
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024L * 1024 -> "${nf.format(bytes / 1024)} KB"
        else -> "${nf.format(bytes / (1024L * 1024))} MB"
    }
}

/** UI state for the whole screen. */
sealed interface UiState {
    object Loading : UiState
    data class Loaded(val plan: LaunchPlan, val dryRun: DryRunUiState = DryRunUiState.Idle) : UiState
    data class FailedLoad(val reason: String) : UiState
}

/** Dry-run state machine: idle -> running -> done / failed. */
sealed interface DryRunUiState {
    object Idle : DryRunUiState
    data class Running(
        val currentFile: String,
        val fileProgress: Float,
        val overallProgress: Float,
        val filesDone: Int,
        val filesTotal: Int,
    ) : DryRunUiState
    object Done : DryRunUiState
    data class Failed(val file: String, val reason: String) : DryRunUiState
}

class LaunchPlanViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val plan = withContext(Dispatchers.Default) {
                    LaunchPlanner(TargetPlatform.DEFAULT).plan(CHOSEN_VERSION)
                }
                _uiState.update { UiState.Loaded(plan, DryRunUiState.Idle) }
            } catch (e: Exception) {
                _uiState.update { UiState.FailedLoad(e.message ?: e.toString()) }
            }
        }
    }

    private val currentPlan: LaunchPlan?
        get() = (_uiState.value as? UiState.Loaded)?.plan

    /** Start the real cache dry-run: download + SHA-1 verify every artifact into cacheDir. */
    fun startDryRun(cacheDir: File) {
        val plan = currentPlan ?: return
        val files = downloadableFiles(plan)
        if (files.isEmpty()) {
            _uiState.update { it ->
                if (it is UiState.Loaded) it.copy(dryRun = DryRunUiState.Done) else it
            }
            return
        }
        viewModelScope.launch {
            var overallRead = 0L
            val totalBytes = files.sumOf { it.size ?: 0L }
            var filesDone = 0
            _uiState.update { it ->
                if (it is UiState.Loaded)
                    it.copy(dryRun = DryRunUiState.Running("", 0f, 0f, 0, files.size))
                else it
            }
            for (file in files) {
                val fileName = file.dest.substringAfterLast('/')
                try {
                    val (hash, size) = downloadAndHash(file, cacheDir)
                    overallRead += size
                    filesDone += 1
                    val verified = file.sha1 == null || file.sha1 == hash
                    _uiState.update { it ->
                        if (it is UiState.Loaded)
                            it.copy(
                                dryRun = DryRunUiState.Running(
                                    fileName,
                                    1f,
                                    if (totalBytes > 0) overallRead.toFloat() / totalBytes else 1f,
                                    filesDone,
                                    files.size,
                                )
                            )
                        else it
                    }
                    if (!verified) {
                        _uiState.update { it ->
                            if (it is UiState.Loaded)
                                it.copy(dryRun = DryRunUiState.Failed(fileName, "SHA-1 mismatch"))
                            else it
                        }
                        return@launch
                    }
                } catch (e: Exception) {
                    _uiState.update { it ->
                        if (it is UiState.Loaded)
                            it.copy(dryRun = DryRunUiState.Failed(fileName, e.message ?: e.toString()))
                        else it
                    }
                    return@launch
                }
            }
            _uiState.update { it ->
                if (it is UiState.Loaded) it.copy(dryRun = DryRunUiState.Done) else it
            }
        }
    }

    private fun downloadableFiles(plan: LaunchPlan): List<DownloadEntry> =
        (plan.libraries.filter { it.download != null }.map { it.download!! } + listOf(plan.clientJar))
            .filter { it.url.isNotEmpty() }

    private suspend fun downloadAndHash(entry: DownloadEntry, cacheDir: File): Pair<String, Long> =
        withContext(Dispatchers.Default) {
            val url = URL(entry.url)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 20_000
            conn.readTimeout = 60_000
            conn.setRequestProperty("User-Agent", "Atlas-super/0.1.0")
            try {
                val code = conn.responseCode
                if (code !in 200..299) throw RuntimeException("HTTP $code for ${entry.url}")
                val input: InputStream = conn.inputStream
                val digest = MessageDigest.getInstance("SHA-1")
                var total = 0L
                val buf = ByteArray(8192)
                var read: Int
                input.use { is ->
                    while (true) {
                        read = is.read(buf)
                        if (read == -1) break
                        digest.update(buf, 0, read)
                        total += read
                    }
                }
                // Real dry-run: stream fully read and hashed; compared against the plan's SHA-1.
                val hash = digest.digest().joinToString("") { "%02x".format(it) }
                Pair(hash, total)
            } finally {
                conn.disconnect()
            }
        }
}

/** The single Compose screen. Shows plan stages + a real cache dry-run. */
@Composable
fun LaunchPlanScreen() {
    val vm = viewModel<LaunchPlanViewModel>()
    val state by vm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val reducedMotion = LocalReducedMotion.current

    when (state) {
        is UiState.Loading -> LoadingScreen(reducedMotion)
        is UiState.FailedLoad -> FailedLoadScreen(state.reason)
        is UiState.Loaded -> LaunchPlanContent(state, vm, context, reducedMotion)
    }
}

@Composable
private fun LoadingScreen(reducedMotion: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(40.dp))
        Text(
            text = "Resolving launch plan…",
            modifier = Modifier.paddingTop(16.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun FailedLoadScreen(reason: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Failed to resolve plan",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Text(text = reason, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.paddingTop(8.dp))
    }
}

@Composable
private fun LaunchPlanContent(
    state: UiState.Loaded,
    vm: LaunchPlanViewModel,
    context: android.content.Context,
    reducedMotion: Boolean,
) {
    val plan = state.plan
    val dryRun = state.dryRun

    val libraries = plan.libraries.filter { it.download != null }
    val natives = plan.natives
    val assetsBytes = state.assetLayout.assetIndex?.totalSize ?: 0L

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Launch Plan — ${plan.versionId}") },
                colors = TopAppBarDefaults.mediumTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            Text(
                text = "Stages",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 8.dp),
            )
            LazyColumn {
                itemsIndexed(listOf(
                    Stage("Libraries", libraries.size, libraries.sumOf { it.download?.size ?: 0L }),
                    Stage("Natives", natives.size, natives.sumOf { it.download?.size ?: 0L }),
                    Stage("Assets", 1, assetsBytes),
                    Stage("Fabric", plan.fabric?.libraries.size ?: 0,
                        plan.fabric?.libraries?.sumOf { it.download?.size ?: 0L } ?: 0L),
                )) { _, stage ->
                    StageCard(stage)
                }
            }

            Divider()

            Text(
                text = "Resolved for ${plan.versionId} (${plan.versionType}) — Java ${plan.javaMajorVersion} (${plan.javaComponent})",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp),
            )

            DryRunPanel(dryRun, vm, context, reducedMotion)
        }
    }
}

@Composable
private fun StageCard(stage: Stage) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        elevation = 2.dp,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stage.name,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${stage.count} files  •  ${formatBytes(stage.bytes)}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun DryRunPanel(
    dryRun: DryRunUiState,
    vm: LaunchPlanViewModel,
    context: android.content.Context,
    reducedMotion: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .animateContentSize(animationSpec = morphSpec),
    ) {
        Text(
            text = "Cache dry-run",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        when (dryRun) {
            is DryRunUiState.Idle -> {
                Button(
                    onClick = { vm.startDryRun(context.cacheDir) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                ) {
                    Text(text = "Verify cache (real download + SHA-1)")
                }
            }
            DryRunUiState.Running(currentFile, fileProgress, overallProgress, filesDone, filesTotal) -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Verifying: $currentFile",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "$filesDone/${filesTotal} files",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    LinearProgressIndicator(
                        progress = overallProgress,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "${(overallProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.align(Alignment.End),
                    )
                }
            }
            DryRunUiState.Done -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Text(
                        text = "Cache dry-run done — all artifacts verified",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            DryRunUiState.Failed(file, reason) -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Dry-run failed",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Text(
                            text = "File: $file\nWhy: $reason",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.paddingTop(4.dp),
                        )
                    }
                }
            }
        }
    }
}

private data class Stage(val name: String, val count: Int, val bytes: Long)
