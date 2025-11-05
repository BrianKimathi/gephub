# Gephub - Multi-Product SaaS Platform

A comprehensive microservices platform providing:
- **Auth Service**: Unified authentication, API keys, JWT tokens
- **KYC Service**: Identity verification with liveness detection
- **Meets Service**: Video/audio collaboration (like Google Meet)
- **Builder Service**: AI-powered website builder with deployment

## Architecture

- **Microservices**: Spring Boot 3.5+ (Java 21)
- **Database**: PostgreSQL (one per service)
- **Cache/Sessions**: Redis
- **Message Queue**: RabbitMQ (for KYC processing)
- **Media Processing**: Python worker (ONNX models)
- **Video/Audio**: LiveKit SFU
- **Deployment**: Docker & Docker Compose

## Quick Start

### Prerequisites
- Docker & Docker Compose
- Java 21 (for local development)
- Maven 3.8+
- PostgreSQL client (optional)

### Local Development

1. **Clone repository:**
   ```bash
   git clone https://github.com/YOUR_USERNAME/gephub.git
   cd gephub
   ```

2. **Generate JWT keys:**
   ```bash
   ./scripts/generate-jwt-keys.sh
   # Or manually:
   openssl genrsa -out jwt_private.pem 2048
   openssl rsa -in jwt_private.pem -pubout -out jwt_public.pem
   ```

3. **Create .env file:**
   ```bash
   cp .env.example .env
   # Edit .env with your values
   ```

4. **Start all services:**
   ```bash
   docker compose up -d
   ```

5. **Check health:**
   ```bash
   curl http://localhost:8080/actuator/health
   ```

### Services

- **Auth Service**: http://localhost:8080
- **KYC Service**: http://localhost:8081
- **Meets Service**: http://localhost:8082
- **Builder Service**: http://localhost:8083

### API Documentation

- Swagger UI available at:
  - Auth: http://localhost:8080/swagger-ui.html
  - KYC: http://localhost:8081/swagger-ui.html
  - Meets: http://localhost:8082/swagger-ui.html
  - Builder: http://localhost:8083/swagger-ui.html

## Deployment

See [DEPLOYMENT_SETUP.md](./DEPLOYMENT_SETUP.md) for detailed deployment instructions.

## Project Structure

```
gephub/
├── gephub-auth-service/    # Authentication & API keys
├── kyc-service/            # KYC verification
├── meets-service/          # Video/audio meetings
├── builder-service/        # Website builder
├── kyc-worker/            # Python KYC processing worker
├── docker-compose.yml      # Local development
└── scripts/                # Deployment scripts
```

## Configuration

All services use environment variables. See `.env.example` for required variables.

### Required API Keys

1. **GitHub Access Token** (for Builder service)
   - Scopes: `repo`
   - Settings → Developer settings → Personal access tokens

2. **AI API Key** (for Builder service)
   - OpenAI: https://platform.openai.com/api-keys
   - Or Anthropic: https://console.anthropic.com/

3. **LiveKit API Key/Secret** (for Meets service)
   - From LiveKit dashboard or self-hosted instance

## Development

### Building Services

```bash
# Build all services
cd gephub-auth-service && mvn clean package
cd ../kyc-service && mvn clean package
cd ../meets-service && mvn clean package
cd ../builder-service && mvn clean package
```

### Running Locally (without Docker)

1. Start PostgreSQL, Redis, RabbitMQ
2. Set environment variables
3. Run each service:
   ```bash
   cd gephub-auth-service
   mvn spring-boot:run
   ```

## Testing

```bash
# Test auth service
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"Test123!","organizationName":"Test Org"}'
```

## License

[Your License Here]

## Support

[Your Support Contact]

