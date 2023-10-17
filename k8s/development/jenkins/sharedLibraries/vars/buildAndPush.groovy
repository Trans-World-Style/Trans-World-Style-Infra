def call(String dockerUsername, String imageName, String dockerTag) {
    container('kaniko') {
        script {
            def imageFullName = "${dockerUsername}/${imageName}:${dockerTag}"
            // sh """
            // /kaniko/executor --context `pwd` --verbosity debug --destination ${imageFullName} --cache
            // """
            sh """
            /kaniko/executor --context `pwd` --destination ${imageFullName} --cache
            """
        }
    }
}
