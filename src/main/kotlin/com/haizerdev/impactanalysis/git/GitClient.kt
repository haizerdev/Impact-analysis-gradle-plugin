package com.haizerdev.impactanalysis.git

import com.haizerdev.impactanalysis.model.ChangeType
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import java.io.File

/**
 * Client for working with Git repository
 */
class GitClient(private val projectDir: File) {

    private val repository: Repository = FileRepositoryBuilder()
        .setGitDir(File(projectDir, ".git"))
        .readEnvironment()
        .findGitDir()
        .build()

    private val git: Git = Git(repository)

    /**
     * Get list of changed files between two commits
     */
    fun getChangedFiles(
        baseRef: String = "HEAD~1",
        compareRef: String = "HEAD"
    ): List<GitDiffEntry> {
        val baseCommit = resolveCommit(baseRef)
        val compareCommit = resolveCommit(compareRef)

        val baseTree = prepareTreeParser(baseCommit)
        val compareTree = prepareTreeParser(compareCommit)

        val diffs = git.diff()
            .setOldTree(baseTree)
            .setNewTree(compareTree)
            .call()

        return diffs.map { it.toGitDiffEntry() }
    }

    /**
     * Get list of changed files in working directory (uncommitted changes)
     */
    fun getUncommittedChanges(): List<GitDiffEntry> {
        val status = git.status().call()

        val changes = mutableListOf<GitDiffEntry>()

        // Added files
        status.added.forEach { path ->
            changes.add(GitDiffEntry(path, path, ChangeType.ADDED))
        }

        // Modified files
        status.modified.forEach { path ->
            changes.add(GitDiffEntry(path, path, ChangeType.MODIFIED))
        }
        status.changed.forEach { path ->
            changes.add(GitDiffEntry(path, path, ChangeType.MODIFIED))
        }

        // Deleted files
        status.removed.forEach { path ->
            changes.add(GitDiffEntry(path, path, ChangeType.DELETED))
        }
        status.missing.forEach { path ->
            changes.add(GitDiffEntry(path, path, ChangeType.DELETED))
        }

        return changes
    }

    /**
     * Get changes between branch and current HEAD
     */
    fun getChangedFilesSinceBranch(branchName: String): List<GitDiffEntry> {
        return getChangedFiles(baseRef = branchName, compareRef = "HEAD")
    }

    /**
     * Get current branch
     */
    fun getCurrentBranch(): String {
        return repository.branch ?: "unknown"
    }

    /**
     * Get HEAD commit SHA
     */
    fun getHeadCommitHash(): String {
        return try {
            val head = repository.resolve("HEAD")
            head?.name ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }

    /**
     * Get hash of uncommitted changes for Gradle input
     * Returns string containing paths and timestamps of all changed files
     */
    fun getUncommittedChangesHash(): String {
        val changes = getUncommittedChanges()
        if (changes.isEmpty()) {
            return "no-changes"
        }

        // Create string from paths and their timestamps for hashing
        val changesString = changes
            .sortedBy { it.newPath }
            .joinToString("|") { entry ->
                val file = File(projectDir, entry.newPath)
                "${entry.newPath}:${entry.changeType}:${file.lastModified()}"
            }

        return changesString.hashCode().toString()
    }

    private fun resolveCommit(ref: String): RevCommit {
        val objectId: ObjectId = repository.resolve(ref)
        return RevWalk(repository).parseCommit(objectId)
    }

    private fun prepareTreeParser(commit: RevCommit): CanonicalTreeParser {
        val reader = repository.newObjectReader()
        val tree = reader.use { reader ->
            val treeId = commit.tree.id
            val parser = CanonicalTreeParser()
            parser.reset(reader, treeId)
            parser
        }
        return tree
    }

    private fun DiffEntry.toGitDiffEntry(): GitDiffEntry {
        val changeType = when (this.changeType!!) {
            DiffEntry.ChangeType.ADD -> ChangeType.ADDED
            DiffEntry.ChangeType.MODIFY -> ChangeType.MODIFIED
            DiffEntry.ChangeType.DELETE -> ChangeType.DELETED
            DiffEntry.ChangeType.RENAME -> ChangeType.RENAMED
            DiffEntry.ChangeType.COPY -> ChangeType.COPIED
        }

        return GitDiffEntry(
            oldPath = this.oldPath,
            newPath = this.newPath,
            changeType = changeType
        )
    }

    fun close() {
        repository.close()
    }
}

/**
 * Git file change representation
 */
data class GitDiffEntry(
    val oldPath: String,
    val newPath: String,
    val changeType: ChangeType
)
