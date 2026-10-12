from pydantic import BaseModel, Field


class EmbedRequest(BaseModel):
    texts: list[str] = Field(min_length=1)


class EmbedResponse(BaseModel):
    model: str
    dimensions: int
    embeddings: list[list[float]]


class HealthResponse(BaseModel):
    # "ready" once the embedding model is loaded, "loading" before, "error" if loading failed.
    status: str
    embed_model: str
    detail: str | None = None
