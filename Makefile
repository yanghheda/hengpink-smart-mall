.PHONY: install start-commerce-app start-smart-mall-h5 start-commerce-api start-agent-service test check

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

test:
	npm test
	npm run test:services

check:
	npm run check
