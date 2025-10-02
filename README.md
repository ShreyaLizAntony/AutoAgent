<<<<<<< HEAD
# AutoAgent
AI Car Rental Assistant • FastAPI + Ollama + FAISS
=======
# Ollama Llama2 Spring Boot Project

This project is a Spring Boot Java application that exposes REST endpoints to interact with the Llama2 model deployed via Ollama.

## Features
- POST `/api/llama2/generate`: Send a prompt and receive generated text from Llama2 via Ollama.

## Requirements
- Java 17+
- Maven
- Ollama running locally with Llama2 model available

## Running Ollama
Make sure Ollama is running and the Llama2 model is pulled:
```
ollama pull llama2
ollama serve
```

## Build & Run
```
mvn clean install
mvn spring-boot:run
```

## Example Request
```
curl -X POST http://localhost:8080/api/llama2/generate -H "Content-Type: text/plain" -d "Your prompt here"
```
>>>>>>> e22cdb9 (Initial commit)
