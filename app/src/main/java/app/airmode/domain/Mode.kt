package app.airmode.domain

enum class Mode(val code: Int) { OFF(1), ANC(2), TRANSPARENCY(3), ADAPTIVE(4);
    companion object { fun fromCode(code: Int): Mode? = entries.firstOrNull { it.code == code } }
}
