package com.lynk.dvrprobe

/** Fixed commands for granting the one development permission used by the MCU reader. */
internal object DumpPermissionCommand {
    const val PERMISSION = "android.permission.DUMP"

    // AOSP UserHandle.PER_USER_RANGE: getIdentifier() is not part of the public SDK.
    fun userIdFromUid(uid: Int): Int {
        require(uid >= 0) { "uid must be non-negative" }
        return uid / 100_000
    }

    private val androidPackageName = Regex("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)+")

    fun shell(packageName: String, userId: Int): String {
        validate(packageName, userId)
        return "/system/bin/pm grant --user $userId $packageName $PERMISSION"
    }

    fun adb(packageName: String, userId: Int): String =
        "adb shell ${shell(packageName, userId)}"

    private fun validate(packageName: String, userId: Int) {
        require(androidPackageName.matches(packageName)) { "Invalid Android package name" }
        require(userId >= 0) { "userId must be non-negative" }
    }
}
