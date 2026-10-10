// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent

import dev.pocketagent.net.GatewayEntry
import dev.pocketagent.transport.RemoteFile
import dev.pocketagent.ui.FileSort
import dev.pocketagent.ui.arrangeFiles
import dev.pocketagent.ui.isApproval
import dev.pocketagent.ui.isImageName
import dev.pocketagent.ui.tmuxOfEvent
import dev.pocketagent.ui.workspaceFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Ekran mantığının saf parçaları: dosya listesi düzeni, workspace yolları,
// görsel türü ve agent olayı → tmux oturumu eşlemesi.
class UiLogicTest {
    private fun f(name: String, dir: Boolean = false, size: Long = 0, mtime: Long = 0) =
        RemoteFile(name, "/h/$name", dir, size, mtime)

    private val listing = listOf(
        f(".config", dir = true, mtime = 50),
        f("src", dir = true, mtime = 10),
        f(".env", size = 10, mtime = 90),
        f("b.txt", size = 300, mtime = 20),
        f("A.md", size = 100, mtime = 80),
    )

    @Test fun hiddenEntriesAreFilteredUnlessShownOrSearched() {
        assertEquals(listOf("src", "A.md", "b.txt"), arrangeFiles(listing, FileSort.Name, showHidden = false, filter = "").map { it.name })
        assertEquals(
            listOf(".config", "src", ".env", "A.md", "b.txt"),
            arrangeFiles(listing, FileSort.Name, showHidden = true, filter = "").map { it.name },
        )
        // "." ile başlayan arama gizlileri de bulur.
        assertEquals(listOf(".env"), arrangeFiles(listing, FileSort.Name, showHidden = false, filter = ".en").map { it.name })
    }

    @Test fun directoriesStayFirstInEverySort() {
        assertEquals(
            listOf("src", "A.md", "b.txt"),
            arrangeFiles(listing, FileSort.Date, showHidden = false, filter = "").map { it.name },
        )
        assertEquals(
            listOf("src", "b.txt", "A.md"),
            arrangeFiles(listing, FileSort.Size, showHidden = false, filter = "").map { it.name },
        )
    }

    // Regresyon: ikinci seviyede girdiler bir önceki dizinin yoluyla
    // kuruluyordu (src/lib yerine lib).
    @Test fun workspacePathsUseTheListedDirectory() {
        val entries = listOf(GatewayEntry("lib", true, 0, 0), GatewayEntry("a.kt", false, 3, 0))
        assertEquals(listOf("src/lib", "src/a.kt"), workspaceFiles("src", entries).map { it.path })
        assertEquals(listOf("lib", "a.kt"), workspaceFiles("", entries).map { it.path })
    }

    @Test fun imageNamesByExtension() {
        assertTrue(isImageName("shot.PNG"))
        assertTrue(isImageName("photo.heic"))
        assertFalse(isImageName("logo.svg")) // XML: metin önizlemesine düşer
        assertFalse(isImageName("README"))
    }

    @Test fun eventSessionMapsToTmuxName() {
        assertEquals("pa-a1b2c3d4", tmuxOfEvent("tmux:pa-a1b2c3d4"))
        assertNull(tmuxOfEvent("tmux:"))
        assertNull(tmuxOfEvent("proc:4242"))
        assertNull(tmuxOfEvent("s:9f2c"))
        assertTrue(isApproval("APPROVAL_REQUIRED"))
        assertFalse(isApproval("TASK_COMPLETE"))
    }
}
