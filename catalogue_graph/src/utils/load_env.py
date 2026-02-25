"""Bootstrap environment variables from .env files.

Must be imported before any module-level os.getenv() calls that depend
on secrets resolved by the Lambda extension.

In Lambda, the bash_secrets_extension writes resolved secrets to /tmp/.env.
Locally, load_dotenv() also picks up a .env in the working directory.

Python caches module imports, so this only runs once no matter how many
config modules import it.
"""

from dotenv import load_dotenv

load_dotenv("/tmp/.env", override=True)
load_dotenv(override=True)
