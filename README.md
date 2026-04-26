# Kotlin Spring Boot API

REST API built with Kotlin + Spring Boot + Gradle.

## Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/items` | List all items |
| GET | `/api/items/{id}` | Get item by ID |
| POST | `/api/items` | Create item |
| PUT | `/api/items/{id}` | Update item |
| DELETE | `/api/items/{id}` | Delete item |

## Run

```bash
./gradlew bootRun
```

API available at `http://localhost:8080`

H2 console at `http://localhost:8080/h2-console`
