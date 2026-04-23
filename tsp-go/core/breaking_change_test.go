package core

import "testing"

func schema(pairs ...any) *TypedSchema {
	fields := map[string]TSPField{}
	for i := 0; i < len(pairs); i += 3 {
		name := pairs[i].(string)
		t := pairs[i+1].(TSPFieldType)
		req := pairs[i+2].(bool)
		fields[name] = TSPField{Name: name, Type: t, Required: req}
	}
	return &TypedSchema{ID: "x", Fields: fields}
}

func TestRemovedFieldIsBreaking(t *testing.T) {
	old := schema("name", str(), true, "email", str(), false)
	nu := schema("name", str(), true)
	changes := DetectBreakingChanges(old, nu)
	if len(changes) != 1 {
		t.Fatalf("got %d changes, want 1: %+v", len(changes), changes)
	}
	if changes[0].Category != "removed_field" || changes[0].FieldPath != "email" {
		t.Errorf("unexpected: %+v", changes[0])
	}
}

func TestAddedOptionalFieldNotBreaking(t *testing.T) {
	old := schema("name", str(), true)
	nu := schema("name", str(), true, "age", num(), false)
	if changes := DetectBreakingChanges(old, nu); len(changes) != 0 {
		t.Errorf("got %d changes, want 0", len(changes))
	}
}

func TestOptionalToRequiredIsBreaking(t *testing.T) {
	old := schema("email", str(), false)
	nu := schema("email", str(), true)
	changes := DetectBreakingChanges(old, nu)
	if len(changes) != 1 || changes[0].Category != "required_change" {
		t.Errorf("got %+v", changes)
	}
}

func TestRequiredToOptionalNotBreaking(t *testing.T) {
	old := schema("email", str(), true)
	nu := schema("email", str(), false)
	if changes := DetectBreakingChanges(old, nu); len(changes) != 0 {
		t.Errorf("got %+v", changes)
	}
}

func TestTypeChangeIsBreaking(t *testing.T) {
	old := schema("count", str(), true)
	nu := schema("count", num(), true)
	changes := DetectBreakingChanges(old, nu)
	if len(changes) != 1 || changes[0].Category != "type_change" {
		t.Errorf("got %+v", changes)
	}
}

func TestEnumValueRemovedIsBreaking(t *testing.T) {
	old := schema("status", TSPFieldType{Kind: "enum", Values: []string{"active", "inactive", "pending"}}, true)
	nu := schema("status", TSPFieldType{Kind: "enum", Values: []string{"active", "inactive"}}, true)
	changes := DetectBreakingChanges(old, nu)
	if len(changes) != 1 || changes[0].Category != "enum_value_removed" || changes[0].Old != "pending" {
		t.Errorf("got %+v", changes)
	}
}

func TestEnumValueAddedNotBreaking(t *testing.T) {
	old := schema("status", TSPFieldType{Kind: "enum", Values: []string{"active"}}, true)
	nu := schema("status", TSPFieldType{Kind: "enum", Values: []string{"active", "inactive"}}, true)
	if changes := DetectBreakingChanges(old, nu); len(changes) != 0 {
		t.Errorf("got %+v", changes)
	}
}

func TestNestedObjectRecursion(t *testing.T) {
	innerOld := TSPFieldType{Kind: "object", Fields: map[string]TSPField{
		"city": {Name: "city", Type: str(), Required: true},
		"zip":  {Name: "zip", Type: str(), Required: false},
	}}
	innerNew := TSPFieldType{Kind: "object", Fields: map[string]TSPField{
		"city": {Name: "city", Type: str(), Required: true},
	}}
	old := schema("address", innerOld, true)
	nu := schema("address", innerNew, true)
	changes := DetectBreakingChanges(old, nu)
	if len(changes) != 1 {
		t.Fatalf("got %+v", changes)
	}
	if changes[0].Category != "removed_field" || changes[0].FieldPath != "address.zip" {
		t.Errorf("got %+v", changes[0])
	}
}
