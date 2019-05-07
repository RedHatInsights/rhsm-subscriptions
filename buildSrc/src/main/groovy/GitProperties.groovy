import org.gradle.api.Plugin
import org.gradle.api.Project

class GitProperties implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.extensions.create('git', GitExtension)
    }
}
