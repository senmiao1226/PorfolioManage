# 本项目主后端为 Java（portfolio-management-java）；以下 Python FastAPI 入口已整体注释保留，不删除。
# 如需恢复，请取消整段注释。

# from contextlib import asynccontextmanager
#
# from fastapi import FastAPI
# from fastapi.middleware.cors import CORSMiddleware
#
# from app.config import settings
# from app.database import Base, engine
# from app.routers import portfolios
#
#
# @asynccontextmanager
# async def lifespan(app: FastAPI):
#     Base.metadata.create_all(bind=engine)
#     yield
#
#
# app = FastAPI(
#     title="Portfolio Management API",
#     description="实训项目：投资组合 REST API（单用户、SQL 持久化、Swagger 文档）",
#     version="1.0.0",
#     lifespan=lifespan,
# )
#
# origins = [o.strip() for o in settings.cors_origins.split(",") if o.strip()]
# app.add_middleware(
#     CORSMiddleware,
#     allow_origins=origins or ["*"],
#     allow_credentials=True,
#     allow_methods=["*"],
#     allow_headers=["*"],
# )
#
# app.include_router(portfolios.router)
#
#
# @app.get("/health")
# def health():
#     return {"status": "ok"}
