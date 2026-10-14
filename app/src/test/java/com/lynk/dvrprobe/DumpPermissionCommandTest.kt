package com.lynk.dvrprobe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DumpPermissionCommandTest {
    @Test
    fun buildsFixedGrantForDebugPackageAndUser() {
        val command = "/system/bin/pm grant --user 10 com.lynk.autoenergyfuel.dhureadonly android.permission.DUMP"

        assertEquals(command, DumpPermissionCommand.shell("com.lynk.autoenergyfuel.dhureadonly", 10))
        assertEquals("adb shell $command", DumpPermissionCommand.adb("com.lynk.autoenergyfuel.dhureadonly", 10))
        assertEquals("android.permission.DUMP", DumpPermissionCommand.PERMISSION)
    }

    @Test
    fun rejectsInvalidOrShellInjectedPackageNames() {
        listOf(
            "example",
            "com.1example.app",
            "com.example.app;id",
            "com.example.app \$(id)",
            "com.example.app\nwhoami",
        ).forEach { packageName ->
            assertThrows(IllegalArgumentException::class.java) {
                DumpPermissionCommand.shell(packageName, 0)
            }
        }
    }

    @Test
    fun rejectsNegativeUserId() {
        assertThrows(IllegalArgumentException::class.java) {
            DumpPermissionCommand.shell("com.example.app", -1)
        }
    }

    @Test
    fun derivesAndroidUserFromProcessUid() {
        assertEquals(0, DumpPermissionCommand.userIdFromUid(10_234))
        assertEquals(10, DumpPermissionCommand.userIdFromUid(1_010_234))
        assertThrows(IllegalArgumentException::class.java) {
            DumpPermissionCommand.userIdFromUid(-1)
        }
    }
}
