# Directory containing Money database file.
# Define on the command line, or defaults to current directory.
MNYDATADIR = ${PWD}

# Money database file password.
# Define on the command line if database is encrypted.
# "MNYPASSWORD=blah make podmanschema"
export MNYPASSWORD

# Container/image settings
IMAGE_NAME := outtamny
IMAGE_WORKDIR := /root/mnydata

.PHONY: help
.DEFAULT_GOAL := help
help:
	@printf 'Usage: make [MNYFILE=] [MNYDATADIR=] [MNYPASSWORD=] TARGET\n'
	@awk 'BEGIN {FS = ":.*##";} /^[a-zA-Z1-9_-]+:.*?##/ { printf "  \033[36m%-20s\033[0m %s\n", $$1, $$2 } /^##@/ { printf "\n\033[1m%s\033[0m\n", substr($$0, 5) } ' $(MAKEFILE_LIST)

.PHONY: podmanstart
podmanstart: ## Start Podman macOS virtual machine
	podman machine start

.PHONY: podmanstop
podmanstop: ## Stop Podman macOS virtual machine
	podman machine stop

.PHONY: podmanrmi
podmanrmi: ## Remove outtamny image
	podman rmi $(IMAGE_NAME)

.PHONY: podmanbuild
podmanbuild: ## Build outtamny image
	podman build -t $(IMAGE_NAME) .

.PHONY: rebuild
rebuild: podmanrmi podmanbuild ## Rebuild outtamny image

.PHONY: podmanrunsh
podmanrunsh: ## Run container and enter shell
	podman run --rm --interactive --tty --volume "$(MNYDATADIR)":$(IMAGE_WORKDIR) -e MNYPASSWORD --entrypoint sh $(IMAGE_NAME)

# MNYFILE="./src/test/resources/sunset-sample-pwd.mny" MNYPASSWORD='123@abc!' make schema
.PHONY: podmanschema
podmanschema: ## Run container and execute command
	$(eval MNYDATADIR := $(shell dirname "$$MNYFILE"))
	$(eval MNYFILE := $(shell basename "$$MNYFILE"))
	podman run --rm --interactive --tty --volume "$(MNYDATADIR)":$(IMAGE_WORKDIR) -e MNYPASSWORD $(IMAGE_NAME) schema -vv --password:env MNYPASSWORD --out "$(IMAGE_WORKDIR)" "$(IMAGE_WORKDIR)/$(MNYFILE)"

.PHONY: test
test: ## Maven test project
	@mvn test

.PHONY: package
package: ## Maven build package
	@mvn clean package -DskipTests

.PHONY: clean
clean: ## Maven clean project
	@mvn clean
