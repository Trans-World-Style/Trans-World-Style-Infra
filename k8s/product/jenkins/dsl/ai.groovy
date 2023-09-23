job("tws-ai-job") {
	description()
	keepDependencies(false)
	scm {
		git {
			remote {
				github("Trans-World-Style/Trans-World-Style-AI", "https")
				credentials("github-access-token")
			}
			branch("*/main")
			branch("*/test")
		}
	}
	disabled(false)
	triggers {
		githubPush()
	}
	concurrentBuild(false)
	steps {
		shell("""mkdir -p ./trans-back/src/main/resources/ssl
cp -L /etc/letsencrypt/live/tw-style.duckdns.org/* ./trans-back/src/main/resources/ssl

echo "ENV=prod
PORT=12530
S3_BUCKET_NAME=trans-world-style
AWS_ACCESS_KEY=AKIA4NJHVZKRCWWUG5KJ
AWS_SECRET_KEY=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
COMPOSE_PROJECT_NAME=PROD_AI" > .env-prod

echo "ENV=test
PORT=12531
S3_BUCKET_NAME=trans-world-style-test
AWS_ACCESS_KEY=AKIA4NJHVZKRCWWUG5KJ
AWS_SECRET_KEY=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
COMPOSE_PROJECT_NAME=TEST_AI" > .env-test

if [ "\${GIT_BRANCH}" = "origin/main" ]; then
	cp .env-prod .env
    docker-compose -f docker-compose.yaml -f docker-compose-gpu.yaml build
    docker-compose -f docker-compose.yaml -f docker-compose-gpu.yaml down
    docker-compose -f docker-compose.yaml -f docker-compose-gpu.yaml up -d
elif [ "\${GIT_BRANCH}" = "origin/test" ]; then
    cp .env-test .env
    docker-compose -f docker-compose.yaml build
    docker-compose -f docker-compose.yaml down
    docker-compose -f docker-compose.yaml up -d
fi

# mkdir -p "\${GIT_BRANCH}"
# rsync -av . "\${GIT_BRANCH}"/ --exclude "\${GIT_BRANCH}"
# if [ "\${GIT_BRANCH}" = "origin/main" ]; then
# 	cp "\${GIT_BRANCH}"/.env-prod "\${GIT_BRANCH}"/.env
#     docker-compose -f "\${GIT_BRANCH}"/docker-compose.yaml -f "\${GIT_BRANCH}"/docker-compose-gpu.yaml build
#     docker-compose -f "\${GIT_BRANCH}"/docker-compose.yaml -f "\${GIT_BRANCH}"/docker-compose-gpu.yaml down
#     docker-compose -f "\${GIT_BRANCH}"/docker-compose.yaml -f "\${GIT_BRANCH}"/docker-compose-gpu.yaml up -d
# elif [ "\${GIT_BRANCH}" = "origin/test" ]; then
#     cp "\${GIT_BRANCH}"/.env-test "\${GIT_BRANCH}"/.env
#     docker-compose -f "\${GIT_BRANCH}"/docker-compose.yaml build
#     docker-compose -f "\${GIT_BRANCH}"/docker-compose.yaml down
#     docker-compose -f "\${GIT_BRANCH}"/docker-compose.yaml up -d
# fi""")
	}
}
