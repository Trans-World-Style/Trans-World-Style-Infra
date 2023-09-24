def call(String dockerUsername, String imageName) {
    container('kaniko') {
        script {
            def imageFullName = "${dockerUsername}/${imageName}:${dockerTag}"
            sh """
            /kaniko/executor --context `pwd` --verbosity debug --destination ${imageFullName} --cache
            """
        }
    }
}
