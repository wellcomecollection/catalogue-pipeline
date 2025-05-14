# Set the default pythonversion to an invalid value. This is because:
# * A default is needed, otherwise Docker warns about an invalid base image name, even when you provide a value.
# * Docker has no way to specify required build args,
#   so a caller could forget to provide the value,
#   or (worse) might have a typo in "--build-arg pythonversion=".
#   Setting the default to 2.7 will cause an obvious failure if this arg is not provided.
ARG pythonversion=A_VERSION_THAT_DOES_NOT_EXIST
FROM public.ecr.aws/docker/library/python:${pythonversion}-slim  AS builder

RUN apt-get update && apt-get -y install gcc g++ libffi-dev

COPY --from=ghcr.io/astral-sh/uv:0.6.17 /uv /uvx /bin/

ENV UV_PYTHON_DOWNLOADS=0
ENV UV_COMPILE_BYTECODE=1 UV_LINK_MODE=copy
ENV UV_PROJECT_ENVIRONMENT=/venv

WORKDIR /src/common

RUN --mount=type=cache,target=/root/.cache/uv  \
    --mount=type=bind,source=common/uv.lock,target=/src/common/uv.lock \
    --mount=type=bind,source=common/pyproject.toml,target=/src/common/pyproject.toml \
    uv sync --frozen --no-dev
COPY common /src/common

FROM builder AS aspect_ratio_inferrer_builder

WORKDIR /src/aspect_ratio_inferrer

RUN --mount=type=cache,target=/root/.cache/uv  \
    --mount=type=bind,source=aspect_ratio_inferrer/uv.lock,target=/src/aspect_ratio_inferrer/uv.lock \
    --mount=type=bind,source=aspect_ratio_inferrer/pyproject.toml,target=/src/aspect_ratio_inferrer/pyproject.toml \
    uv sync --frozen --no-dev

FROM builder AS palette_inferrer_builder

WORKDIR /src/palette_inferrer

RUN --mount=type=cache,target=/root/.cache/uv  \
    --mount=type=bind,source=palette_inferrer/uv.lock,target=/src/palette_inferrer/uv.lock \
    --mount=type=bind,source=palette_inferrer/pyproject.toml,target=/src/palette_inferrer/pyproject.toml \
    uv sync --frozen --no-dev

FROM builder AS feature_inferrer_builder

WORKDIR /src/feature_inferrer

RUN --mount=type=cache,target=/root/.cache/uv  \
    --mount=type=bind,source=feature_inferrer/uv.lock,target=/src/feature_inferrer/uv.lock \
    --mount=type=bind,source=feature_inferrer/pyproject.toml,target=/src/feature_inferrer/pyproject.toml \
    uv sync --frozen --no-dev

FROM  public.ecr.aws/docker/library/python:${pythonversion}-slim  AS base

RUN apt-get -y install curl

ENV PYTHONPATH=/app
ENV PATH="/venv/bin:$PATH"
COPY common/gunicorn.conf.py /
COPY common/start.sh /

COPY --from=builder /venv /venv

EXPOSE 80
CMD ["./start.sh"]

FROM base AS aspect_ratio_inferrer
COPY --from=aspect_ratio_inferrer_builder /venv /venv
COPY aspect_ratio_inferrer/app app

FROM base AS feature_inferrer
COPY --from=feature_inferrer_builder /venv /venv

COPY feature_inferrer/fetch_vgg.py /
RUN python ./fetch_vgg.py

COPY feature_inferrer/app app

FROM base AS palette_inferrer
COPY --from=palette_inferrer_builder /venv /venv
COPY palette_inferrer/app app
