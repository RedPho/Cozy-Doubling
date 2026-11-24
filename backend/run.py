import os
import uvicorn

if __name__ == "__main__":
    backend_dir = os.path.dirname(os.path.abspath(__file__))
    uvicorn.run(
        "app.main:app",
        host="0.0.0.0",
        port=8000,
        reload=True,
        reload_dirs=[backend_dir],
        ws_ping_interval=25.0,
        ws_ping_timeout=25.0
    )

