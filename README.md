# Weather API

A Spring Boot REST API that returns current weather and forecast data for any city.
Responses are cached in Redis for 12 hours to minimise calls to the Visual Crossing
weather data provider.

## Architecture

```
Client → WeatherController → WeatherService → CacheService (Redis)
                                           ↘ VisualCrossingClient (on cache miss)
```

## Prerequisites

| Requirement | Version |
|-------------|---------|
| Java        | 21      |
| Docker      | 24+     |
| Maven       | 3.9+ (or use the included `./mvnw` wrapper) |

## Environment Variables

| Variable          | Required | Default     | Description                                      |
|-------------------|----------|-------------|--------------------------------------------------|
| `WEATHER_API_KEY` | Yes      | —           | Visual Crossing API key (no default — app will not start without it) |
| `REDIS_HOST`      | No       | `localhost` | Redis hostname                                   |
| `REDIS_PORT`      | No       | `6379`      | Redis port                                       |

Get a free Visual Crossing API key at https://www.visualcrossing.com/

## Running the application

### Redis only (run the app via Maven locally)

```bash
export WEATHER_API_KEY=your_api_key_here

# Start Redis in Docker
docker compose up redis -d

# Run the app
./mvnw spring-boot:run
```

### Full stack via Docker Compose

```bash
export WEATHER_API_KEY=your_api_key_here
docker compose up --build
```

## API Reference

| Resource | URL |
|----------|-----|
| Swagger UI | `http://localhost:8080/swagger-ui.html` |
| OpenAPI JSON | `http://localhost:8080/v3/api-docs` |
| OpenAPI YAML | [`openapi.yml`](openapi-spec/openapi.yml) |


### `GET /api/v1/weather/{city}`

Returns current conditions and a multi-day forecast for the given city. City names are case-insensitive.

| Parameter | Type   | Required | Description           |
|-----------|--------|----------|-----------------------|
| `city`    | string | Yes      | City name (path variable, must not be blank) |

### Response codes

| Status | Meaning                                                               |
|--------|-----------------------------------------------------------------------|
| `200`  | Success — weather data returned (from cache or live fetch)            |
| `400`  | Blank city name or malformed request parameter                        |
| `401`  | Invalid or missing `WEATHER_API_KEY`                                  |
| `404`  | City not recognised by the weather data provider                      |
| `429`  | Rate limit exceeded — more than 20 requests per minute from this IP   |
| `500`  | Unexpected internal error                                             |
| `502`  | Weather data provider returned a server error                         |
| `503`  | Weather data provider is unreachable (timeout / network failure)      |

## Running tests

```bash
./mvnw test
```

## Rate limiting

Each client IP is limited to **20 requests per minute** using a token-bucket
algorithm (Bucket4j). Exceeding the limit returns HTTP 429.
