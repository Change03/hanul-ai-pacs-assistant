.PHONY: up down seed test smoke logs

up:
	docker compose up --build

down:
	docker compose down

seed:
	docker compose run --rm seed-dicoms

test:
	docker run --rm -v "$$(pwd)/ai-service:/app" -w /app -e PYTHONPATH=/app python:3.12-slim sh -c "pip install -r requirements.txt && pytest"
	docker run --rm -v "$$(pwd)/backend:/workspace" -w /workspace gradle:8.10-jdk21 gradle test --no-daemon
	cd web && npm install && npm run build && npm test

smoke:
	bash scripts/smoke-test.sh

logs:
	docker compose logs -f --tail=200
