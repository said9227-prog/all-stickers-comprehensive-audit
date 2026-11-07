package com.example.creator

enum class StickerFontCategory(val label: String, val emoji: String) {
    ARABIC("عربي فاخر", "🌙"),
    ARABIC_MODERN("عربي حديث", "✨"),
    ENGLISH("إنجليزي", "🔤"),
    DISPLAY("عرض وعناوين", "💫")
}

/**
 * Fonts are sourced from the official Google Fonts repository on GitHub
 * (github.com/google/fonts). Bundled files are licensed under the SIL OFL.
 * Additional fonts can be downloaded on demand from the same repository.
 */
data class StickerFont(
    val id: String,
    val name: String,
    val arabicName: String,
    val category: StickerFontCategory,
    val supportsArabic: Boolean,
    val assetPath: String? = null,
    val downloadUrl: String? = null,
    val previewText: String = "أبجد هوّز • Sticker"
) {
    val isBundled: Boolean get() = assetPath != null
    val isRemote: Boolean get() = downloadUrl != null
}

val STICKER_FONTS = listOf(
    StickerFont(
        id = "noto_naskh",
        name = "Noto Naskh Arabic",
        arabicName = "نوتو نسخ",
        category = StickerFontCategory.ARABIC,
        supportsArabic = true,
        assetPath = "fonts/NotoNaskhArabic.ttf",
        previewText = "بسم الله الرحمن الرحيم"
    ),
    StickerFont(
        id = "noto_kufi",
        name = "Noto Kufi Arabic",
        arabicName = "نوتو كوفي",
        category = StickerFontCategory.ARABIC_MODERN,
        supportsArabic = true,
        assetPath = "fonts/NotoKufiArabic.ttf",
        previewText = "ملصقات عربية أنيقة"
    ),
    StickerFont(
        id = "amiri",
        name = "Amiri",
        arabicName = "أميري",
        category = StickerFontCategory.ARABIC,
        supportsArabic = true,
        assetPath = "fonts/Amiri-Regular.ttf",
        previewText = "الخط الأميري الكلاسيكي"
    ),
    StickerFont(
        id = "cairo",
        name = "Cairo",
        arabicName = "القاهرة",
        category = StickerFontCategory.ARABIC_MODERN,
        supportsArabic = true,
        assetPath = "fonts/Cairo.ttf",
        previewText = "القاهرة خط عصري"
    ),
    StickerFont(
        id = "tajawal",
        name = "Tajawal",
        arabicName = "تجوال",
        category = StickerFontCategory.ARABIC_MODERN,
        supportsArabic = true,
        assetPath = "fonts/Tajawal-Regular.ttf",
        previewText = "تجوال للعناوين"
    ),
    StickerFont(
        id = "almarai",
        name = "Almarai",
        arabicName = "المراعي",
        category = StickerFontCategory.ARABIC_MODERN,
        supportsArabic = true,
        assetPath = "fonts/Almarai-Regular.ttf",
        previewText = "المراعي خط واضح"
    ),
    StickerFont(
        id = "reem_kufi",
        name = "Reem Kufi",
        arabicName = "ريم كوفي",
        category = StickerFontCategory.ARABIC_MODERN,
        supportsArabic = true,
        assetPath = "fonts/ReemKufi.ttf",
        previewText = "ريم كوفي هندسي"
    ),
    StickerFont(
        id = "el_messiri",
        name = "El Messiri",
        arabicName = "المسيري",
        category = StickerFontCategory.ARABIC_MODERN,
        supportsArabic = true,
        assetPath = "fonts/ElMessiri.ttf",
        previewText = "المسيري الأنيق"
    ),
    StickerFont(
        id = "lalezar",
        name = "Lalezar",
        arabicName = "لاله زار",
        category = StickerFontCategory.DISPLAY,
        supportsArabic = true,
        assetPath = "fonts/Lalezar-Regular.ttf",
        previewText = "لاله زار للعروض"
    ),
    StickerFont(
        id = "rakkas",
        name = "Rakkas",
        arabicName = "رقّاص",
        category = StickerFontCategory.DISPLAY,
        supportsArabic = true,
        assetPath = "fonts/Rakkas-Regular.ttf",
        previewText = "رقّاص عنوان قوي"
    ),
    StickerFont(
        id = "poppins",
        name = "Poppins",
        arabicName = "بوبينز",
        category = StickerFontCategory.ENGLISH,
        supportsArabic = false,
        assetPath = "fonts/Poppins-Regular.ttf",
        previewText = "Poppins modern"
    ),
    StickerFont(
        id = "poppins_bold",
        name = "Poppins Bold",
        arabicName = "بوبينز عريض",
        category = StickerFontCategory.ENGLISH,
        supportsArabic = false,
        assetPath = "fonts/Poppins-Bold.ttf",
        previewText = "Poppins Bold"
    ),
    StickerFont(
        id = "montserrat",
        name = "Montserrat",
        arabicName = "مونتسيرات",
        category = StickerFontCategory.ENGLISH,
        supportsArabic = false,
        assetPath = "fonts/Montserrat.ttf",
        previewText = "Montserrat display"
    ),
    StickerFont(
        id = "bebas",
        name = "Bebas Neue",
        arabicName = "بيباس",
        category = StickerFontCategory.DISPLAY,
        supportsArabic = false,
        assetPath = "fonts/BebasNeue-Regular.ttf",
        previewText = "BEBAS NEUE"
    ),
    StickerFont(
        id = "pacifico",
        name = "Pacifico",
        arabicName = "باسيفيكو",
        category = StickerFontCategory.ENGLISH,
        supportsArabic = false,
        assetPath = "fonts/Pacifico-Regular.ttf",
        previewText = "Pacifico vibes"
    ),
    StickerFont(
        id = "lobster",
        name = "Lobster",
        arabicName = "لوبستر",
        category = StickerFontCategory.ENGLISH,
        supportsArabic = false,
        assetPath = "fonts/Lobster-Regular.ttf",
        previewText = "Lobster style"
    ),
    StickerFont(
        id = "orbitron",
        name = "Orbitron",
        arabicName = "أوربيترون",
        category = StickerFontCategory.DISPLAY,
        supportsArabic = false,
        assetPath = "fonts/Orbitron.ttf",
        previewText = "ORBITRON 2077"
    ),
    StickerFont(
        id = "playfair",
        name = "Playfair Display",
        arabicName = "بلايفير",
        category = StickerFontCategory.ENGLISH,
        supportsArabic = false,
        assetPath = "fonts/PlayfairDisplay.ttf",
        previewText = "Playfair Display"
    ),
    StickerFont(
        id = "great_vibes",
        name = "Great Vibes",
        arabicName = "غريت فايبس",
        category = StickerFontCategory.DISPLAY,
        supportsArabic = false,
        assetPath = "fonts/GreatVibes-Regular.ttf",
        previewText = "Great Vibes"
    ),
    StickerFont(
        id = "cinzel",
        name = "Cinzel",
        arabicName = "سينزل",
        category = StickerFontCategory.DISPLAY,
        supportsArabic = false,
        assetPath = "fonts/Cinzel.ttf",
        previewText = "CINZEL LUXE"
    ),
    StickerFont(
        id = "noto_sans_arabic",
        name = "Noto Sans Arabic",
        arabicName = "نوتو سانس عربي",
        category = StickerFontCategory.ARABIC_MODERN,
        supportsArabic = true,
        assetPath = "fonts/NotoSansArabic.ttf",
        previewText = "نوتو سانس العربي"
    ),
    StickerFont(
        id = "changa",
        name = "Changa",
        arabicName = "تشانغا",
        category = StickerFontCategory.ARABIC_MODERN,
        supportsArabic = true,
        assetPath = "fonts/Changa.ttf",
        previewText = "تشانغا للعناوين"
    ),
    StickerFont(
        id = "harmattan",
        name = "Harmattan",
        arabicName = "هرمتان",
        category = StickerFontCategory.ARABIC,
        supportsArabic = true,
        assetPath = "fonts/Harmattan-Regular.ttf",
        previewText = "هرمتان خط هادئ"
    ),
    StickerFont(
        id = "scheherazade",
        name = "Scheherazade New",
        arabicName = "شهرزاد",
        category = StickerFontCategory.ARABIC,
        supportsArabic = true,
        assetPath = "fonts/ScheherazadeNew-Regular.ttf",
        previewText = "شهرزاد وحكاياتها"
    ),
    StickerFont(
        id = "aref_ruqaa",
        name = "Aref Ruqaa",
        arabicName = "عارف رقعة",
        category = StickerFontCategory.ARABIC,
        supportsArabic = true,
        assetPath = "fonts/ArefRuqaa-Regular.ttf",
        previewText = "عارف رقعة فاخر"
    ),
    StickerFont(
        id = "mada",
        name = "Mada",
        arabicName = "مدى",
        category = StickerFontCategory.ARABIC_MODERN,
        supportsArabic = true,
        assetPath = "fonts/Mada.ttf",
        previewText = "مدى بلا حدود"
    ),
    StickerFont(
        id = "ibm_plex_sans_arabic",
        name = "IBM Plex Sans Arabic",
        arabicName = "آي بي إم بلكس عربي",
        category = StickerFontCategory.ARABIC_MODERN,
        supportsArabic = true,
        assetPath = "fonts/IBMPlexSansArabic-Regular.ttf",
        previewText = "بلكس عربي حديث"
    ),
    StickerFont(
        id = "vazirmatn",
        name = "Vazirmatn",
        arabicName = "وزير",
        category = StickerFontCategory.ARABIC_MODERN,
        supportsArabic = true,
        assetPath = "fonts/Vazirmatn.ttf",
        previewText = "وزير متعدد الأوزان"
    ),
    StickerFont(
        id = "baloo_bhaijaan_2",
        name = "Baloo Bhaijaan 2",
        arabicName = "بالو بحرين",
        category = StickerFontCategory.ARABIC_MODERN,
        supportsArabic = true,
        assetPath = "fonts/BalooBhaijaan2.ttf",
        previewText = "بالو دائري مرن"
    ),
    StickerFont(
        id = "marhey",
        name = "Marhey",
        arabicName = "مرح",
        category = StickerFontCategory.ARABIC,
        supportsArabic = true,
        assetPath = "fonts/Marhey.ttf",
        previewText = "مرح بخط لافت"
    ),
    StickerFont(
        id = "lemonada",
        name = "Lemonada",
        arabicName = "ليمونادا",
        category = StickerFontCategory.ARABIC_MODERN,
        supportsArabic = true,
        assetPath = "fonts/Lemonada.ttf",
        previewText = "ليمونادا عربي"
    ),
    StickerFont(
        id = "alkalami",
        name = "Alkalami",
        arabicName = "الكلمي",
        category = StickerFontCategory.ARABIC,
        supportsArabic = true,
        assetPath = "fonts/Alkalami-Regular.ttf",
        previewText = "الكلمي كوفي حاد"
    ),
    StickerFont(
        id = "katibeh",
        name = "Katibeh",
        arabicName = "الكتائب",
        category = StickerFontCategory.ARABIC,
        supportsArabic = true,
        assetPath = "fonts/Katibeh-Regular.ttf",
        previewText = "اكتب بخط العنوان"
    ),
    StickerFont(
        id = "mirza",
        name = "Mirza",
        arabicName = "مرزا",
        category = StickerFontCategory.ARABIC,
        supportsArabic = true,
        assetPath = "fonts/Mirza-Regular.ttf",
        previewText = "مرزا أنيق نستعليق"
    ),
    StickerFont(
        id = "lateef",
        name = "Lateef",
        arabicName = "لطيف",
        category = StickerFontCategory.ARABIC,
        supportsArabic = true,
        assetPath = "fonts/Lateef-Regular.ttf",
        previewText = "لطيف للنصوص الصغيرة"
    ),
    StickerFont(
        id = "jomhuria",
        name = "Jomhuria",
        arabicName = "جمهورية",
        category = StickerFontCategory.ARABIC,
        supportsArabic = true,
        assetPath = "fonts/Jomhuria-Regular.ttf",
        previewText = "جمهورية ضخم"
    ),
    StickerFont(
        id = "qahiri",
        name = "Qahiri",
        arabicName = "قاهري",
        category = StickerFontCategory.ARABIC,
        supportsArabic = true,
        assetPath = "fonts/Qahiri-Regular.ttf",
        previewText = "قاهري كوفي"
    ),
    StickerFont(
        id = "ruwudu",
        name = "Ruwudu",
        arabicName = "روضو",
        category = StickerFontCategory.ARABIC,
        supportsArabic = true,
        assetPath = "fonts/Ruwudu-Regular.ttf",
        previewText = "روضو للنصوص"
    ),
    StickerFont(
        id = "gulzar",
        name = "Gulzar",
        arabicName = "کلزار",
        category = StickerFontCategory.ARABIC,
        supportsArabic = true,
        assetPath = "fonts/Gulzar-Regular.ttf",
        previewText = "کلزار نسخ أنيق"
    ),
    StickerFont(
        id = "righteous",
        name = "Righteous",
        arabicName = "رايتشس",
        category = StickerFontCategory.DISPLAY,
        supportsArabic = false,
        assetPath = "fonts/Righteous-Regular.ttf",
        previewText = "RIGHTEOUS"
    ),
    StickerFont(
        id = "bangers",
        name = "Bangers",
        arabicName = "بانجرز",
        category = StickerFontCategory.DISPLAY,
        supportsArabic = false,
        assetPath = "fonts/Bangers-Regular.ttf",
        previewText = "BANGERS!"
    ),
    StickerFont(
        id = "anton",
        name = "Anton",
        arabicName = "أنطون",
        category = StickerFontCategory.DISPLAY,
        supportsArabic = false,
        assetPath = "fonts/Anton-Regular.ttf",
        previewText = "ANTON"
    ),
    StickerFont(
        id = "caveat",
        name = "Caveat",
        arabicName = "كافيات",
        category = StickerFontCategory.ENGLISH,
        supportsArabic = false,
        assetPath = "fonts/Caveat.ttf",
        previewText = "Caveat handwritten"
    ),
    StickerFont(
        id = "dancing_script",
        name = "Dancing Script",
        arabicName = "دانسينغ سكريبت",
        category = StickerFontCategory.ENGLISH,
        supportsArabic = false,
        assetPath = "fonts/DancingScript.ttf",
        previewText = "Dancing Script"
    ),
    StickerFont(
        id = "alfa_slab",
        name = "Alfa Slab One",
        arabicName = "ألفا سلاب",
        category = StickerFontCategory.DISPLAY,
        supportsArabic = false,
        assetPath = "fonts/AlfaSlabOne-Regular.ttf",
        previewText = "ALFA SLAB"
    ),
    StickerFont(
        id = "press_start",
        name = "Press Start 2P",
        arabicName = "بريس ستارت",
        category = StickerFontCategory.DISPLAY,
        supportsArabic = false,
        assetPath = "fonts/PressStart2P-Regular.ttf",
        previewText = "PRESS START"
    ),
    StickerFont(
        id = "bungee",
        name = "Bungee",
        arabicName = "بانجي",
        category = StickerFontCategory.DISPLAY,
        supportsArabic = false,
        assetPath = "fonts/Bungee-Regular.ttf",
        previewText = "BUNGEE"
    ),
    StickerFont(
        id = "monoton",
        name = "Monoton",
        arabicName = "مونوتون",
        category = StickerFontCategory.DISPLAY,
        supportsArabic = false,
        assetPath = "fonts/Monoton-Regular.ttf",
        previewText = "MONOTON"
    ),
    StickerFont(
        id = "fredoka",
        name = "Fredoka",
        arabicName = "فريدوكا",
        category = StickerFontCategory.ENGLISH,
        supportsArabic = false,
        assetPath = "fonts/Fredoka.ttf",
        previewText = "Fredoka round"
    ),
    StickerFont(
        id = "rubik",
        name = "Rubik",
        arabicName = "روبيك",
        category = StickerFontCategory.ENGLISH,
        supportsArabic = false,
        assetPath = "fonts/Rubik.ttf",
        previewText = "Rubik clean"
    ),
    StickerFont(
        id = "permanent_marker",
        name = "Permanent Marker",
        arabicName = "ماركر دائم",
        category = StickerFontCategory.ENGLISH,
        supportsArabic = false,
        assetPath = "fonts/PermanentMarker-Regular.ttf",
        previewText = "Marker style"
    ),
    StickerFont(
        id = "shadows_into_light",
        name = "Shadows Into Light",
        arabicName = "ظلال خفيفة",
        category = StickerFontCategory.ENGLISH,
        supportsArabic = false,
        assetPath = "fonts/ShadowsIntoLight.ttf",
        previewText = "Light handwriting"
    )
)

fun findStickerFont(id: String?): StickerFont? =
    STICKER_FONTS.firstOrNull { it.id == id }

enum class StickerFontLanguage(val label: String) {
    ARABIC("خطوط عربية"),
    ENGLISH("English fonts")
}

fun StickerFont.language(): StickerFontLanguage =
    if (supportsArabic) StickerFontLanguage.ARABIC else StickerFontLanguage.ENGLISH

fun stickerFontsFor(language: StickerFontLanguage): List<StickerFont> =
    STICKER_FONTS.filter { it.language() == language }
