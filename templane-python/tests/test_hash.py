from templane_core.hash import schema_hash
from templane_core.models import TypedSchema, TemplaneField, StringType, NumberType


def test_hash_is_deterministic():
    s1 = TypedSchema(id="x", fields={"name": TemplaneField(name="name", type=StringType(), required=True)})
    s2 = TypedSchema(id="x", fields={"name": TemplaneField(name="name", type=StringType(), required=True)})
    assert schema_hash(s1) == schema_hash(s2)


def test_hash_differs_for_different_schemas():
    s1 = TypedSchema(id="x", fields={"name": TemplaneField(name="name", type=StringType(), required=True)})
    s2 = TypedSchema(id="x", fields={"name": TemplaneField(name="name", type=NumberType(), required=True)})
    assert schema_hash(s1) != schema_hash(s2)


def test_hash_is_sha256_hex():
    s = TypedSchema(id="x", fields={"name": TemplaneField(name="name", type=StringType(), required=True)})
    h = schema_hash(s)
    assert isinstance(h, str)
    assert len(h) == 64
    assert all(c in "0123456789abcdef" for c in h)
