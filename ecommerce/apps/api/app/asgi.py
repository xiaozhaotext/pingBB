from .logging_config import configure_logging
from .config import settings
configure_logging(settings.log_level)
from .main import app
