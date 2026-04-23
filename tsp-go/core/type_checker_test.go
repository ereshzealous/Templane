package core

import (
	"strings"
	"testing"
)

func schemaOf(pairs ...any) *TypedSchema {
	fields := map[string]TSPField{}
	for i := 0; i < len(pairs); i += 3 {
		name := pairs[i].(string)
		t := pairs[i+1].(TSPFieldType)
		req := pairs[i+2].(bool)
		fields[name] = TSPField{Name: name, Type: t, Required: req}
	}
	return &TypedSchema{ID: "test", Fields: fields}
}

func str() TSPFieldType { return TSPFieldType{Kind: "string"} }
func num() TSPFieldType { return TSPFieldType{Kind: "number"} }
func boo() TSPFieldType { return TSPFieldType{Kind: "boolean"} }

func TestValidDataNoErrors(t *testing.T) {
	s := schemaOf("name", str(), true, "age", num(), false)
	errs := Check(s, map[string]any{"name": "Alice", "age": 30})
	if len(errs) != 0 {
		t.Errorf("got %d errors, want 0", len(errs))
	}
}

func TestMissingRequiredField(t *testing.T) {
	s := schemaOf("name", str(), true, "email", str(), true)
	errs := Check(s, map[string]any{"name": "Alice"})
	if len(errs) != 1 {
		t.Fatalf("got %d errors, want 1", len(errs))
	}
	if errs[0].Code != "missing_required_field" || errs[0].Field != "email" {
		t.Errorf("unexpected error: %+v", errs[0])
	}
}

func TestTypeMismatchNumber(t *testing.T) {
	s := schemaOf("age", num(), true)
	errs := Check(s, map[string]any{"age": "thirty"})
	if len(errs) != 1 {
		t.Fatalf("got %d errors, want 1", len(errs))
	}
	if errs[0].Code != "type_mismatch" || errs[0].Field != "age" {
		t.Errorf("unexpected: %+v", errs[0])
	}
	if !strings.Contains(errs[0].Message, "number") || !strings.Contains(errs[0].Message, "string") {
		t.Errorf("bad message: %s", errs[0].Message)
	}
}

func TestInvalidEnumValue(t *testing.T) {
	s := schemaOf("status", TSPFieldType{Kind: "enum", Values: []string{"active", "inactive"}}, true)
	errs := Check(s, map[string]any{"status": "unknown"})
	if len(errs) != 1 {
		t.Fatalf("got %d, want 1: %+v", len(errs), errs)
	}
	if errs[0].Code != "invalid_enum_value" {
		t.Errorf("code = %s", errs[0].Code)
	}
}

func TestUnknownField(t *testing.T) {
	s := schemaOf("name", str(), true)
	errs := Check(s, map[string]any{"name": "Alice", "extra": "value"})
	found := false
	for _, e := range errs {
		if e.Code == "unknown_field" && e.Field == "extra" {
			found = true
		}
	}
	if !found {
		t.Errorf("did not find unknown_field for 'extra': %+v", errs)
	}
}

func TestDidYouMean(t *testing.T) {
	s := schemaOf("name", str(), true)
	errs := Check(s, map[string]any{"naem": "Alice"})
	var missing, dym bool
	for _, e := range errs {
		if e.Code == "missing_required_field" {
			missing = true
		}
		if e.Code == "did_you_mean" && strings.Contains(e.Message, "name") {
			dym = true
		}
	}
	if !missing || !dym {
		t.Errorf("missing=%v dym=%v errs=%+v", missing, dym, errs)
	}
}

func TestNestedObjectTypeError(t *testing.T) {
	inner := TSPFieldType{Kind: "object", Fields: map[string]TSPField{
		"city": {Name: "city", Type: str(), Required: true},
	}}
	s := schemaOf("address", inner, true)
	errs := Check(s, map[string]any{
		"address": map[string]any{"city": 42},
	})
	if len(errs) != 1 {
		t.Fatalf("got %d errors", len(errs))
	}
	if errs[0].Field != "address.city" || errs[0].Code != "type_mismatch" {
		t.Errorf("unexpected: %+v", errs[0])
	}
}

func TestListItemTypeMismatch(t *testing.T) {
	s := schemaOf("tags", TSPFieldType{Kind: "list", ItemType: &TSPFieldType{Kind: "string"}}, true)
	errs := Check(s, map[string]any{"tags": []any{"hello", 42, "world"}})
	if len(errs) != 1 {
		t.Fatalf("got %d errors: %+v", len(errs), errs)
	}
	if errs[0].Field != "tags[1]" || errs[0].Code != "type_mismatch" {
		t.Errorf("unexpected: %+v", errs[0])
	}
}

func TestErrorsCollectedNotShortCircuited(t *testing.T) {
	s := schemaOf("a", str(), true, "b", num(), true)
	errs := Check(s, map[string]any{"a": 1, "b": "x"})
	if len(errs) != 2 {
		t.Errorf("got %d, want 2: %+v", len(errs), errs)
	}
}
