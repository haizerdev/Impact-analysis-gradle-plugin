package com.nzr.impactanalysis.git

import com.nzr.impactanalysis.model.ChangeType
import com.nzr.impactanalysis.git.GitClient
import org.eclipse.jgit.api.Git
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit тесты для GitClient
 */
class GitClientTest {

    private lateinit var tempDir: File
    private lateinit var git: Git
    private lateinit var gitClient: GitClient

    @Before
    fun setup() {
        // Создаем временный Git репозиторий
        tempDir = createTempDir("git-test")
        git = Git.init().setDirectory(tempDir).call()

        // Создаем начальный коммит
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
        // Добавляем новый файл
        File(tempDir, "NewFile.kt").writeText("class NewFile")
        git.add().addFilepattern(".").call()
        git.commit().setMessage("Add new file").call()

        // Получаем изменения
        val changes = gitClient.getChangedFiles("HEAD~1", "HEAD")

        // Проверяем
        assertEquals(1, changes.size)
        assertEquals("NewFile.kt", changes[0].newPath)
        assertEquals(ChangeType.ADDED, changes[0].changeType)
    }

    @Test
    fun `test getChangedFiles with modified file`() {
        // Изменяем существующий файл
        File(tempDir, "README.md").writeText("# Updated Project")
        git.add().addFilepattern(".").call()
        git.commit().setMessage("Update README").call()

        // Получаем изменения
        val changes = gitClient.getChangedFiles("HEAD~1", "HEAD")

        // Проверяем
        assertEquals(1, changes.size)
        assertEquals("README.md", changes[0].newPath)
        assertEquals(ChangeType.MODIFIED, changes[0].changeType)
    }

    @Test
    fun `test getChangedFiles with deleted file`() {
        // Удаляем файл
        File(tempDir, "README.md").delete()
        git.rm().addFilepattern("README.md").call()
        git.commit().setMessage("Delete README").call()

        // Получаем изменения
        val changes = gitClient.getChangedFiles("HEAD~1", "HEAD")

        // Проверяем
        assertEquals(1, changes.size)
        assertEquals(ChangeType.DELETED, changes[0].changeType)
    }

    @Test
    fun `test getChangedFiles with multiple files`() {
        // Добавляем несколько файлов
        File(tempDir, "File1.kt").writeText("class File1")
        File(tempDir, "File2.kt").writeText("class File2")
        File(tempDir, "File3.kt").writeText("class File3")
        git.add().addFilepattern(".").call()
        git.commit().setMessage("Add multiple files").call()

        // Получаем изменения
        val changes = gitClient.getChangedFiles("HEAD~1", "HEAD")

        // Проверяем
        assertEquals(3, changes.size)
        assertTrue(changes.all { it.changeType == ChangeType.ADDED })
    }

    @Test
    fun `test getUncommittedChanges`() {
        // Создаем файл без коммита
        File(tempDir, "Uncommitted.kt").writeText("class Uncommitted")
        git.add().addFilepattern(".").call()

        // Получаем uncommitted изменения
        val changes = gitClient.getUncommittedChanges()

        // Проверяем
        assertTrue(changes.isNotEmpty())
        assertTrue(changes.any { it.newPath == "Uncommitted.kt" })
    }

    @Test
    fun `test getCurrentBranch`() {
        val branch = gitClient.getCurrentBranch()

        // По умолчанию должна быть master или main
        assertNotNull(branch)
        assertTrue(branch == "master" || branch == "main")
    }

    @Test
    fun `test getChangedFilesSinceBranch`() {
        // Создаем новую ветку
        git.checkout().setCreateBranch(true).setName("feature").call()

        // Добавляем файл в feature ветке
        File(tempDir, "Feature.kt").writeText("class Feature")
        git.add().addFilepattern(".").call()
        git.commit().setMessage("Add feature").call()

        // Получаем изменения от master
        val changes = gitClient.getChangedFilesSinceBranch("master")

        // Проверяем
        assertEquals(1, changes.size)
        assertEquals("Feature.kt", changes[0].newPath)
    }
}
