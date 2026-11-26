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
    # "ready" when an LLM API key is configured, "disabled" otherwise; independent of `status`.
    llm: str = "disabled"
    llm_model: str | None = None
    # "ready", "loading", "error" or "disabled"; independent of `status`, embedding works without it.
    rerank: str = "disabled"
    rerank_model: str | None = None
    detail: str | None = None


class RerankRequest(BaseModel):
    """One demand / supply text against the texts of the candidates it is compared with."""

    query: str = Field(min_length=1)
    documents: list[str] = Field(min_length=1)


class RerankResponse(BaseModel):
    model: str
    # Relevance in [0, 1], one per document, in the same order.
    scores: list[float]


class VocabularyTag(BaseModel):
    code: str = Field(min_length=1, max_length=60)
    name: str = Field(min_length=1, max_length=200)


class EnrichRequest(BaseModel):
    """Field names follow the contract in the design (04, section 4.4)."""

    entityType: str = Field(pattern="^(STUDENT|TUTOR|STUDYING_REQUEST|TEACHING_REQUEST)$")
    subject: str | None = Field(default=None, max_length=200)
    grades: list[str] = Field(default_factory=list, max_length=20)
    # Free text typed by users, already stripped of phone numbers and e-mails by the backend.
    fields: dict[str, str] = Field(max_length=12)
    # Allowed codes per group (TOPIC / STYLE / SEGMENT / GOAL): the model may only choose from these.
    vocabulary: dict[str, list[VocabularyTag]] = Field(default_factory=dict)
    extract: list[str] = Field(default_factory=list)


class ScheduleItem(BaseModel):
    # ISO-8601: 1 = Monday ... 7 = Sunday.
    dayOfWeek: int
    start: str
    end: str
    confidence: float


class TagItem(BaseModel):
    group: str
    code: str
    role: str
    confidence: float


class EnrichResponse(BaseModel):
    model: str
    schedule: list[ScheduleItem]
    tags: list[TagItem]


class SuggestTagsRequest(BaseModel):
    """Subject names only reach this endpoint after an administrator added the subject to the catalogue."""

    subject: str = Field(min_length=1, max_length=200)
    existing: list[VocabularyTag] = Field(default_factory=list, max_length=200)
    # Short excerpts of what users wrote about the subject, already stripped of phone numbers and e-mails.
    samples: list[str] = Field(default_factory=list, max_length=30)
    maxTags: int = Field(default=10, ge=1, le=20)


class SuggestedTag(BaseModel):
    code: str
    name: str
    description: str
    parentCode: str | None = None


class SuggestTagsResponse(BaseModel):
    model: str
    tags: list[SuggestedTag]


class ParseCatalog(BaseModel):
    """Names the model may choose from; anything else it returns is dropped."""

    subjects: list[str] = Field(default_factory=list, max_length=300)
    grades: list[str] = Field(default_factory=list, max_length=50)
    districtsInUserProvince: list[str] = Field(default_factory=list, max_length=200)


class ParseQueryRequest(BaseModel):
    """A2 (design 05, 5.4): one sentence typed by a user, already stripped of phone numbers and e-mails."""

    text: str = Field(min_length=1, max_length=500)
    direction: str = Field(default="STUDENT_TO_TUTOR", pattern="^(STUDENT_TO_TUTOR|TUTOR_TO_STUDENT)$")
    catalog: ParseCatalog = Field(default_factory=ParseCatalog)
    # Allowed codes per group (STYLE / GOAL / SEGMENT / TOPIC).
    vocabulary: dict[str, list[VocabularyTag]] = Field(default_factory=dict)


class ParsedArea(BaseModel):
    scope: str | None = None
    districts: list[str] = Field(default_factory=list)


class ParsedPrice(BaseModel):
    min: int | None = None
    max: int | None = None


class ParsedSlot(BaseModel):
    # ISO-8601: 1 = Monday ... 7 = Sunday.
    dayOfWeek: int
    start: str
    end: str


class ParseQueryResponse(BaseModel):
    model: str
    subject: str | None = None
    # The first grade, for searches that take one; `grades` has all of them.
    grade: str | None = None
    grades: list[str] = Field(default_factory=list)
    mode: str | None = None
    area: ParsedArea = Field(default_factory=ParsedArea)
    price: ParsedPrice = Field(default_factory=ParsedPrice)
    schedule: list[ParsedSlot] = Field(default_factory=list)
    styleTags: list[str] = Field(default_factory=list)
    goalTags: list[str] = Field(default_factory=list)
    segmentTags: list[str] = Field(default_factory=list)
    topicTags: list[str] = Field(default_factory=list)
    unparsed: list[str] = Field(default_factory=list)
