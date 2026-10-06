package app.airmode.domain

enum class Mode(val code: Int) { OFF(1), ANC(2), TRANSPARENCY(3), ADAPTIVE(4);
    companion object { fun fromCode(code: Int): Mode? = entries.firstOrNull { it.code == code } }
}

internal fun tileCycle(supported: Set<Mode>, selected: Set<Mode>): List<Mode> {
    val preferred = Mode.entries.filter { it in supported && it in selected }
    return preferred.takeIf { it.size >= 2 } ?: Mode.entries.filter { it in supported }
}
