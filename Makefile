.PHONY: install start-commerce-app start-smart-mall-h5 start-commerce-api start-agent-service infra-up infra-down infra-status db-migrate db-seed rag-index eval-golden test-db-integration test check

install:
	npm ci
	python3 -m venv services/agent-service/.venv
	services/agent-service/.venv/bin/python -m pip install -r services/agent-service/requirements.lock

start-commerce-app:
	npm run start:commerce-app

start-smart-mall-h5:
	npm run start:smart-mall-h5

start-commerce-api:
	npm run start:commerce-api

start-agent-service:
	npm run start:agent-service

infra-up:
	docker compose --env-file deploy/.env -f deploy/docker-compose.yml up -d

infra-down:
	docker compose --env-file deploy/.env -f deploy/docker-compose.yml down

infra-status:
	docker compose --env-file deploy/.env -f deploy/docker-compose.yml ps

db-migrate:
	test -n "$$MYSQL_URL" && test -n "$$MYSQL_USERNAME" && test -n "$$MYSQL_PASSWORD"
	mvn -f services/commerce-api/pom.xml -Dflyway.url="$$MYSQL_URL" -Dflyway.user="$$MYSQL_USERNAME" -Dflyway.password="$$MYSQL_PASSWORD" flyway:migrate

db-seed: db-migrate
	test -n "$$MYSQL_URL" && test -n "$$MYSQL_USERNAME" && test -n "$$MYSQL_PASSWORD"
	mvn -f services/commerce-api/pom.xml -Dspring-boot.run.main-class=com.hengpick.mall.catalog.importer.CommerceDatasetImporter spring-boot:run

rag-index:
	test -n "$$DATASET_VERSION" && test -n "$$MYSQL_HOST" && test -n "$$MYSQL_USERNAME" && test -n "$$MYSQL_PASSWORD" && test -n "$$MYSQL_DATABASE" && test -n "$$QDRANT_URL"
	PYTHONPATH=services/agent-service services/agent-service/.venv/bin/python -m app.knowledge.cli

eval-golden:
	npm run eval:golden

test-db-integration:
	./scripts/test-vm-database.sh

test:
	npm test
	npm run test:services

check:
	npm run check
