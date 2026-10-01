# GCP Config Vars
REGION := us-central1
SERVICE_NAME := quarkus-example
ARTIFACT_REGISTRY_REPO := cloud-run-source-deploy
GCP_PROD_PROJECT_ID := certifyos-production-platform
GCP_STAGING_PROJECT_ID := certifyos-development

# Vars that can be passed in
PROFILE ?= dev
GCP_PROJECT_ID ?= certifyos-development

IMAGE_NAME := $(REGION)-docker.pkg.dev/$(GCP_PROJECT_ID)/$(ARTIFACT_REGISTRY_REPO)/$(SERVICE_NAME)
TAG := $(shell date +%Y%m%d%H%M%S)
FULL_IMAGE_NAME := $(IMAGE_NAME):$(TAG)

ifeq ($(OS),Windows_NT)
    GRADLEW := gradlew.bat
else
    GRADLEW := ./gradlew
endif

# Default target
default: help

###### Targets for use by devs ######
.PHONY: help
help: ## This help command
	@echo "Available Targets:"
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "} {printf "\033[36m%-30s\033[0m %s\n", $$1, $$2}'

# Development Tasks
.PHONY: install-git-hooks
install-git-hooks: ## Install project's git hooks to your local repo
	$(GRADLEW) installGitHooks

.PHONY: dev
dev: ## Run the application locally in dev mode
	$(GRADLEW) quarkusDev -Dquarkus.console.basic=true

.PHONY: run
run: build-local-native ## Run the native binary of the application locally
	./build/*SNAPSHOT-runner

.PHONY: format-fix
format-fix: ## Format the code
	$(GRADLEW) spotlessApply

.PHONY: format-check
format-check: ## Format the code
	$(GRADLEW) spotlessCheck

.PHONY: lint
lint: ## Lint the codebase
	$(GRADLEW) checkstyleMain checkstyleTest checkstyleIntegrationTest

.PHONY: integration-test
integration-test: ## Run integration tests with Firestore emulator
	$(GRADLEW) integrationTest

.PHONY: integration-test-one
integration-test-one:
	$(GRADLEW) integrationTest --tests "$(TEST)"

.PHONY: test
test: ## Run unit tests
	$(GRADLEW) test

.PHONY: test
test-one:
	$(GRADLEW) test --tests "$(TEST)"

.PHONY: native-test
native-test: ## Run native integration tests with Firestore emulator
	$(GRADLEW) testNative -x test -x integrationTest

.PHONY: coverage
coverage: codegen-test test ## Generate a code coverage report (unit test only)
	$(GRADLEW) jacocoTestReport

.PHONY: static-analysis
static-analysis: ## Run static analysis using SpotBugs
	$(GRADLEW) spotbugsMain spotbugsTest  spotbugsIntegrationTest

.PHONY: check
check: clean format-check ## Run all tests, lint and format the code
	$(GRADLEW) check

.PHONY: javadoc
javadoc: ## Run javadoc
	$(GRADLEW) javadoc

# Build Tasks
.PHONY: clean
clean: ## Clean the build directory
	$(GRADLEW) clean

.PHONY: build-local
build-local: clean ## Build JAR for running on local JVM
	./gradlew build \
		-Pquarkus.profile=$(PROFILE) \
		-x integrationTest \
		-x test \
		-x jacocoTestCoverageVerification \
		-x jacocoTestReport \
		-x spotbugsIntegrationTest \
		-x spotbugsMain \
		-x spotbugsNativeTest \
		-x spotbugsTest \
		-x spotlessApply \
		-x spotlessCheck;

.PHONY: build-local-native
build-local-native: clean ## Build native binary for local machine
	$(GRADLEW) build \
		-Dquarkus.native.enabled=true \
		-Dquarkus.package.jar.enabled=false \
		-Pquarkus.profile=$(PROFILE) \
		-x integrationTest \
		-x test \
		-x jacocoTestCoverageVerification \
		-x jacocoTestReport \
		-x spotbugsIntegrationTest \
		-x spotbugsMain \
		-x spotbugsNativeTest \
		-x spotbugsTest \
		-x spotlessApply \
		-x spotlessCheck;

# Deployment Tasks
.PHONY: deploy-staging
deploy-staging: check ## Deploy to the staging environment
	@$(MAKE) deploy GCP_PROJECT_ID=$(GCP_STAGING_PROJECT_ID) PROFILE=dev TAG=canary

.PHONY: deploy-prod
deploy-prod: check ## Deploy to the production environment
	@$(MAKE) deploy GCP_PROJECT_ID=$(GCP_PROD_PROJECT_ID) PROFILE=prod TAG=production

###### Targets that should not be used directly ######

.PHONY: build-native
build-native: clean
	$(GRADLEW) build \
		-Dquarkus.native.enabled=true \
		-Dquarkus.native.container-build=true \
		-Dquarkus.native.container-runtime=docker \
		-Dquarkus.native.container-runtime-options=--platform=linux/amd64 \
		-x integrationTest \
		-Pquarkus.profile=$(PROFILE) \
		-x integrationTest \
		-x test \
		-x jacocoTestCoverageVerification \
		-x jacocoTestReport \
		-x spotbugsIntegrationTest \
		-x spotbugsMain \
		-x spotbugsNativeTest \
		-x spotbugsTest \
		-x spotlessApply \
		-x spotlessCheck;

.PHONY: build-image
build-image:
	docker build \
		--platform=linux/amd64 \
		--build-arg QUARKUS_PROFILE=$(PROFILE) \
		-f src/main/docker/Dockerfile.native \
		-t $(FULL_IMAGE_NAME) \
		.

# Deployment Tasks
# Allow overriding the image name for deployment, default to calculated FULL_IMAGE_NAME
IMAGE_NAME_TO_DEPLOY ?= $(FULL_IMAGE_NAME)

.PHONY: deploy-cloud-run
deploy-cloud-run:
	gcloud run deploy $(SERVICE_NAME) \
		--image $(IMAGE_NAME_TO_DEPLOY) \
		--region $(REGION) \
		--project $(GCP_PROJECT_ID) \
		--no-allow-unauthenticated \
		--ingress=internal-and-cloud-load-balancing \
		--tag=$(TAG) \
		--no-traffic

.PHONY: push-image
push-image:
#	gcloud auth configure-docker $(REGION)-docker.pkg.dev --quiet
	docker push $(FULL_IMAGE_NAME)

.PHONY: shift-traffic
shift-traffic:
	gcloud run services update-traffic $(SERVICE_NAME) \
		--to-tags=$(TAG)=100 \
		--region $(REGION) \
		--project $(GCP_PROJECT_ID)

.PHONY: deploy
deploy: build-native build-image push-image deploy-cloud-run

.PHONY: print-image-name
print-image-name: ## Print the full image name (helper for CI/CD)
	@echo "$(FULL_IMAGE_NAME)"
