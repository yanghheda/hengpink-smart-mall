import os

from fastapi import FastAPI

app = FastAPI(title="HengPick Agent Service", version="0.1.0")


@app.get("/")
async def landing() -> dict[str, str]:
    return {
        "service": "agent-service",
        "status": "UP",
        "scope": "P01-S01",
    }


@app.get("/health/live")
async def live() -> dict[str, str]:
    return {"status": "UP"}


def configured_port() -> int:
    """Expose the environment-driven port for repeatable launcher tests."""
    return int(os.getenv("AGENT_PORT", "8000"))
