package com.example.presentation.navigation

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.creator.StickerEditorMode
import com.example.presentation.StickerCreatorEvent
import com.example.presentation.StickerViewModel
import com.example.presentation.dialogs.ExportDialog
import com.example.presentation.screens.AboutScreen
import com.example.presentation.screens.DiagnosticsScreen
import com.example.presentation.screens.ImportScreen
import com.example.presentation.screens.PackDetailScreen
import com.example.presentation.screens.PackListScreen
import com.example.presentation.screens.AiCropScreen
import com.example.presentation.screens.AiStickerScreen
import com.example.presentation.screens.SettingsScreen
import com.example.presentation.screens.StickerEditorScreen
import com.example.presentation.screens.StickerStudioScreen
import com.example.presentation.screens.StudioPreviewScreen
import com.example.presentation.screens.TextStudioMode
import com.example.presentation.screens.TextStudioScreen
import com.example.wastickers.WastickersImportScreen

sealed class Screen {
    data object PackList : Screen()
    data object Studio : Screen()
    data class TextStudio(val mode: TextStudioMode) : Screen()
    data object AiSticker : Screen()
    data object AiCrop : Screen()
    data object Settings : Screen()
    data class StudioPreview(val imageId: String) : Screen()
    data class Editor(
        val uriString: String?,
        val mode: StickerEditorMode,
        val studioImageId: String? = null,
        val studioImageName: String? = null,
        val studioTextMode: Boolean = false
    ) : Screen()
    data class Import(val packId: Long? = null) : Screen()
    data object WastickersBrowser : Screen()
    data class PackDetail(val packId: Long) : Screen()
    data object About : Screen()
    data object Diagnostics : Screen()
}

@Composable
fun StickerApp(
    viewModel: StickerViewModel = viewModel()
) {
    val packs by viewModel.packs.collectAsState()
    val packPreviews by viewModel.packPreviews.collectAsState()
    val currentPack by viewModel.currentPack.collectAsState()
    val currentStickers by viewModel.currentStickers.collectAsState()
    val conversionProgress by viewModel.conversionProgress.collectAsState()
    val isImporting by viewModel.isImporting.collectAsState()
    val detectedItems by viewModel.importDetectedItems.collectAsState()
    val packExportTarget by viewModel.packExportTarget.collectAsState()
    val studioImages by viewModel.studioImages.collectAsState()
    val isCreatingStudioPack by viewModel.isCreatingStudioPack.collectAsState()

    var currentScreen by remember { mutableStateOf<Screen>(Screen.PackList) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.creatorEvents.collect { event ->
            when (event) {
                is StickerCreatorEvent.Saved -> {
                    viewModel.selectPack(event.packId)
                    currentScreen = Screen.PackDetail(event.packId)
                }
                is StickerCreatorEvent.Failed -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Enforce Arabic RTL layout as requested
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            when (val screen = currentScreen) {
                is Screen.PackList -> {
                    PackListScreen(
                        packs = packs,
                        packPreviews = packPreviews,
                        onOpenPack = { packId ->
                            viewModel.selectPack(packId)
                            currentScreen = Screen.PackDetail(packId)
                        },
                        onCreatePack = { name, shortName, author, defaultEmoji ->
                            viewModel.createPack(name, shortName, author, defaultEmoji) { newId ->
                                currentScreen = Screen.PackDetail(newId)
                            }
                        },
                        onRenamePack = { packId, newName ->
                            viewModel.renamePack(packId, newName)
                        },
                        onDeletePack = { packId ->
                            viewModel.deletePack(packId)
                        },
                        onDeletePacks = { packIds ->
                            viewModel.deletePacks(packIds)
                        },
                        onExportPack = { packId ->
                            viewModel.openExportForPack(packId)
                        },
                        onOpenStudio = {
                            currentScreen = Screen.Studio
                        },
                        onNavigateToImport = {
                            currentScreen = Screen.Import()
                        },
                        onNavigateToAbout = {
                            currentScreen = Screen.About
                        },
                        onNavigateToDiagnostics = {
                            currentScreen = Screen.Diagnostics
                        }
                    )
                }

                is Screen.Studio -> {
                    BackHandler {
                        currentScreen = Screen.PackList
                    }

                    StickerStudioScreen(
                        images = studioImages,
                        onNavigateBack = { currentScreen = Screen.PackList },
                        onAddImages = { uris -> viewModel.addStudioImages(uris) },
                        onPreviewImage = { imageId ->
                            currentScreen = Screen.StudioPreview(imageId)
                        },
                        onEditImage = { image ->
                            currentScreen = Screen.Editor(
                                uriString = viewModel.studioImageUri(image),
                                mode = StickerEditorMode.IMAGE,
                                studioImageId = image.id,
                                studioImageName = image.name
                            )
                        },
                        onDeleteImage = { imageId -> viewModel.deleteStudioImage(imageId) },
                        onCreateText = {
                            currentScreen = Screen.TextStudio(TextStudioMode.PLAIN_TEXT)
                        },
                        onOpen3DStudio = {
                            currentScreen = Screen.TextStudio(TextStudioMode.TEXT_3D)
                        },
                        onOpenAiStudio = {
                            currentScreen = Screen.AiSticker
                        },
                        onCreatePack = { selectedImages ->
                            viewModel.createPackFromStudioImages(
                                images = selectedImages,
                                onCreated = { currentScreen = Screen.PackList },
                                onError = { message ->
                                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                }
                            )
                        },
                        isCreatingPack = isCreatingStudioPack
                    )
                }

                is Screen.TextStudio -> {
                    BackHandler {
                        currentScreen = Screen.Studio
                    }

                    TextStudioScreen(
                        packs = packs,
                        initialMode = screen.mode,
                        onNavigateBack = { currentScreen = Screen.Studio },
                        onSaveToStudio = { bitmap, name ->
                            viewModel.saveStudioImage(null, name, bitmap) {
                                currentScreen = Screen.Studio
                            }
                        },
                        onSaveSticker = { bitmap, name, emoji, packId, newPackName ->
                            viewModel.saveCreatedSticker(bitmap, name, emoji, packId, newPackName)
                        }
                    )
                }

                is Screen.AiSticker -> {
                    BackHandler {
                        currentScreen = Screen.Studio
                    }

                    AiStickerScreen(
                        onNavigateBack = { currentScreen = Screen.Studio },
                        onOpenSettings = { currentScreen = Screen.Settings },
                        onOpenCrop = { currentScreen = Screen.AiCrop },
                        onCreatePack = { images, name, emoji ->
                            viewModel.createAiPack(
                                images,
                                name,
                                emoji,
                                onCreated = { packId ->
                                    currentScreen = Screen.PackDetail(packId)
                                }
                            )
                        },
                        onSaveImages = { images, name ->
                            viewModel.saveAiImagesToGallery(
                                images = images,
                                baseName = name,
                                onComplete = { saved ->
                                    Toast.makeText(
                                        context,
                                        "تم حفظ $saved صورة في المعرض",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                onError = { message ->
                                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                }
                            )
                        }
                    )
                }

                is Screen.AiCrop -> {
                    BackHandler {
                        currentScreen = Screen.AiSticker
                    }

                    AiCropScreen(
                        onNavigateBack = { currentScreen = Screen.AiSticker },
                        onSaveImages = { images, name ->
                            viewModel.saveAiImagesToGallery(
                                images = images,
                                baseName = name
                            )
                        }
                    )
                }

                is Screen.Settings -> {
                    BackHandler {
                        currentScreen = Screen.AiSticker
                    }

                    SettingsScreen(
                        onNavigateBack = { currentScreen = Screen.AiSticker }
                    )
                }

                is Screen.StudioPreview -> {
                    BackHandler {
                        currentScreen = Screen.Studio
                    }

                    StudioPreviewScreen(
                        images = studioImages,
                        initialId = screen.imageId,
                        onNavigateBack = { currentScreen = Screen.Studio },
                        onEditImage = { image ->
                            currentScreen = Screen.Editor(
                                uriString = viewModel.studioImageUri(image),
                                mode = StickerEditorMode.IMAGE,
                                studioImageId = image.id,
                                studioImageName = image.name
                            )
                        }
                    )
                }

                is Screen.Editor -> {
                    BackHandler {
                        currentScreen = Screen.Studio
                    }

                    StickerEditorScreen(
                        mode = screen.mode,
                        initialUri = screen.uriString,
                        packs = packs,
                        onNavigateBack = { currentScreen = Screen.Studio },
                        onSaveSticker = { bitmap, name, emoji, packId, newPackName ->
                            viewModel.saveCreatedSticker(bitmap, name, emoji, packId, newPackName)
                        },
                        studioImageId = screen.studioImageId,
                        studioImageName = screen.studioImageName,
                        onSaveStudioImage = if (
                            screen.studioImageId != null || screen.studioTextMode
                        ) {
                            { id, name, bitmap ->
                                viewModel.saveStudioImage(id, name, bitmap) {
                                    currentScreen = Screen.Studio
                                }
                            }
                        } else {
                            null
                        }
                    )
                }

                is Screen.Import -> {
                    BackHandler {
                        currentScreen = if (screen.packId != null) {
                            Screen.PackDetail(screen.packId)
                        } else {
                            Screen.PackList
                        }
                    }

                    ImportScreen(
                        packs = packs,
                        initialPackId = screen.packId,
                        isImporting = isImporting,
                        detectedItems = detectedItems,
                        onInspectUris = { uris ->
                            viewModel.inspectUris(uris)
                        },
                        onImportZip = { uri, onComplete, onError ->
                            viewModel.importZipArchive(uri, null, null, onComplete, onError)
                        },
                        onCommitImport = { packId, items, onComplete ->
                            viewModel.commitImportToPack(packId, items) {
                                onComplete()
                            }
                        },
                        onCreatePackAndImport = { name, items, onComplete ->
                            viewModel.createPack(name, "pack_" + System.currentTimeMillis() % 10000, "", "✨") { newPackId ->
                                viewModel.commitImportToPack(newPackId, items) {
                                    onComplete(newPackId)
                                }
                            }
                        },
                        onNavigateBack = {
                            currentScreen = if (screen.packId != null) {
                                Screen.PackDetail(screen.packId)
                            } else {
                                Screen.PackList
                            }
                        },
                        onOpenPack = { packId ->
                            viewModel.selectPack(packId)
                            currentScreen = Screen.PackDetail(packId)
                        },
                        onBrowseDeviceWastickers = {
                            currentScreen = Screen.WastickersBrowser
                        }
                    )
                }

                is Screen.WastickersBrowser -> {
                    BackHandler {
                        currentScreen = Screen.Import()
                    }

                    WastickersImportScreen(
                        onNavigateBack = { currentScreen = Screen.Import() },
                        onOpenPack = { packId ->
                            viewModel.selectPack(packId)
                            currentScreen = Screen.PackDetail(packId)
                        }
                    )
                }

                is Screen.PackDetail -> {
                    BackHandler {
                        currentScreen = Screen.PackList
                    }

                    if (currentPack != null && currentPack!!.id == screen.packId) {
                        PackDetailScreen(
                            pack = currentPack!!,
                            stickers = currentStickers,
                            conversionProgress = conversionProgress,
                            onNavigateBack = { currentScreen = Screen.PackList },
                            onNavigateToImport = { packId -> currentScreen = Screen.Import(packId) },
                            onUpdateStickerName = { id, name -> viewModel.updateStickerName(id, name) },
                            onUpdateStickerEmoji = { id, emoji -> viewModel.updateStickerEmoji(id, emoji) },
                            onGenerateNamesAutomatically = { prefix -> viewModel.generateNamesAutomatically(prefix) },
                            onDeleteSticker = { id -> viewModel.deleteSticker(id) },
                            onMoveStickerUp = { id -> viewModel.moveStickerUp(id) },
                            onMoveStickerDown = { id -> viewModel.moveStickerDown(id) },
                            onStartConversion = { list -> viewModel.startConversion(list) },
                            onUpdatePack = { pack -> viewModel.updatePack(pack) },
                            onReprocessSticker = { id -> viewModel.reprocessSingleSticker(id) },
                            onCancelConversion = { viewModel.cancelConversion() },
                            onDismissConversionProgress = { viewModel.dismissConversionDialog() },
                            onValidateSticker = { item -> viewModel.validateSticker(item) }
                        )
                    } else {
                        // Reload pack if needed
                        viewModel.selectPack(screen.packId)
                    }
                }

                is Screen.About -> {
                    BackHandler {
                        currentScreen = Screen.PackList
                    }

                    AboutScreen(
                        onNavigateBack = { currentScreen = Screen.PackList }
                    )
                }

                is Screen.Diagnostics -> {
                    BackHandler {
                        currentScreen = Screen.PackList
                    }

                    DiagnosticsScreen(
                        onNavigateBack = { currentScreen = Screen.PackList }
                    )
                }
            }

            // Global export dialog, opened from the pack list quick action.
            packExportTarget?.let { target ->
                ExportDialog(
                    pack = target.pack,
                    stickers = target.stickers,
                    onDismiss = { viewModel.closeExportDialog() },
                    onUpdatePack = { pack -> viewModel.updatePack(pack) }
                )
            }
        }
    }
}
