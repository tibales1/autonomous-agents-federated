@echo off
echo 🚀 Deploying AI Runtime Code Modification System...

echo 📦 Building container...
podman-compose build

echo 🏃 Starting services...
podman-compose up -d

echo 📋 Container status:
podman-compose ps

echo 📊 Logs (press Ctrl+C to stop following):
podman-compose logs -f llm-runtime-system