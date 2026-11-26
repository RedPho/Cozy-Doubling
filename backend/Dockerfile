# Cozy Doubling API. Build from backend/:  docker build -t cozy-api .
FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY . .

EXPOSE 8000
# Exactly one worker: room presence, safety sockets and the login throttle live in memory.
CMD ["sh", "-c", "exec uvicorn app.main:app --host 0.0.0.0 --port ${PORT:-8000} --workers 1 --ws-ping-interval 25 --ws-ping-timeout 25"]
