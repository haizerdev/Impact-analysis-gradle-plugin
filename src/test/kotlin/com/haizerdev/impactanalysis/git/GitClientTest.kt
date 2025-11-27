package com.haizerdev.impactanalysis.git

import com.haizerdev.impactanalysis.model.ChangeType
import com.haizerdev.impactanalysis.git.GitClient
import org.eclipse.jgit.api.Git
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for GitClient
 */
class GitClientTest {

    private lateinit var tempDir: File
    private lateinit var git: Git
    private lateinit var gitClient: GitClient

    @Before
    fun setup() {
        // Create temporary Git repository
        tempDir = createTempDir("git-test")
        git = Git.init().setDirectory(tempDir).call()

        // Create initial commit
        File(tempDir, "README.md").writeText("# Test Project")
        git.add().addFilepattern(".").call()
        git.commit().setMessage("Initial commit").call()

        gitClient = GitClient(tempDir)
    }

    @After
    fun cleanup() {
        gitClient.close()
        git.close()
        tempDir.deleteRecursively()
    }

    @Test
    fun `test getChangedFiles with added file`() {
        // Add new file
        File(tempDir, "NewFile.kt").writeText("class NewFile")
        git.add().addFilepattern(".").call()
        git.commit().setMessage("Add new file").call()

        // Get changes
        val changes = gitClient.getChangedFiles("HEAD~1", "HEAD")

        // Verify
        assertEquals(1, changes.size)
        assertEquals("NewFile.kt", changes[0].newPath)
        assertEquals(ChangeType.ADDED, changes[0].changeType)
    }

    @Test
    fun `test getChangedFiles with modified file`() {
        // Modify existing file
        File(tempDir, "README.md").writeText("# Updated Project")
        git.add().addFilepattern(".").call()
        git.commit().setMessage("Update README").call()

        // Get changes
        val changes = gitClient.getChangedFiles("HEAD~1", "HEAD")

        // Verify
        assertEquals(1, changes.size)
        assertEquals("README.md", changes[0].newPath)
        assertEquals(ChangeType.MODIFIED, changes[0].changeType)
    }

    @Test
    fun `test getChangedFiles with deleted file`() {
        // Delete file
        File(tempDir, "README.md").delete()
        git.rm().addFilepattern("README.md").call()
        git.commit().setMessage("Delete README").call()

        // Get changes
        val changes = gitClient.getChangedFiles("HEAD~1", "HEAD")

        // Verify
        assertEquals(1, changes.size)
        assertEquals(ChangeType.DELETED, changes[0].changeType)
    }

    @Test
    fun `test getChangedFiles with multiple files`() {
        // Add multiple files
        File(tempDir, "File1.kt").writeText("class File1")
        File(tempDir, "File2.kt").writeText("class File2")
        File(tempDir, "File3.kt").writeText("class File3")
        git.add().addFilepattern(".").call()
        git.commit().setMessage("Add multiple files").call()

        // Get changes
        val changes = gitClient.getChangedFiles("HEAD~1", "HEAD")

        // Verify
        assertEquals(3, changes.size)
        assertTrue(changes.all { it.changeType == ChangeType.ADDED })
    }

    @Test
    fun `test getUncommittedChanges`() {
        // Create file without commit
        File(tempDir, "Uncommitted.kt").writeText("class Uncommitted")
        git.add().addFilepattern(".").call()

        // Get uncommitted changes
        val changes = gitClient.getUncommittedChanges()

        // Verify
        assertTrue(changes.isNotEmpty())
        assertTrue(changes.any { it.newPath == "Uncommitted.kt" })
    }

    @Test
    fun `test getCurrentBranch`() {
        val branch = gitClient.getCurrentBranch()

        // Should be master or main by default
        assertNotNull(branch)
        assertTrue(branch == "master" || branch == "main")
    }

    @Test
    fun `test getChangedFilesSinceBranch`() {
        // Create new branch
        git.checkout().setCreateBranch(true).setName("feature").call()

        // Add file in feature branch
        File(tempDir, "Feature.kt").writeText("class Feature")
        git.add().addFilepattern(".").call()
        git.commit().setMessage("Add feature").call()

        // Get changes from master
        val changes = gitClient.getChangedFilesSinceBranch("master")

        // Verify
        assertEquals(1, changes.size)
        assertEquals("Feature.kt", changes[0].newPath)
    }
}
