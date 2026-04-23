package core

import (
	"testing"
)

func TestBasicFields(t *testing.T) {
	yaml := "name:\n  type: string\n  required: true\nage:\n  type: number\n  required: false\n"
	r := ParseSchema(yaml, "basic")
	if r.Error != "" {
		t.Fatalf("unexpected error: %s", r.Error)
	}
	if r.Schema.ID != "basic" {
		t.Errorf("id = %q, want basic", r.Schema.ID)
	}
	if r.Schema.Fields["name"].Type.Kind != "string" {
		t.Errorf("name type = %q, want string", r.Schema.Fields["name"].Type.Kind)
	}
	if !r.Schema.Fields["name"].Required {
		t.Errorf("name should be required")
	}
	if r.Schema.Fields["age"].Type.Kind != "number" {
		t.Errorf("age type = %q, want number", r.Schema.Fields["age"].Type.Kind)
	}
	if r.Schema.Fields["age"].Required {
		t.Errorf("age should not be required")
	}
}

func TestEnumType(t *testing.T) {
	yaml := "status:\n  type: enum\n  values: [active, inactive, pending]\n  required: true\n"
	r := ParseSchema(yaml, "enum-type")
	status := r.Schema.Fields["status"].Type
	if status.Kind != "enum" {
		t.Errorf("kind = %q, want enum", status.Kind)
	}
	want := []string{"active", "inactive", "pending"}
	if len(status.Values) != len(want) {
		t.Fatalf("values len = %d, want %d", len(status.Values), len(want))
	}
	for i, v := range want {
		if status.Values[i] != v {
			t.Errorf("values[%d] = %q, want %q", i, status.Values[i], v)
		}
	}
}

func TestListType(t *testing.T) {
	yaml := "tags:\n  type: list\n  items:\n    type: string\n  required: false\n"
	r := ParseSchema(yaml, "list-type")
	tags := r.Schema.Fields["tags"].Type
	if tags.Kind != "list" {
		t.Errorf("kind = %q, want list", tags.Kind)
	}
	if tags.ItemType == nil || tags.ItemType.Kind != "string" {
		t.Errorf("item_type = %+v, want string", tags.ItemType)
	}
}

func TestObjectType(t *testing.T) {
	yaml := "address:\n  type: object\n  required: true\n  fields:\n    city:\n      type: string\n      required: true\n"
	r := ParseSchema(yaml, "object-type")
	addr := r.Schema.Fields["address"].Type
	if addr.Kind != "object" {
		t.Errorf("kind = %q, want object", addr.Kind)
	}
	if addr.Fields["city"].Type.Kind != "string" {
		t.Errorf("city type = %q, want string", addr.Fields["city"].Type.Kind)
	}
}

func TestBodyExtracted(t *testing.T) {
	yaml := "name:\n  type: string\n  required: true\n---\nHello {{ name }}!\n"
	r := ParseSchema(yaml, "body-extracted")
	if r.Schema == nil {
		t.Fatalf("schema is nil")
	}
	if r.Body == nil {
		t.Fatalf("body is nil")
	}
	if *r.Body != "Hello {{ name }}!\n" {
		t.Errorf("body = %q", *r.Body)
	}
}

func TestInvalidSchemaReturnsError(t *testing.T) {
	r := ParseSchema("- just\n- a\n- list\n", "invalid-schema")
	if r.Error == "" {
		t.Errorf("expected error, got none")
	}
	if r.Schema != nil {
		t.Errorf("expected nil schema, got %+v", r.Schema)
	}
}

func TestDeepNesting(t *testing.T) {
	yaml := `order:
  type: object
  required: true
  fields:
    customer:
      type: object
      required: true
      fields:
        address:
          type: object
          required: true
          fields:
            city:
              type: string
              required: true
`
	r := ParseSchema(yaml, "deep-nesting")
	order := r.Schema.Fields["order"].Type
	customer := order.Fields["customer"].Type
	address := customer.Fields["address"].Type
	if address.Fields["city"].Type.Kind != "string" {
		t.Errorf("city type = %q", address.Fields["city"].Type.Kind)
	}
}
