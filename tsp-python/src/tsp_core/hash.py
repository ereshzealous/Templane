from __future__ import annotations
import hashlib
import json
from .models import TypedSchema, typed_schema_to_dict


def schema_hash(schema: TypedSchema) -> str:
    """SHA-256 hex digest of the schema's canonical JSON serialization.

    Field-order invariant: uses sort_keys=True so {a,b} and {b,a} hash identically.
    """
    data = typed_schema_to_dict(schema)
    serialized = json.dumps(data, sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(serialized.encode("utf-8")).hexdigest()
