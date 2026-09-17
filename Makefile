# Pocket Agent — tek giriş noktası. HANDOFF.md §5'in hedeflere çevrilmiş hali.
.PHONY: all gates go proto android lint unit apk release live-up live-test \
        package npm-package sbom ccs mosh clean help

GRADLE := apps/android/gradlew
export ANDROID_HOME ?= /opt/android-sdk
export ANDROID_SDK_ROOT ?= /opt/android-sdk

help: ## Bu metni göster
	@grep -hE '^[a-zA-Z_-]+:.*## ' $(MAKEFILE_LIST) | awk -F':.*## ' '{printf "  %-14s %s\n", $$1, $$2}'

all: go android ## Go + Android tam derleme

go: ## Go: test + vet + build
	go test ./... && go vet ./... && go build ./...

proto: ## Proto sözleşmeleri lint
	buf lint

gates: ## P00/P01/repo kapıları (secret, license, packaging, protocol, canary)
	./scripts/secret-scan.sh
	./scripts/license-check.sh
	./scripts/check-packaging.sh
	./tests/protocol/privacy-schema.sh
	./tests/security/canary.sh
	buf lint

e2e: ## P04 uçtan uca akış
	./tests/e2e/p04-flow.sh

android: ## Android: lint + unit test + debug APK
	cd apps/android && ./gradlew :app:lintDebug :app:testDebugUnitTest :app:assembleDebug

unit: ## Yalnız Android unit testler
	cd apps/android && ./gradlew :app:testDebugUnitTest

apk: ## Yalnız debug APK
	cd apps/android && ./gradlew :app:assembleDebug
	@echo "APK: apps/android/app/build/outputs/apk/debug/app-debug.apk"

release: ## İmzalı release zinciri (env: POCKET_AGENT_UPLOAD_*)
	./scripts/build-release.sh

live-up: ## Canlı test ortamını ayağa kaldır (backend :8080, gateway :24543, preview :8899)
	./scripts/live-env.sh up

live-down: ## Canlı test ortamını kapat
	./scripts/live-env.sh down

live-env: ## PA_LIVE_* export satırlarını bas (eval ile kullan)
	./scripts/live-env.sh env

live-test: live-up ## Tüm canlı env-gated süitler
	cd apps/android && eval `../../scripts/live-env.sh env` && \
	  ./gradlew :app:testDebugUnitTest --tests 'dev.pocketagent.*LiveTest' --tests 'dev.pocketagent.MoshBootstrapTest' --tests 'dev.pocketagent.PairingLiveTest'

package: ## CLI tarball'ları (dist/)
	./scripts/package-cli.sh

npm-package: ## npm host CLI paketi (dist/pocket-agent-cli-*.tgz)
	./scripts/package-npm.sh

sbom: ## SBOM üret
	./scripts/sbom.sh

ccs: ## GPL CCS paketi
	./scripts/package-ccs.sh

mosh: ## libmoshclient.so kaynaktan (3 ABI)
	./scripts/build-mosh.sh

hook: ## pocket-agent-hook binary'sini /tmp/pa-hook'a derle
	go build -o /tmp/pa-hook ./cmd/pocket-agent-hook

clean: ## Derleme çıktılarını temizle
	cd apps/android && ./gradlew clean
	rm -rf dist/ build/
