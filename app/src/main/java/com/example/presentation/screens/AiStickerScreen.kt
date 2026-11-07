package com.example.presentation.screens

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ai.GeminiStickerClient
import com.example.data.settings.AppSettingsRepository
import com.example.presentation.components.AuroraDialog
import com.example.presentation.components.AuroraGradientButton
import kotlinx.coroutines.launch

private data class AiChoice(
    val label: String,
    val prompt: String
)

private data class AiStickerOptions(
    val category: AiChoice,
    val style: AiChoice,
    val character: AiChoice,
    val expression: AiChoice,
    val effects: Set<String>,
    val customCharacter: String,
    val description: String,
    val count: Int,
    val dimension: String,
    val quality: String,
    val transparent: Boolean,
    val textMode: String,
    val customText: String,
    val output: String
)

private val AI_CATEGORIES = listOf(
    AiChoice("😂 ملصقات مضحكة", "funny comic sticker"),
    AiChoice("❤️ ملصقات حب ورومانسية", "romantic love sticker"),
    AiChoice("😍 ملصقات تعبيرية", "expressive emoji sticker"),
    AiChoice("🐱 حيوانات كرتونية", "cartoon animal sticker"),
    AiChoice("👨 شخصيات كرتونية", "cartoon character sticker"),
    AiChoice("🤖 شخصيات ثلاثية الأبعاد", "3D character sticker"),
    AiChoice("🌸 ملصقات لطيفة", "cute kawaii sticker"),
    AiChoice("💬 ملصقات محادثات", "chat reaction sticker"),
    AiChoice("🎮 شخصيات ألعاب", "video game character sticker"),
    AiChoice("🎉 مناسبات واحتفالات", "celebration occasion sticker")
)

private val AI_STYLES = listOf(
    AiChoice("3D", "glossy 3D render with soft studio lighting"),
    AiChoice("كرتوني", "cartoon illustration with bold outlines"),
    AiChoice("أنمي", "anime style with expressive eyes"),
    AiChoice("تشيبي", "chibi style with big head and cute proportions"),
    AiChoice("واقعي", "realistic detailed rendering"),
    AiChoice("بيكسل", "pixel art style"),
    AiChoice("Clay 3D", "clay 3D render with soft matte texture"),
    AiChoice("رسم يدوي", "hand drawn illustration"),
    AiChoice("فني وأنيق", "elegant artistic design"),
    AiChoice("إيموجي", "emoji style with flat vibrant colors")
)

private val AI_CHARACTERS = listOf(
    AiChoice("رجل", "a friendly man"),
    AiChoice("امرأة", "a friendly woman"),
    AiChoice("طفل", "a cute child"),
    AiChoice("حيوان", "a cute cartoon animal"),
    AiChoice("خيالية", "a fantasy creature"),
    AiChoice("روبوت", "a friendly robot"),
    AiChoice("مخصص", "an original custom character")
)

private val AI_EXPRESSIONS = listOf(
    AiChoice("سعيد", "happy and smiling"),
    AiChoice("حزين", "sad and emotional"),
    AiChoice("غاضب", "angry and exaggerated"),
    AiChoice("متفاجئ", "surprised with wide eyes"),
    AiChoice("يضحك", "laughing loudly"),
    AiChoice("يبكي", "crying with tears"),
    AiChoice("يغمز", "winking playfully"),
    AiChoice("قبلة", "blowing a kiss"),
    AiChoice("يلوّح", "waving hello"),
    AiChoice("إبهام", "giving a thumbs up")
)

private val AI_EFFECTS = listOf(
    AiChoice("ذهبي", "golden metallic accents"),
    AiChoice("فضي", "silver metallic accents"),
    AiChoice("نيون", "neon glow"),
    AiChoice("باستيل", "pastel color palette"),
    AiChoice("زاهية", "vivid bright colors"),
    AiChoice("داكنة", "dark moody palette"),
    AiChoice("توهج", "soft glowing aura"),
    AiChoice("حدود بيضاء", "thick white outline border")
)

private val AI_IDEAS = listOf(
    "قطة صغيرة تضحك وترتدي نظارة شمسية",
    "شخصية كرتونية تلوّح بيدها بابتسامة كبيرة",
    "كلب لطيف يحتفل بعيد ميلاد مع بالونات",
    "روبوت لامع يعطي إبهامًا للأعلى",
    "قلب كرتوني يحمل وردة حمراء",
    "طفل سعيد يقفز من الفرح",
    "أرنب صغير يشرب القهوة",
    "شخصية أنمي تغمز بعينها",
    "دب لطيف يرتدي قبعة احتفالية",
    "قطة نينجا بشفافية وخلفية ملونة"
)

private fun defaultAiOptions(): AiStickerOptions {
    return AiStickerOptions(
        category = AI_CATEGORIES.first(),
        style = AI_STYLES.first(),
        character = AI_CHARACTERS.first(),
        expression = AI_EXPRESSIONS.first(),
        effects = emptySet(),
        customCharacter = "",
        description = "",
        count = 1,
        dimension = "مربع",
        quality = "1K",
        transparent = true,
        textMode = "بدون",
        customText = "",
        output = "ملصق"
    )
}

private fun aspectRatioFor(dimension: String): String {
    return when (dimension) {
        "عمودي" -> "3:4"
        "أفقي" -> "4:3"
        else -> "1:1"
    }
}

private fun qualityHint(quality: String): String {
    return when (quality) {
        "512" -> "512 px"
        "2K" -> "2048 px"
        else -> "1024 px"
    }
}

private fun buildStickerPrompt(options: AiStickerOptions): String {
    val description = options.description.trim().ifBlank {
        "a cute expressive character inspired by ${options.category.prompt}"
    }
    val character = when {
        options.character.label == "مخصص" && options.customCharacter.trim().isNotBlank() -> {
            options.customCharacter.trim()
        }
        options.character.label == "مخصص" -> "an original custom character"
        else -> options.character.prompt
    }
    val effects = AI_EFFECTS
        .filter { it.label in options.effects }
        .joinToString(", ") { it.prompt }
        .ifBlank { "clean vibrant colors" }
    val textInstruction = when {
        options.textMode == "مخصص" && options.customText.trim().isNotBlank() -> {
            "Include the Arabic text \"${options.customText.trim()}\" inside the image clearly and correctly."
        }
        options.textMode == "مخصص" -> {
            "Include a short readable text inside the image."
        }
        else -> {
            "Do not include any text inside the image."
        }
    }
    val background = if (options.transparent) {
        "Transparent background, no background, crisp cutout edges, PNG sticker style."
    } else {
        "Colored vibrant background that matches the theme."
    }
    val output = if (options.output == "ملصق") {
        "WhatsApp and Telegram sticker"
    } else {
        "high quality image"
    }
    val ratio = aspectRatioFor(options.dimension)
    val quality = qualityHint(options.quality)

    return buildString {
        append("أنشئ $output عالي الجودة بالذكاء الاصطناعي. ")
        append("Subject: $description. ")
        append("Category: ${options.category.prompt}. ")
        append("Art style: ${options.style.prompt}. ")
        append("Character: $character. ")
        append("Expression: ${options.expression.prompt}. ")
        append("Colors and effects: $effects. ")
        append("Background: $background ")
        append(textInstruction)
        append(" ")
        append("Composition: centered, bold clean outlines, expressive, sticker-ready, no watermark. ")
        append("Aspect ratio $ratio, quality $quality.")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiStickerScreen(
    onNavigateBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onSaveImages: (List<Bitmap>, String) -> Unit,
    onCreatePack: ((List<Bitmap>, String, String) -> Unit)? = null,
    onOpenCrop: () -> Unit = {}
) {
    val context = LocalContext.current
    val repository = remember { AppSettingsRepository(context) }
    val client = remember { GeminiStickerClient() }
    val scope = rememberCoroutineScope()

    var apiKey by remember { mutableStateOf(repository.getGeminiApiKey()) }
    var options by remember { mutableStateOf(defaultAiOptions()) }
    var isGenerating by remember { mutableStateOf(false) }
    var generatedImages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var sourceImage by remember { mutableStateOf<Bitmap?>(null) }
    var generationStatus by remember { mutableStateOf("") }
    var showPackDialog by remember { mutableStateOf(false) }
    var packName by remember { mutableStateOf("ملصقات الذكاء الاصطناعي") }
    var packEmoji by remember { mutableStateOf("✨") }
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        sourceImage = decodeBackgroundBitmap(context, uri)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                apiKey = repository.getGeminiApiKey()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    fun startGeneration(selectedOptions: AiStickerOptions) {
        if (isGenerating) return
        val key = repository.getGeminiApiKey()
        apiKey = key
        if (key.isBlank()) {
            Toast.makeText(
                context,
                "أضف مفتاح Gemini من الإعدادات أولًا",
                Toast.LENGTH_LONG
            ).show()
            onOpenSettings()
            return
        }
        isGenerating = true
        errorMessage = null
        scope.launch {
            try {
                val images = client.generateImages(
                    apiKey = key,
                    prompt = if (sourceImage != null) {
                        buildImageToStickerPrompt(selectedOptions)
                    } else {
                        buildStickerPrompt(selectedOptions)
                    },
                    count = selectedOptions.count,
                    aspectRatio = aspectRatioFor(selectedOptions.dimension),
                    transparent = sourceImage != null || selectedOptions.transparent,
                    inputImage = sourceImage
                )
                generatedImages = images
                repository.setLastAiStyle(selectedOptions.style.label)
                repository.setLastAiCategory(selectedOptions.category.label)
            } catch (error: Exception) {
                errorMessage = error.message?.takeIf { it.isNotBlank() }
                    ?: "تعذر إنشاء الملصق. تحقق من الاتصال والمفتاح وحاول مجددًا."
            } finally {
                isGenerating = false
            }
        }
    }

    fun generateRandom() {
        val randomOptions = options.copy(
            category = AI_CATEGORIES.random(),
            style = AI_STYLES.random(),
            character = AI_CHARACTERS.random(),
            expression = AI_EXPRESSIONS.random(),
            effects = AI_EFFECTS.shuffled().take(2).map { it.label }.toSet(),
            customCharacter = "",
            description = AI_IDEAS.random()
        )
        options = randomOptions
        startGeneration(randomOptions)
    }

    fun generateNormalImage() {
        val updated = options.copy(transparent = false, output = "صورة")
        options = updated
        startGeneration(updated)
    }

    fun generateTransparentSticker() {
        val updated = options.copy(transparent = true, output = "ملصق")
        options = updated
        startGeneration(updated)
    }

    fun saveGeneratedImages() {
        val images = generatedImages
        if (images.isEmpty()) return
        val categoryName = options.category.label
            .substringAfter(' ')
            .trim()
            .ifBlank { "ملصق" }
        val suggestedName = "ملصق ذكاء اصطناعي $categoryName ${System.currentTimeMillis()}"
        onSaveImages(images, suggestedName)
        Toast.makeText(
            context,
            "تم حفظ ${images.size} صورة في الاستوديو",
            Toast.LENGTH_SHORT
        ).show()
    }

    fun generateAutoPack(count: Int) {
        if (isGenerating) return
        val key = repository.getGeminiApiKey()
        apiKey = key
        if (key.isBlank()) {
            Toast.makeText(context, "أضف مفتاح Gemini من الإعدادات أولاً", Toast.LENGTH_LONG).show()
            onOpenSettings()
            return
        }
        isGenerating = true
        errorMessage = null
        generationStatus = "0 / $count"
        scope.launch {
            val collected = mutableListOf<Bitmap>()
            val variations = listOf(
                "يضحك من قلبه",
                "مندهش وسعيد",
                "غاضب بلمسة مرحة",
                "خجول ومتضايق",
                "متحد وعازم",
                "سعيد بيوم مثالي",
                "وسط الفوضى",
                "وجه استفهام مضحك",
                "انفجار حماس",
                "وقفة حزم",
                "متلهف ومتحمس",
                "نصف نائم",
                "يغني بارتفاع الصوت",
                "هدير خوف لطيف",
                "نظرة بجد حاد",
                "يرقص بهدوء",
                "يفكر عميقا",
                "وجه الحكمة",
                "تساءل بشارة",
                "وجه حنون",
            )
            var completed = 0
            try {
                val baseIdea = options.description.ifBlank { options.category.prompt }
                variations.take(count).forEach { variation ->
                    try {
                        val generated = client.generateImages(
                            apiKey = key,
                            prompt = buildAutoPackPrompt(baseIdea, variation),
                            count = 1,
                            aspectRatio = "1:1",
                            transparent = true,
                            inputImage = sourceImage
                        )
                        if (generated.isNotEmpty() && collected.size < count) {
                            collected += generated.take(1)
                        }
                    } catch (_: Exception) {
                    } finally {
                        completed++
                        generationStatus = "$completed / $count"
                    }
                }
            } finally {
                isGenerating = false
                generationStatus = ""
                if (collected.isEmpty()) {
                    errorMessage = "تعذر إنشاء الحزمة. تحقق من المفتاح والاتصال وحاول مرة أخرى."
                } else {
                    generatedImages = collected
                }
            }
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "مولّد الملصقات بالذكاء الاصطناعي",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Gemini 2.5 Flash Image",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.testTag("ai_back_button")
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "رجوع"
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = onOpenSettings,
                            modifier = Modifier.testTag("ai_settings_button")
                        ) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "الإعدادات"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    AiHeroCard(hasApiKey = apiKey.isNotBlank())
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            SectionTitle(
                                title = "من صورة شخصية",
                                subtitle = "حوّل صورتك إلى ملصق كرتوني بالذكاء الاصطناعي"
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                sourceImage?.let { image ->
                                    Image(
                                        bitmap = image.asImageBitmap(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .size(74.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                    )
                                }
                                OutlinedButton(
                                    onClick = { imagePicker.launch("image/*") },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = if (sourceImage == null) {
                                            "اختر صورة شخصية"
                                        } else {
                                            "تغيير الصورة"
                                        },
                                        fontSize = 12.sp
                                    )
                                }
                                if (sourceImage != null) {
                                    OutlinedButton(
                                        onClick = { sourceImage = null },
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("إزالة", fontSize = 12.sp)
                                    }
                                }
                            }
                            if (sourceImage != null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "سيتم إنشاء شخصية كرتونية من هذه الصورة بخلفية شفافة.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                item {
                    AuroraGradientButton(
                        label = "شاشة قص الصور (حدود أوتوماتيك)",
                        onClick = onOpenCrop,
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Default.PhotoLibrary
                    )
                }

                if (apiKey.isBlank()) {
                    item {
                        MissingApiKeyCard(onOpenSettings = onOpenSettings)
                    }
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            SectionTitle(
                                title = "نوع الملصق",
                                subtitle = "اختر الفئة المناسبة لفكرتك"
                            )
                            AI_CATEGORIES.chunked(2).forEachIndexed { index, row ->
                                if (index > 0) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    row.forEach { choice ->
                                        CategoryCard(
                                            choice = choice,
                                            selected = options.category.label == choice.label,
                                            onClick = {
                                                options = options.copy(category = choice)
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    if (row.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            SectionTitle(
                                title = "وصف الملصق",
                                subtitle = "اكتب وصفًا عربيًا أو إنجليزيًا بالتفصيل"
                            )
                            OutlinedTextField(
                                value = options.description,
                                onValueChange = {
                                    options = options.copy(description = it)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("الوصف") },
                                placeholder = { Text("قطة صغيرة تضحك وترتدي نظارة شمسية") },
                                minLines = 3,
                                maxLines = 5
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        options = options.copy(description = AI_IDEAS.random())
                                        Toast.makeText(
                                            context,
                                            "تم اقتراح فكرة جديدة",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(14.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lightbulb,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("توليد أفكار", fontSize = 12.sp, maxLines = 1)
                                }
                                OutlinedButton(
                                    onClick = { generateRandom() },
                                    enabled = !isGenerating,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(14.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Shuffle,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("إنشاء عشوائي", fontSize = 12.sp, maxLines = 1)
                                }
                            }
                        }
                    }
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            ChoiceChips(
                                title = "الأسلوب",
                                choices = AI_STYLES,
                                selectedLabel = options.style.label,
                                onSelect = { choice ->
                                    options = options.copy(style = choice)
                                }
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            ChoiceChips(
                                title = "الشخصية",
                                choices = AI_CHARACTERS,
                                selectedLabel = options.character.label,
                                onSelect = { choice ->
                                    options = options.copy(
                                        character = choice,
                                        customCharacter = if (choice.label == "مخصص") {
                                            options.customCharacter
                                        } else {
                                            ""
                                        }
                                    )
                                }
                            )
                            if (options.character.label == "مخصص") {
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = options.customCharacter,
                                    onValueChange = {
                                        options = options.copy(customCharacter = it)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("وصف الشخصية المخصصة") },
                                    placeholder = { Text("مثال: فارس صغير يرتدي درعًا ذهبيًا") },
                                    singleLine = true
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            ChoiceChips(
                                title = "التعبير",
                                choices = AI_EXPRESSIONS,
                                selectedLabel = options.expression.label,
                                onSelect = { choice ->
                                    options = options.copy(expression = choice)
                                }
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            EffectChips(
                                selectedLabels = options.effects,
                                onToggle = { effect ->
                                    val current = options.effects
                                    options = options.copy(
                                        effects = if (effect.label in current) {
                                            current - effect.label
                                        } else {
                                            current + effect.label
                                        }
                                    )
                                }
                            )
                        }
                    }
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            SectionTitle(
                                title = "خيارات متقدمة",
                                subtitle = "تحكم في العدد والأبعاد والجودة والمخرجات"
                            )
                            SegmentedOptions(
                                title = "عدد الصور",
                                options = listOf("1", "2", "4"),
                                selected = options.count.toString(),
                                onSelect = { value ->
                                    options = options.copy(
                                        count = value.toIntOrNull() ?: 1
                                    )
                                }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            SegmentedOptions(
                                title = "الأبعاد",
                                options = listOf("مربع", "عمودي", "أفقي"),
                                selected = options.dimension,
                                onSelect = { value ->
                                    options = options.copy(dimension = value)
                                }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            SegmentedOptions(
                                title = "الجودة",
                                options = listOf("512", "1K", "2K"),
                                selected = options.quality,
                                onSelect = { value ->
                                    options = options.copy(quality = value)
                                }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            SegmentedOptions(
                                title = "الخلفية",
                                options = listOf("شفافة", "ملونة"),
                                selected = if (options.transparent) "شفافة" else "ملونة",
                                onSelect = { value ->
                                    options = options.copy(transparent = value == "شفافة")
                                }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            SegmentedOptions(
                                title = "نص داخل الصورة",
                                options = listOf("بدون", "مخصص"),
                                selected = options.textMode,
                                onSelect = { value ->
                                    options = options.copy(textMode = value)
                                }
                            )
                            if (options.textMode == "مخصص") {
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = options.customText,
                                    onValueChange = {
                                        options = options.copy(customText = it)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("النص المطلوب") },
                                    placeholder = { Text("مثال: أحبك") },
                                    singleLine = true
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            SegmentedOptions(
                                title = "الناتج",
                                options = listOf("صورة", "ملصق"),
                                selected = options.output,
                                onSelect = { value ->
                                    options = options.copy(output = value)
                                }
                            )
                        }
                    }
                }

                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AuroraGradientButton(
                            label = "✨ إنشاء الملصق بالذكاء الاصطناعي",
                            onClick = { startGeneration(options) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isGenerating,
                            icon = Icons.Default.AutoAwesome
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { generateNormalImage() },
                                enabled = !isGenerating,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp)
                            ) {
                                Text(
                                    text = "إنشاء صورة عادية",
                                    fontSize = 11.sp,
                                    maxLines = 2
                                )
                            }
                            OutlinedButton(
                                onClick = { generateTransparentSticker() },
                                enabled = !isGenerating,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp)
                            ) {
                                Text(
                                    text = "إنشاء ملصق بخلفية شفافة",
                                    fontSize = 11.sp,
                                    maxLines = 2
                                )
                            }
                        }
                    }
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            SectionTitle(
                                title = "حزمة تلقائية جاهزة",
                                subtitle = "توليد 10 أو 20 ملصقًا لنفس الشخصية بتعبيرات متنوعة"
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { generateAutoPack(10) },
                                    enabled = !isGenerating,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("حزمة 10 ملصقات", fontSize = 11.sp)
                                }
                                OutlinedButton(
                                    onClick = { generateAutoPack(20) },
                                    enabled = !isGenerating,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("حزمة 20 ملصقًا", fontSize = 11.sp)
                                }
                            }
                            if (isGenerating && generationStatus.isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "جارٍ التوليد ($generationStatus)…",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (isGenerating) {
                    item {
                        LoadingCard()
                    }
                }

                if (generatedImages.isNotEmpty()) {
                    item {
                        GeneratedPreviewCard(
                            images = generatedImages,
                            onSave = { saveGeneratedImages() }
                        )
                    }
                }

                if (generatedImages.isNotEmpty() && onCreatePack != null) {
                    item {
                        AuroraGradientButton(
                            label = "إنشاء حزمة جاهزة للتصدير (واتساب/تليجرام)",
                            onClick = { showPackDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            icon = Icons.Default.PhotoLibrary
                        )
                    }
                }

                item {
                    AiTipsCard()
                }
            }
        }
    }

    if (showPackDialog) {
        AuroraDialog(
            onDismissRequest = { showPackDialog = false },
            title = "إنشاء حزمة ملصقات",
            subtitle = "حزمة جديدة من ${generatedImages.size} صورة مولدة، قابلة للتصدير لاحقًا",
            icon = Icons.Default.PhotoLibrary,
            dismissLabel = "إلغاء",
            showDismiss = true
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = packName,
                    onValueChange = { packName = it },
                    label = { Text("اسم الحزمة") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = packEmoji,
                    onValueChange = { value -> packEmoji = value.take(2) },
                    label = { Text("الرمز التعبيري الافتراضي") },
                    singleLine = true
                )
                AuroraGradientButton(
                    label = "إنشاء الحزمة",
                    onClick = {
                        val images = generatedImages
                        showPackDialog = false
                        if (images.isNotEmpty()) {
                            onCreatePack?.invoke(images, packName, packEmoji)
                            Toast.makeText(
                                context,
                                "سيتم إعداد الحزمة ثم فتح تبويب الحزمة",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    val currentError = errorMessage
    if (currentError != null) {
        AuroraDialog(
            onDismissRequest = { errorMessage = null },
            title = "تعذر إنشاء الملصق",
            subtitle = "تحقق من مفتاح Gemini واتصالك بالإنترنت",
            icon = Icons.Default.ErrorOutline,
            dismissLabel = "حسنًا",
            showDismiss = true
        ) {
            Text(
                text = currentError,
                color = Color.White.copy(alpha = 0.88f)
            )
        }
    }
}

@Composable
private fun AiHeroCard(hasApiKey: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF8B5CF6),
                        Color(0xFF22D3EE),
                        Color(0xFFF472B6)
                    )
                )
            )
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "مولّد ملصقات Gemini",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "اختر الفئة والأسلوب واكتب فكرتك، وسنحوّلها إلى ملصق جاهز.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.9f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha = 0.18f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (hasApiKey) "المفتاح جاهز" else "المفتاح غير مضاف",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun MissingApiKeyCard(onOpenSettings: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                shape = RoundedCornerShape(22.dp)
            ),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.VpnKey,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "مفتاح Gemini مطلوب",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "لتوليد الملصقات بالذكاء الاصطناعي، أضف مفتاح Gemini API من شاشة الإعدادات. يُحفظ المفتاح محليًا على جهازك.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            AuroraGradientButton(
                label = "أضف مفتاح Gemini من الإعدادات",
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Default.Settings
            )
        }
    }
}

@Composable
private fun SectionTitle(
    title: String,
    subtitle: String? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.ExtraBold
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CategoryCard(
    choice: AiChoice,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(18.dp)
    Card(
        modifier = modifier
            .height(76.dp)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                shape = shape
            )
            .clickable(onClick = onClick),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (selected) 3.dp else 0.dp
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = choice.label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ChoiceChips(
    title: String,
    choices: List<AiChoice>,
    selectedLabel: String,
    onSelect: (AiChoice) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionTitle(title = title)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            choices.forEach { choice ->
                val selected = choice.label == selectedLabel
                OutlinedButton(
                    onClick = { onSelect(choice) },
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (selected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                        } else {
                            Color.Transparent
                        },
                        contentColor = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = choice.label,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
private fun EffectChips(
    selectedLabels: Set<String>,
    onToggle: (AiChoice) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionTitle(title = "الألوان والتأثيرات")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AI_EFFECTS.forEach { effect ->
                val selected = effect.label in selectedLabels
                OutlinedButton(
                    onClick = { onToggle(effect) },
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (selected) {
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.28f)
                        } else {
                            Color.Transparent
                        },
                        contentColor = if (selected) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = effect.label,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
private fun SegmentedOptions(
    title: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            options.forEach { option ->
                val isSelected = option == selected
                OutlinedButton(
                    onClick = { onSelect(option) },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (isSelected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                        } else {
                            Color.Transparent
                        },
                        contentColor = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = option,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "جارٍ إنشاء الملصقات...",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "قد يستغرق التوليد بعض الثواني",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun buildImageToStickerPrompt(options: AiStickerOptions): String {
    val styleText = options.style.prompt
    return "حوّل الصورة المرفقة إلى شخصية كرتونية تحافظ على ملامح الوجه والتسريحة والملابس، " +
        "وأنشئ ملصقًا شفافًا بقص الشخصية بدقة بدون أي خلفية. " +
        "التعبير: ${options.expression.prompt}. الأسلوب: $styleText. نسبة $options.dimension بجودة عالية."
}

private fun buildAutoPackPrompt(baseIdea: String, expression: String): String {
    return "أنشئ ملصقًا شفافًا لشخصية ثابتة تحافظ على نفس الهوية (نفس الوجه والملابس والألوان) " +
        "والتعبير فقط يتغير بحسب الوصف: $expression. الفكرة: $baseIdea. " +
        "النتيجة بدون خلفية وبحدود واضحة ونسبة 1:1."
}

@Composable
private fun GeneratedPreviewCard(
    images: List<Bitmap>,
    onSave: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            SectionTitle(
                title = "معاينة النتائج",
                subtitle = "${images.size} صورة جاهزة للحفظ"
            )
            images.chunked(2).forEachIndexed { index, row ->
                if (index > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { bitmap ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = 0.06f))
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(14.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "ملصق مولّد",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(4.dp)
                            )
                        }
                    }
                    if (row.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            AuroraGradientButton(
                label = "حفظ في المعرض",
                onClick = onSave,
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Default.PhotoLibrary
            )
        }
    }
}

@Composable
private fun AiTipsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Lightbulb,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "نصائح للتوليد",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "• كلما كان الوصف أدق كانت النتيجة أفضل.\n" +
                    "• استخدم الخلفية الشفافة لملصقات واتساب وتيليجرام.\n" +
                    "• جرّب أسلوبًا واحدًا في كل مرة وقارن النتائج.\n" +
                    "• يمكنك اختيار أكثر من لون أو تأثير في الوقت نفسه.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
