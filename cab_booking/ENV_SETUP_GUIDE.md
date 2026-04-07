# Environment Configuration Guide

## Overview

Tất cả các sensitive configuration (database credentials, JWT keys, service URLs) đã được extract vào file `.env` để:**
- ✅ Tránh hardcode secrets trong source code
- ✅ Dễ quản lý khác nhau giữa dev/test/production
- ✅ Bảo vệ credentials không commit lên git

## File Structure

```
cab_booking/
├── .env                    # Actual environment variables (NEVER commit)
├── .env.example           # Template for team reference
├── .gitignore             # Already contains .env exclusion
│
├── auth-service/
│   ├── docker-compose.yaml         # Uses ${VAR} placeholders
│   ├── .gitignore                   # Includes .env exclusion
│   └── src/main/resources/certs/    # Private key files (fallback only)
│
├── cab-booking-config/
│   ├── auth-service.yaml           # Uses ${VAR} placeholders  
│   └── api-gateway.yaml
│
├── config-server/
├── eureka/
├── api-gateway/
└── common/
```

## Setup Instructions

### 1. Local Development Setup

```bash
# Navigate to project root
cd cab_booking/

# Copy example template
cp .env.example .env

# Edit .env with your local values
# For development:
# - Use localhost or docker service names (auth-db, eureka-server, etc.)
# - Update passwords as needed
# - Generate new JWT_PRIVATE_KEY if required
```

### 2. Docker Compose Startup

```bash
# .env file will be automatically loaded by docker-compose
docker-compose up -d

# Verify services are running
docker-compose ps

# View logs
docker-compose logs -f auth-service
docker-compose logs -f eureka
docker-compose logs -f config-server
```

### 3. JWT Private Key Generation (if needed)

```bash
# Generate 2048-bit RSA key in PEM format
openssl genrsa -out private_key.pem 2048

# Export as PKCS#8 (Java format)
openssl pkcs8 -topk8 -inform pem -in private_key.pem -outform pem -nocrypt

# Copy the output to JWT_PRIVATE_KEY in .env
# Remember to escape newlines as \n for single-line env variable
```

## Environment Variables Reference

### Database Configuration
| Variable | Purpose | Example |
|----------|---------|---------|
| `AUTH_DB_NAME` | Database name | `auth_db` |
| `AUTH_DB_USER` | Database user | `admin` |
| `AUTH_DB_PASSWORD` | Database password | `your_password` |
| `AUTH_DB_PORT` | Database port | `5433` |
| `AUTH_DB_JDBC_URL` | JDBC connection string | `jdbc:postgresql://auth-db:5432/auth_db` |

### Service Configuration
| Variable | Purpose | Example |
|----------|---------|---------|
| `AUTH_SERVICE_PORT` | Auth service port | `8081` |
| `CONFIG_SERVER_PORT` | Config server port | `8888` |
| `EUREKA_SERVER_PORT` | Eureka registry port | `8761` |
| `API_GATEWAY_PORT` | API Gateway port | `8080` |

### Service Discovery
| Variable | Purpose | Example |
|----------|---------|---------|
| `EUREKA_DEFAULT_ZONE` | Eureka registry URL | `http://eureka-server:8761/eureka/` |
| `EUREKA_HOSTNAME` | Eureka server hostname | `eureka-server` |

### JWT & Security
| Variable | Purpose | Example |
|----------|---------|---------|
| `JWT_PRIVATE_KEY` | RSA private key (PEM format) | `-----BEGIN PRIVATE KEY-----\n...\n-----END PRIVATE KEY-----` |

### Application Settings
| Variable | Purpose | Example |
|----------|---------|---------|
| `APP_PROFILE` | Active profile | `dev`, `test`, `docker`, `production` |
| `CONFIG_LOCATION` | Config server file location | `file:/app/cab-booking-config/` |
| `SPRING_PROFILES_ACTIVE` | Spring profile | `native` |

## Files Using Environment Variables

### Docker Compose Files
✅ `cab_booking/auth-service/docker-compose.yaml`
- All database credentials now reference `${AUTH_DB_*}` variables
- JWT key references `${JWT_PRIVATE_KEY}`
- Service ports reference `${*_PORT}` variables

### Configuration Files
✅ `cab_booking/cab-booking-config/auth-service.yaml`
- Datasource URL: `${AUTH_DB_JDBC_URL}`
- Username: `${AUTH_DB_USER}`
- Password: `${AUTH_DB_PASSWORD}`
- Eureka: `${EUREKA_DEFAULT_ZONE}`

### Java Configuration
✅ `cab_booking/auth-service/src/main/java/iuh/fit/auth_service/config/JwtConfig.java`
- `loadPrivateKey()` - Reads `JWT_PRIVATE_KEY` from environment first
- `loadPublicKey()` - Derives public key from private key
- Fallback to classpath files if env variable not set

## Git Ignore Rules

The following are already configured:

```
# Root .gitignore
/cab_booking/.env

# Auth Service .gitignore  
.env
/src/main/resources/certs/private_key.pem
```

**IMPORTANT**: Always verify `.gitignore` before committing!
```bash
git status  # Check nothing sensitive appears
git add .
git commit -m "..."
```

## Environment-Specific Setup

### Development (Local)
```bash
# .env
AUTH_DB_HOST=localhost
AUTH_DB_PORT=5433
EUREKA_DEFAULT_ZONE=http://localhost:8761/eureka/
API_GATEWAY_URL=http://localhost:8080
APP_PROFILE=dev
```

### Docker Compose (Local Containers)
```bash
# .env (current setup)
AUTH_DB_HOST=auth-db       # Docker service name
AUTH_DB_PORT=5433
EUREKA_DEFAULT_ZONE=http://eureka-server:8761/eureka/
APP_PROFILE=docker
```

### Production (Remote Deployment)
```bash
# .env
AUTH_DB_HOST=prod-db.example.com
AUTH_DB_PORT=5432
AUTH_DB_PASSWORD=<use-secrets-manager>
JWT_PRIVATE_KEY=<load-from-vault>
EUREKA_DEFAULT_ZONE=http://eureka-prod:8761/eureka/
APP_PROFILE=production
```

## Troubleshooting

### Issue: Docker container fails to start with "password authentication failed"
**Solution**: Check `.env` values match `docker-compose.yaml` placeholders
```bash
# Verify variable substitution
docker-compose config | grep POSTGRES_PASSWORD
```

### Issue: JWT token signing fails
**Solution**: Verify `JWT_PRIVATE_KEY` is properly formatted in `.env`
```bash
# Check private key format (should start/end with PEM markers)
echo "$JWT_PRIVATE_KEY" | head -c 50
```

### Issue: Services can't discover each other
**Solution**: Verify `EUREKA_DEFAULT_ZONE` URL is accessible
```bash
# From inside docker network
docker-compose exec auth-service curl http://eureka-server:8761/eureka/apps
```

## Security Best Practices

1. **Never commit `.env`** - Already in `.gitignore`
2. **Use strong passwords** - Especially for production
3. **Rotate JWT keys periodically** - Generate new keys and update `.env`
4. **Use secrets manager in production** - AWS Secrets, Azure Key Vault, Vault, etc.
5. **Limit file permissions** - `chmod 600 .env` on Unix systems
6. **Different keys per environment** - Don't reuse keys across dev/test/prod

## Testing Environment Variables

```bash
# Verify all required variables are set
cd cab_booking/
docker-compose config  # Shows resolved values (with ${VAR} substituted)

# Check specific service
docker-compose exec auth-service env | grep AUTH_DB
docker-compose exec auth-service env | grep JWT

# View logs to confirm values applied
docker-compose logs auth-service | grep "datasource\|jdbc\|jwt"
```

## Next Steps

- [ ] Update `.env` with your actual database password
- [ ] Generate new `JWT_PRIVATE_KEY` for production
- [ ] Test `docker-compose up` to verify all variables are resolved
- [ ] Document any additional environment-specific variables
- [ ] Set up CI/CD pipeline to handle `.env` securely
- [ ] Consider secrets management solution for production (AWS Secrets Manager, HashiCorp Vault, etc.)
