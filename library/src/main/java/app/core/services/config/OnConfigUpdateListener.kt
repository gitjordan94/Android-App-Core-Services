package app.core.services.config

fun interface OnConfigUpdateListener {
    fun onUpdate(updatedKeys: Set<String>)
}