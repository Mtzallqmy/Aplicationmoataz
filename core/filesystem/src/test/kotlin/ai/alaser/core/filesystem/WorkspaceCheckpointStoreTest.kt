package ai.alaser.core.filesystem

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class WorkspaceCheckpointStoreTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun restoresModifiedFilesAndRemovesNewFiles() = runBlocking {
        val workspace = temporary.newFolder("workspace")
        File(workspace, "app.py").writeText("original")
        File(workspace, ".env").writeText("PRIVATE=1")
        val store = WorkspaceCheckpointStore(workspace, temporary.newFolder("checkpoints"))
        val checkpoint = store.create("Before agent changes")

        File(workspace, "app.py").writeText("modified")
        File(workspace, "new.py").writeText("new")
        store.restore(checkpoint.id)

        assertEquals("original", File(workspace, "app.py").readText())
        assertFalse(File(workspace, "new.py").exists())
        assertTrue(File(workspace, ".env").exists())
        assertEquals(1, checkpoint.fileCount)
        assertEquals(checkpoint.id, store.list().single().id)
    }
}
