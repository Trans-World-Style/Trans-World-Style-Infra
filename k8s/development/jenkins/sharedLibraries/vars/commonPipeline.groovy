def call(Closure body) {
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  properties([
    parameters([
      string(name: 'CONFIG_MAP_NAME', defaultValue: '', description: 'Name of the ConfigMap'),
      string(name: 'CONFIG_MAP_NAMESPACE', defaultValue: '', description: 'Namespace of the ConfigMap'),
      string(name: 'CONFIG_FILE_NAME', defaultValue: '', description: 'Name of the Config File from ConfigMap'),
      string(name: 'CONFIG_MAP_MOUNT_PATH', defaultValue: '', description: 'Mount path for the ConfigMap in the container')
    ])
  ])

  if (!config.imageName || !config.manifestRepo || !config.manifestDir || !config.manifestFile || !config.manifestBranch) {
    error("Mandatory config values are missing!")
  }
  def useConfigMap = !(params.CONFIG_MAP_NAME == '' || params.CONFIG_MAP_NAMESPACE == '' || params.CONFIG_MAP_MOUNT_PATH == '' || params.CONFIG_MAP_NAME == '')

  pipeline {
    agent {
      kubernetes {
        yaml '''
          apiVersion: v1
          kind: Pod
          metadata:
            labels:
              jenkins: slave
          spec:
            serviceAccountName: jenkins-admin
            containers:
            - name: kubectl
              image: bitnami/kubectl:latest
              command:
              - cat
              tty: true
            - name: kaniko
              image: gcr.io/kaniko-project/executor:debug
              command:
              - /busybox/cat
              imagePullPolicy: Always
              tty: true
    '''
      }
    }
    environment {
      IMAGE_NAME = "${config.imageName}"
      MANIFEST_REPO = "${config.manifestRepo}"
      MANIFEST_DIR = "${config.manifestDir}"
      MANIFEST_FILE = "${config.manifestFile}"
      MANIFEST_BRANCH = "${config.manifestBranch}"
      GIT_COMMIT_SHORT = sh(script: 'echo $GIT_COMMIT | cut -c 1-12', returnStdout: true).trim()
    }
    stages {
      stage('extract docker tag') {
        steps {
          script {
            extractDockerTag()
            env.IMAGE_TAG = env.DOCKER_TAG + '-' + GIT_COMMIT_SHORT
          }
        }
      }

      stage('Before Build Stages') {
        when {
          expression { return config.beforeBuildStages }
        }
        steps {
          script {
            config.beforeBuildStages.each { stageName, stageClosure ->
              stageClosure.call()
            }
          }
        }
      }

      stage('Load ConfigMap Data') {
        when {
          expression { return useConfigMap }
        }
        steps {
          container('kubectl') {
            script {
              sh """
                kubectl get configmap ${params.CONFIG_MAP_NAME} -n ${params.CONFIG_MAP_NAMESPACE} -o jsonpath='{.data.${params.CONFIG_FILE_NAME}}' > ${params.CONFIG_FILE_NAME}
              """
            }
          }
        }
      }

      stage('Build and Push') {
        steps {
          container('kaniko') {
            script {
              withCredentials([usernamePassword(credentialsId: 'dockerhub-secret',
                                usernameVariable: 'DOCKERHUB_ID', passwordVariable: 'DOCKERHUB_TOKEN')]) {
                // def imageFullName = "${DOCKERHUB_ID}/${IMAGE_NAME}:${IMAGE_TAG}"
                sh '''
                  echo "{\"auths\":{\"https://index.docker.io/v1/\":{\"auth\":\"$(echo -n "$DOCKERHUB_ID:$DOCKERHUB_TOKEN" | base64)\"}}}" > /kaniko/.docker/config.json

                  /kaniko/executor --context `pwd` --destination $DOCKERHUB_ID/$IMAGE_NAME:$IMAGE_TAG --cache
                '''
              }
            }
          }
        }
      }

      stage('Delivery To Github Manifest') {
        steps {
          script {
            withCredentials([usernamePassword(credentialsId: 'github-app-credentials',
              usernameVariable: 'GITHUB_APP_ID', passwordVariable: 'GITHUB_ACCESS_TOKEN')]) {
              dir("${AGENT_WORKDIR}") {
                sh 'git clone https://$GITHUB_APP_ID:$GITHUB_ACCESS_TOKEN@github.com/$MANIFEST_REPO'
                dir("${MANIFEST_REPO.split('/')[1].replace('.git', '')}") {
                  sh """
                    git checkout ${MANIFEST_BRANCH}
                    sed -i 's|${DOCKERHUB_USERNAME}/${IMAGE_NAME}:.*|${env.DOCKERHUB_USERNAME}/${IMAGE_NAME}:${env.IMAGE_TAG}|' ${MANIFEST_DIR}/${MANIFEST_FILE}
                    git config user.name '${env.AUTHOR_NAME}'
                    git config user.email '${env.AUTHOR_EMAIL}'
                    git add .
                    git commit -m 'Update image tag to ${env.IMAGE_TAG}'
                  """
                  sh 'git push https://$GITHUB_APP_ID:$GITHUB_ACCESS_TOKEN@github.com/$MANIFEST_REPO $MANIFEST_BRANCH'
                }
              }
            }
          }
        }
      }

      stage('After Build Stages') {
        when {
          expression { return config.afterBuildStages }
        }
        steps {
          script {
            config.afterBuildStages.each { stageName, stageClosure ->
              echo "Starting custom stage: ${stageName}"
              stageClosure.call()
              echo "Finished custom stage: ${stageName}"
            }
          }
        }
      }
    }
    post {
      always {
        script {
          echo "Keeping the pod running for debugging..."
          sleep 3600
        }
      }
    }

    // post {
    //     failure {
    //         script {
    //             echo "An error occurred. Keeping the pod running for debugging..."
    //             sleep 3600 // Pod will be kept running for 1 hour 
    //         }
    //     }
    // }
  }
}