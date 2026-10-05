# vendor-exchange-service
#
# Developer verbs. Image push and GCE deploy targets arrive with terraform/internal (Step 8); production
# deploys happen only through CI.

PROJECT  := certifyos-development
REGION   := us-central1
REPO     := $(REGION)-docker.pkg.dev/$(PROJECT)/vendor-exchange
TAG      := $(shell git rev-parse --short=12 HEAD)
IMAGE    := $(REPO)/vendor-exchange-service:$(TAG)
ROLE     ?= api

ifeq ($(OS),Windows_NT)
    GRADLEW := gradlew.bat
else
    GRADLEW := ./gradlew
endif

default: help

.PHONY: help
help: ## List targets
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "} {printf "\033[36m%-16s\033[0m %s\n", $$1, $$2}'

.PHONY: bootstrap
bootstrap: ## One-time setup: git hooks and a local .env
	$(GRADLEW) installGitHooks
	@test -f .env || cp .env.example .env
	@echo "Fill the (required) values in .env, then: make dev"

.PHONY: dev
dev: ## Run locally in dev mode; ROLE=api (default) or ROLE=worker
	QUARKUS_PROFILE=dev,$(ROLE) $(GRADLEW) quarkusDev -Dquarkus.console.basic=true

.PHONY: fmt
fmt: ## Apply Spotless formatting
	$(GRADLEW) spotlessApply

.PHONY: lint
lint: ## Spotless check, Checkstyle and SpotBugs on every source set (no tests)
	$(GRADLEW) spotlessCheck checkstyleMain checkstyleTest checkstyleIntegrationTest spotbugsMain spotbugsTest spotbugsIntegrationTest

.PHONY: compile
compile: ## Compile main, test and integrationTest sources without running anything
	$(GRADLEW) compileJava compileTestJava compileIntegrationTestJava

.PHONY: test
test: ## Unit tests (plain JUnit, ArchUnit)
	$(GRADLEW) test

.PHONY: check
check: ## Everything CI runs (lint, unit tests, integration tests on Testcontainers)
	$(GRADLEW) check

.PHONY: openapi
openapi: ## Regenerate openapi/openapi.yaml from the code (commit the result; CI verifies it)
	$(GRADLEW) quarkusBuild
	cp build/openapi/openapi.yaml openapi/openapi.yaml

.PHONY: build
build: ## Fast-jar and the linux/amd64 image
	$(GRADLEW) quarkusBuild
	docker build --platform linux/amd64 -f src/main/docker/Dockerfile.jvm -t $(IMAGE) .
	@echo "$(IMAGE)"
