def call() {
    script {
        def commitHash = env.GIT_COMMIT
        def commitMessage = sh(script: "git log -1 --pretty=%B ${commitHash}", returnStdout: true).trim()
        def match = commitMessage =~ /tag: (\S+)/
        if(match) {
            dockerTag = match[0][1]
            return dockerTag
            // echo "Commit Tag: ${dockerTag}"
        } else {
            error("Tag not found in commit message!\ncommit message: ${commitMessage}")
        }
    }
}
