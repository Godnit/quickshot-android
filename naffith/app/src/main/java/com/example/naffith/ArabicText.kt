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
        // أوامر الصوت والكتابة قد تصل بعلامة استفهام عربية، فاصلة، اقتباس،
        // أو محارف اتجاه مخفية. حذفها هنا يجعل المطابقة مستقلة عن طريقة الإدخال.
        .replace(Regex("[\\p{Punct}؟،؛…«»ـ\\u200E\\u200F\\u202A-\\u202E]"), " ")
        .replace(Regex("\\s+"), " ").trim()
}
