package com.example.naffith

/** التطبيع للمقارنة فقط؛ يبقى نص البحث الأصلي كما كتبه المستخدم. */
object ArabicText {
    fun normalize(value: String): String = value.lowercase(java.util.Locale.ROOT)
        .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
        .replace('ى', 'ي').replace('ة', 'ه')
        .replace(Regex("[ًٌٍَُِّْـ]"), "")
        .map { char ->
            when (char) {
                in '٠'..'٩' -> '0' + (char - '٠')
                in '۰'..'۹' -> '0' + (char - '۰')
                '×', '✕' -> '*'
                '÷' -> '/'
                '−', '–' -> '-'
                '٫' -> '.'
                else -> char
            }
        }.joinToString("")
        .replace(Regex("\\s+"), " ").trim()
}
