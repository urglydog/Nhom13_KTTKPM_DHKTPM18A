#!/bin/bash

echo "Clearing Docker caches..."

# Clear local Maven cache
rm -rf ~/.m2/repository/* 2>/dev/null || true
echo "Cleared local Maven cache"

# Remove Docker build cache for auth-service
echo "Removing Docker build cache..."
docker builder prune -f --filter type=build --filter unused=true || true

# Remove dangling images
docker image prune -f || true

# Rebuild auth-service with --no-cache flag
echo "Building auth-service with fresh cache..."
cd "$(dirname "$0")" || exit
docker-compose -f auth-service/docker-compose.yaml build --no-cache auth-service

echo "Done! Auth-service built successfully"
