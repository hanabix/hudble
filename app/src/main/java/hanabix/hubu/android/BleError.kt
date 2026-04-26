package hanabix.hubu.android

internal class BleError(
    message: String,
) : RuntimeException(message) {
    override fun fillInStackTrace(): Throwable = this
}
