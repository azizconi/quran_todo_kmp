package tj.app.quran_todo

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform