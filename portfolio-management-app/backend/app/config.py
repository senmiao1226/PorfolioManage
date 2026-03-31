from typing import Optional
from urllib.parse import quote_plus

from pydantic import Field, computed_field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    # 若设置则完全使用该 URL（可为 sqlite 或任意 MySQL 连接串）
    database_url_override: Optional[str] = Field(default=None, validation_alias="DATABASE_URL")
    # 与 Spring application.yml 中 ${MYSQL_PASSWORD:root} 一致
    mysql_password: str = Field(default="root", validation_alias="MYSQL_PASSWORD")
    cached_price_base: str = (
        "https://c4rm9elh30.execute-api.us-east-1.amazonaws.com/default/cachedPriceData"
    )
    cors_origins: str = "http://localhost:5173,http://127.0.0.1:5173"

    @computed_field
    @property
    def database_url(self) -> str:
        if self.database_url_override:
            return self.database_url_override
        return (
            f"mysql+pymysql://root:{quote_plus(self.mysql_password)}"
            "@localhost:3306/portfolio_db?charset=utf8mb4"
        )


settings = Settings()
