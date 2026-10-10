import uuid
from datetime import datetime, timezone
from sqlalchemy import create_engine, event
from sqlalchemy.orm import DeclarativeBase, sessionmaker
from .config import settings

class Base(DeclarativeBase): pass

connect_args = {"check_same_thread": False} if settings.database_url.startswith("sqlite") else {}
engine = create_engine(settings.database_url, pool_pre_ping=True, connect_args=connect_args)
if settings.database_url.startswith("sqlite"):
    @event.listens_for(engine, "connect")
    def _fk_on(dbapi_connection, _):
        dbapi_connection.execute("PRAGMA foreign_keys=ON")
SessionLocal = sessionmaker(bind=engine, autoflush=False, expire_on_commit=False)

def get_db():
    db = SessionLocal()
    try: yield db
    finally: db.close()

def new_id(): return str(uuid.uuid4())
def now_utc(): return datetime.now(timezone.utc)
