def call() {
    script {
        def commitHash = env.GIT_COMMIT
        def commitMessage = sh(script: "git log -1 --pretty=%B ${commitHash}", returnStdout: true).trim()
        def authorName = sh(script: "git log -1 --pretty=%an ${commitHash}", returnStdout: true).trim()
        def authorEmail = sh(script: "git log -1 --pretty=%ae ${commitHash}", returnStdout: true).trim()
        def match = commitMessage =~ /tag: (\S+)/

        if(match) {
            env.DOCKER_TAG = match[0][1]
            env.AUTHOR_NAME =  authorName
            env.AUTHOR_EMAIL = authorEmail
        } else {
            error("Tag not found in commit message!\ncommit message: ${commitMessage}")
        }
    }
}
