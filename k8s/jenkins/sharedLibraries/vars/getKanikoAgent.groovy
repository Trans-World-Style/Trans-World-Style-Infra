def call() {
    return [
        kubernetes [
            cloud "kubernetes-docker-job"
            yaml '''
                apiVersion: v1
                kind: Pod
                metadata:
                  labels:
                    role: kaniko
                spec:
                  containers:
                  - name: kaniko
                    image: gcr.io/kaniko-project/executor:debug
                    command:
                    - /busybox/cat
                    imagePullPolicy: Always
                    tty: true
                    volumeMounts:
                    - name: jenkins-docker-cfg
                      mountPath: /kaniko/.docker
                  volumes:
                  - name: jenkins-docker-cfg
                    projected:
                      sources:
                      - secret:
                          name: dockerhub-secret
                          items:
                            - key: .dockerconfigjson
                              path: config.json
                '''
        ]
    ]
}
