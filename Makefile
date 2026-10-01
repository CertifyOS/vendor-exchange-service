# vendor-exchange-service
#
# Developer targets. Image push and GCE deploy targets arrive with terraform/internal (Step 8).

PROJECT  := certifyos-development
REGION   := us-central1
REPO     := $(REGION)-docker.pkg.dev/$(PROJECT)/vendor-exchange
TAG      := $(shell git rev-parse --short=12 HEAD)
IMAGE    := $(REPO)/vendor-exchange-service:$(TAG)

ifeq ($(OS),Windows_NT)
    GRADLEW := gradlew.bat
else
    GRADLEW := ./gradlew
endif

default: help

.PHONY: help
help: ## List targets
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "} {printf "\033[36m%-24s\033[0m %s\n", $$1, $$2}'

.PHONY: install-git-hooks
install-git-hooks: ## Install the commit-msg hook (Conventional Commits)
	$(GRADLEW) installGitHooks

.PHONY: dev-api
dev-api: ## Run locally in dev mode as the api role
	QUARKUS_PROFILE=dev,api $(GRADLEW) quarkusDev -Dquarkus.console.basic=true

.PHONY: dev-worker
dev-worker: ## Run locally in dev mode as the worker role
	QUARKUS_PROFILE=dev,worker $(GRADLEW) quarkusDev -Dquarkus.console.basic=true

.PHONY: compile
compile: ## Compile main, test and integrationTest sources without running anything
	$(GRADLEW) compileJava compileTestJava compileIntegrationTestJava

.PHONY: format
format: ## Apply Spotless formatting
	$(GRADLEW) spotlessApply

.PHONY: lint
lint: ## Spotless check, Checkstyle and SpotBugs on every source set
	$(GRADLEW) spotlessCheck checkstyleMain checkstyleTest checkstyleIntegrationTest spotbugsMain spotbugsTest spotbugsIntegrationTest

.PHONY: test
test: ## Unit tests
	$(GRADLEW) test

.PHONY: integration-test
integration-test: ## Integration tests (Testcontainers MongoDB; needs Docker)
	$(GRADLEW) integrationTest

.PHONY: check
check: ## Everything CI runs
	$(GRADLEW) check

.PHONY: build-image
build-image: ## Build the fast-jar and the linux/amd64 image
	$(GRADLEW) quarkusBuild
	docker build --platform linux/amd64 -f src/main/docker/Dockerfile.jvm -t $(IMAGE) .

.PHONY: print-image
print-image: ## Print the image name for this commit
	@echo "$(IMAGE)"
