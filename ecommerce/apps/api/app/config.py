from pydantic_settings import BaseSettings, SettingsConfigDict

class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=(".env", "../../.env"), extra="ignore")
    app_name: str = "Ecommerce API"
    environment: str = "development"
    database_url: str = "sqlite:///./ecommerce-dev.db"
    redis_url: str = "redis://localhost:6379/0"
    redis_required: bool = False
    session_ttl_seconds: int = 86400
    login_rate_limit: int = 10
    login_rate_window_seconds: int = 60
    bootstrap_token: str | None = None
    log_level: str = "INFO"
    cors_origins: str = "http://localhost:5173,http://localhost:5174,http://localhost:5175"

settings = Settings()
