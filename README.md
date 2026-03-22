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

## Usage

```bash
# Get weather for a city
curl http://localhost:8080/api/v1/weather/London

# City names are case-insensitive (all resolve to the same cache key)
curl http://localhost:8080/api/v1/weather/london
curl http://localhost:8080/api/v1/weather/LONDON
```

### Example response

```json
{
  "resolvedAddress": "London, England, United Kingdom",
  "timezone": "Europe/London",
  "description": "Partly cloudy throughout the day.",
  "currentConditions": {
    "temp": 14.2,
    "conditions": "Partially cloudy",
    "humidity": 72.0
  },
  "days": [...]
}
```

### Error responses

All errors return a consistent shape:

```json
{
  "timestamp": "2026-03-22T10:00:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "City not found or invalid request: xyz"
}
```

| Status | Meaning                                      |
|--------|----------------------------------------------|
| 400    | Blank city name or invalid request parameter |
| 429    | Rate limit exceeded (20 requests/minute/IP)  |
| 502    | Visual Crossing API returned an error        |
| 503    | Visual Crossing API is unreachable           |

## Running tests

```bash
./mvnw test
```

## Rate limiting

Each client IP is limited to **20 requests per minute** using a token-bucket
algorithm (Bucket4j). Exceeding the limit returns HTTP 429.
