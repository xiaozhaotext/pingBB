from logging.config import dictConfig
import logging

def configure_logging(level="INFO"):
    dictConfig({"version":1,"disable_existing_loggers":False,"formatters":{"json":{"format":"%(asctime)s %(levelname)s %(name)s %(message)s"}},"handlers":{"console":{"class":"logging.StreamHandler","formatter":"json"}},"root":{"handlers":["console"],"level":level}})
    for name in ("uvicorn.access",):logging.getLogger(name).setLevel(level)
