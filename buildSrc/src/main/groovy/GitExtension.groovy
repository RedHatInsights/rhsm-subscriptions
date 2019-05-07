import org.eclipse.jgit.api.Git
import org.eclipse.jgit.internal.storage.file.FileRepository
import org.eclipse.jgit.lib.ObjectId

class GitExtension {
    final String hash
    final String description

    GitExtension() {
        def repo = new FileRepository('.git')
        def git = new Git(repo)
        try {
            hash = ObjectId.toString(repo.resolve('HEAD'))
            description = git.describe().call()
        } catch (RefNotFoundException) {
            // ignore so we can support the cases of brand new repo, or no tag yet.
        }
    }
}
