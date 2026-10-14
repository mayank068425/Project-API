.PHONY: help build test run up down logs clean

MVN := mvn

help:
	@echo "build   compile and package"
	@echo "test    run the test suite"
	@echo "run     run locally against localhost MySQL"
	@echo "up      start MySQL and the app in Docker"
	@echo "down    stop everything"
	@echo "logs    follow application logs"
	@echo "clean   remove build output and volumes"

build:
	$(MVN) clean package -DskipTests

test:
	$(MVN) test

run:
	$(MVN) spring-boot:run

up:
	docker compose up -d --build
	@echo "API      http://localhost:8080/api"
	@echo "Swagger  http://localhost:8080/swagger-ui.html"

down:
	docker compose down

logs:
	docker compose logs -f app

clean:
	$(MVN) clean
	docker compose down -v
