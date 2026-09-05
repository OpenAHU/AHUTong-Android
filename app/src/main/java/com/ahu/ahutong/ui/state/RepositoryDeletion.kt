package com.ahu.ahutong.ui.state

import com.ahu.ahutong.data.repository.DownloadedFile
import kotlinx.coroutines.CancellationException

internal data class RepositoryDeletionResult(
    val remainingFiles: List<DownloadedFile>,
    val failedPaths: Set<String>
)

/** Finish every deletion before taking the snapshot displayed by the downloads screen. */
internal fun deleteDownloadedFiles(
    paths: Collection<String>,
    delete: (String) -> Boolean,
    listFiles: () -> List<DownloadedFile>
): RepositoryDeletionResult {
    val requestedPaths = paths.toSet()
    requestedPaths.forEach { path ->
        try {
            delete(path)
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            // A failed file must not prevent the remaining selected files from being deleted.
        }
    }
    val remainingFiles = listFiles()
    return RepositoryDeletionResult(
        remainingFiles = remainingFiles,
        // A file removed outside the app is already gone; only surviving rows need a retry.
        failedPaths = remainingFiles.mapTo(mutableSetOf()) { it.path }.intersect(requestedPaths)
    )
}
